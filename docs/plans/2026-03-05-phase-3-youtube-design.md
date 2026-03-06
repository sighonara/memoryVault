# Phase 3: YouTube Archival — Design

**Date**: 2026-03-05
**Status**: Approved

## Goal

Track YouTube playlists and automatically archive every video in them. When videos disappear from YouTube, MemoryVault still has them. Archival-first: every video in a tracked playlist is downloaded, no opt-in per video.

## Architecture

Spring Boot manages playlist subscriptions, video metadata, and download orchestration. yt-dlp handles both metadata fetching and video downloading. Two abstraction interfaces (`VideoDownloader`, `StorageService`) provide concrete local implementations for development and stubs for future AWS Lambda + S3 deployment.

## Entities

DB tables already exist from V2 migration. This phase adds JPA entity mappings.

### YoutubeList

Maps to `youtube_lists` table. Tracks a YouTube playlist for archival.

- `id` (UUID), `userId`, `youtubeListId` (external playlist ID), `url`, `name`, `description`
- `lastSyncedAt`, `failureCount`
- Soft delete (`deletedAt`), optimistic locking (`version`)

### Video

Maps to `videos` table. A single video within a tracked playlist.

- `id` (UUID), `youtubeListId` (FK), `youtubeVideoId` (external video ID)
- `title`, `description`, `channelName`, `thumbnailPath`
- `youtubeUrl` (canonical link), `filePath` (local path or S3 key, null until downloaded), `downloadedAt`, `durationSeconds`
- `removedFromYoutube` (boolean), `removedDetectedAt`
- Tags via `video_tags` join table (same `Tag` entity shared with Bookmarks and FeedItems)

## Service Layer

### YtDlpService

Wraps yt-dlp CLI via `ProcessBuilder`. Two operations:

- `fetchPlaylistMetadata(url): List<VideoMetadata>` — runs `yt-dlp --flat-playlist --dump-json <url>`. Fast, no downloading. Returns structured metadata for each video in the playlist (title, video ID, channel, duration, URL).
- `downloadVideo(videoUrl, outputPath): DownloadResult` — runs `yt-dlp -o <outputPath> <url>`. Downloads a single video. Returns success/failure with file path.

