# Manual Testing Guide

Quick reference for human testers working with MemoryVault locally.

## Prerequisites

- Docker running (`docker compose up -d` for PostgreSQL)
- Backend running (`./gradlew bootRun`)
- Frontend running (`cd client && ng serve` — serves at `http://localhost:4200`)

**Seed user credentials:**
- Email: `system@memoryvault.local`
- Password: `memoryvault`

---

## Getting a JWT Token

Many commands below require a JWT token. Get one via:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"system@memoryvault.local","password":"memoryvault"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")

echo $TOKEN
```

Or log in via the UI at `http://localhost:4200/login` and grab the token from browser DevTools: **Application > Local Storage > `auth_token`**.

---

## Adding Dummy Data

### Bookmarks (via GraphQL)

```bash
# Single bookmark
curl -s -X POST http://localhost:8080/graphql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation { addBookmark(url: \"https://example.com\", title: \"Example Site\", tags: [\"test\"]) { id url title } }"}'

# Multiple bookmarks (run in a loop)
for i in $(seq 1 10); do
  curl -s -X POST http://localhost:8080/graphql \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"query\":\"mutation { addBookmark(url: \\\"https://example-${i}.com\\\", title: \\\"Test Bookmark ${i}\\\", tags: [\\\"batch\\\", \\\"test\\\"]) { id } }\"}"
done
```

### Folders (via GraphQL)

```bash
# Create a root folder
curl -s -X POST http://localhost:8080/graphql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation { createFolder(name: \"Tech\") { id name } }"}'

# Create a nested folder (use the parent id from above)
curl -s -X POST http://localhost:8080/graphql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation { createFolder(name: \"Frontend\", parentId: \"<PARENT_ID>\") { id name parentId } }"}'
```

### Feeds (via GraphQL)

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation { addFeed(url: \"https://hnrss.org/newest\") { id title url } }"}'
```

### Ingest Preview (via REST)

```bash
curl -s -X POST http://localhost:8080/api/bookmarks/ingest \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bookmarks": [
      {"url": "https://new-site.com", "title": "New Site", "browserFolder": "Tech"},
      {"url": "https://example.com", "title": "Updated Title", "browserFolder": "General"}
    ]
  }'
```

This returns a `previewId`. Open the conflict review dialog by navigating to:
`http://localhost:4200/bookmarks?ingest=<previewId>`

---

## Removing Data

### Clear All Bookmarks (keep folders)

```bash
docker compose exec postgres psql -U memoryvault -c "
  DELETE FROM bookmark_tags;
  DELETE FROM bookmarks;
"
```

### Clear All Folders

```bash
docker compose exec postgres psql -U memoryvault -c "
  UPDATE bookmarks SET folder_id = NULL;
  DELETE FROM folders;
"
```

### Clear All Bookmarks + Folders

```bash
docker compose exec postgres psql -U memoryvault -c "
  DELETE FROM bookmark_tags;
  DELETE FROM bookmarks;
  DELETE FROM ingest_previews;
  DELETE FROM folders;
"
```

### Clear All Feeds

```bash
docker compose exec postgres psql -U memoryvault -c "
  DELETE FROM feed_item_tags;
  DELETE FROM feed_items;
  DELETE FROM feeds;
"
```

### Clear All YouTube Data

```bash
docker compose exec postgres psql -U memoryvault -c "
  DELETE FROM video_tags;
  DELETE FROM videos;
  DELETE FROM youtube_lists;
"
```

### Clear Tags (orphaned tags only)

```bash
docker compose exec postgres psql -U memoryvault -c "
  DELETE FROM tags WHERE id NOT IN (
    SELECT tag_id FROM bookmark_tags
    UNION SELECT tag_id FROM feed_item_tags
    UNION SELECT tag_id FROM video_tags
  );
"
```

---

## Full Database Reset

Drops all user-created data. Keeps the schema, Flyway history, and seed user.

```bash
docker compose exec postgres psql -U memoryvault -c "
  DELETE FROM bookmark_tags;
  DELETE FROM feed_item_tags;
  DELETE FROM video_tags;
  DELETE FROM bookmarks;
  DELETE FROM ingest_previews;
  DELETE FROM folders;
  DELETE FROM feed_items;
  DELETE FROM feeds;
  DELETE FROM videos;
  DELETE FROM youtube_lists;
  DELETE FROM sync_jobs;
  DELETE FROM tags;
"
```

### Nuclear Option — Recreate the Entire Database

This destroys everything including the schema and re-runs all Flyway migrations:

```bash
docker compose down -v
docker compose up -d
# Wait a few seconds for PostgreSQL to start, then:
./gradlew bootRun
# Flyway will recreate all tables and the seed user on startup
```

---

## Checking Data

### Quick counts

```bash
docker compose exec postgres psql -U memoryvault -c "
  SELECT 'bookmarks' as table_name, count(*) FROM bookmarks WHERE deleted_at IS NULL
  UNION ALL SELECT 'folders', count(*) FROM folders WHERE deleted_at IS NULL
  UNION ALL SELECT 'tags', count(*) FROM tags
  UNION ALL SELECT 'feeds', count(*) FROM feeds
  UNION ALL SELECT 'feed_items', count(*) FROM feed_items
  UNION ALL SELECT 'youtube_lists', count(*) FROM youtube_lists
  UNION ALL SELECT 'videos', count(*) FROM videos
  ORDER BY table_name;
"
```

### View bookmarks with tags

```bash
docker compose exec postgres psql -U memoryvault -c "
  SELECT b.id, b.title, b.url, b.folder_id,
         string_agg(t.name, ', ') as tags
  FROM bookmarks b
  LEFT JOIN bookmark_tags bt ON b.id = bt.bookmark_id
  LEFT JOIN tags t ON bt.tag_id = t.id
  WHERE b.deleted_at IS NULL
  GROUP BY b.id
  ORDER BY b.created_at DESC
  LIMIT 20;
"
```

### View folder tree

```bash
docker compose exec postgres psql -U memoryvault -c "
  SELECT f.id, f.name, f.parent_id, f.sort_order,
         (SELECT count(*) FROM bookmarks b WHERE b.folder_id = f.id AND b.deleted_at IS NULL) as bookmark_count
  FROM folders f
  WHERE f.deleted_at IS NULL
  ORDER BY f.parent_id NULLS FIRST, f.sort_order;
"
```

---

## Frontend Testing Notes

- **Clear browser state:** Open DevTools > Application > Local Storage > Clear `auth_token` to force re-login
- **GraphQL playground:** If you have a GraphQL client (e.g., Altair, GraphQL Playground), point it at `http://localhost:8080/graphql` with the `Authorization: Bearer <token>` header
- **Ingest flow:** Use the Import panel in the bookmarks page to copy a CLI command, or seed a preview via the REST endpoint above and navigate to `?ingest=<previewId>`