Per-video downloading (not whole-playlist) gives us:
- Granular progress tracking and status per video
- Resilient resumption after failures (don't restart 200 videos for one failure)
- Per-video DB updates as each download completes

### YoutubeListService

CRUD for tracked playlists. Uses system user ID (same as Feeds/Bookmarks).

- `addList(url): YoutubeList` — saves the list, calls `VideoSyncService.syncList()` to immediately fetch metadata and queue downloads.
- `listLists(): List<Pair<YoutubeList, ListStats>>` — all active lists with video count, downloaded count, removed count.
- `deleteList(listId): YoutubeList?` — soft delete.
- `refreshList(listId?): List<SyncResult>` — delegates to `VideoSyncService` for one or all lists.

### VideoService

Query and filter videos.

- `getVideos(listId?, query?, removedOnly?): List<Video>` — browse with optional filters.
- `getVideoStatus(videoId): Video?` — single video detail.

### VideoSyncService

Orchestrates the full sync + download cycle:

1. `YtDlpService.fetchPlaylistMetadata(url)` — get current playlist contents
2. Compare incoming video IDs against DB (by `youtubeVideoId`)
3. New videos: save `Video` records, then download each via `VideoDownloader`
4. Missing videos (in DB but not in playlist): set `removedFromYoutube = true`, `removedDetectedAt = now()`
5. Update `YoutubeList.lastSyncedAt`

Removal assumption: videos disappear from playlists because they've been taken down by the creator or by YouTube. We don't distinguish "removed from playlist" from "removed from YouTube."

## Abstraction Interfaces

Same pattern as `JobScheduler` / `SpringJobScheduler` from Phase 2.

### VideoDownloader

```kotlin
interface VideoDownloader {
    fun download(youtubeUrl: String, videoId: UUID): DownloadResult
}
```

**`LocalVideoDownloader`** (concrete): calls `YtDlpService.downloadVideo()`, then `StorageService.store()` to persist the file. Updates `Video.filePath` and `Video.downloadedAt`.

**`LambdaVideoDownloader`** (stub): documents intent to invoke an AWS Lambda function with the video URL and S3 destination. Logs a message and returns a "not implemented" result. Comments describe:
- Lambda function name and payload shape
- How the Lambda would invoke yt-dlp and upload to S3
- How the Lambda would callback/update the DB on completion (SQS or direct DB write)

### StorageService

```kotlin
interface StorageService {
    fun store(key: String, inputStream: InputStream): String
    fun retrieve(key: String): InputStream
    fun delete(key: String)
    fun exists(key: String): Boolean
}
```

**`LocalStorageService`** (concrete): reads/writes to a configurable local directory. Config: `memoryvault.storage.local-path` (default `~/.memoryvault/storage/`). Key becomes a relative file path under that directory.

**`S3StorageService`** (stub): documents intent to use AWS SDK S3 client. Comments describe:
- Bucket name config (`memoryvault.storage.s3-bucket`)
- Multipart upload for large video files
- Pre-signed URLs for direct browser access from the web UI
- Lifecycle policies for storage class transitions (e.g., Infrequent Access after 90 days)

Spring profiles select implementations: `local` (default) uses `LocalVideoDownloader` + `LocalStorageService`. Future `aws` profile uses Lambda + S3.

## MCP Tools (6)

1. **`addYoutubeList(url)`** — Subscribe to a playlist. Immediately fetches metadata and queues all videos for download. Returns list name and video count.
2. **`listYoutubeLists()`** — All tracked playlists with video counts, download progress, and removed count.
3. **`listArchivedVideos(listId?, query?, removedOnly?)`** — Browse/search videos. Filter by playlist, text search, or show only removed videos.
4. **`getVideoStatus(videoId)`** — Detailed status for a single video: title, channel, download status, file path, removed status.
5. **`refreshYoutubeList(listId?)`** — Re-sync metadata for one or all playlists. Detects removals, queues new videos for download.
6. **`deleteYoutubeList(listId)`** — Soft delete a tracked playlist.

## Scheduler Integration

Reuses `JobScheduler` from Phase 2.

- `YoutubeSyncRegistrar` (same pattern as `FeedSyncRegistrar`): registers a `youtube-sync` job on `ApplicationReadyEvent`
- Cron configurable via `memoryvault.youtube.sync-cron` (default `"-"` = disabled)
- Sync job calls `refreshList(null)` to sync all active playlists

## Configuration

```properties
# YouTube sync schedule (cron syntax). Set to "-" to disable.
memoryvault.youtube.sync-cron=-

# Local storage path for downloaded videos
memoryvault.storage.local-path=${user.home}/.memoryvault/storage

# Active storage/downloader profile (local or aws)
# spring.profiles.active=local
```

## Data Flow

```
addYoutubeList(url)
  -> YtDlpService.fetchPlaylistMetadata(url)     [yt-dlp --flat-playlist --dump-json]
  -> Save YoutubeList + Video records
  -> For each video: VideoDownloader.download()
    -> YtDlpService.downloadVideo()               [yt-dlp -o <path>]
    -> StorageService.store()
    -> Update video.filePath, video.downloadedAt

refreshYoutubeList(listId)
  -> YtDlpService.fetchPlaylistMetadata(url)
  -> Diff: new videos -> save + download
  -> Diff: missing videos -> mark removedFromYoutube = true
  -> Update lastSyncedAt
```

## Testing

- **Unit tests**: MockK for all services. YtDlpService mocked in VideoSyncService/downloader tests.
- **Integration tests**: TestContainers with real PostgreSQL. Mocked yt-dlp output (no real network calls).
- **Test fixture**: Sample yt-dlp `--flat-playlist --dump-json` output as a JSON file in `src/test/resources/fixtures/`.
- **Test script**: `scripts/test-youtube.sh` runs unit + integration tests.

## Design Updates from Original Spec

Changes from the original tooling-first design doc:

- Added `listYoutubeLists()` tool (was missing, needed for browsing playlists)
- Added `refreshYoutubeList(listId?)` tool (was missing, needed for manual re-sync)
- Added `deleteYoutubeList(listId)` tool (was missing, needed for removing playlists)
- Downloads are per-video (not whole-playlist) for granular tracking and failure resilience
- `VideoDownloader` and `StorageService` interfaces abstract local vs AWS implementations
- All videos in a tracked playlist are automatically downloaded (archival mindset, no per-video opt-in)
- Removal detection via sync comparison only (no per-video availability check)
