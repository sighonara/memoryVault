import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { SystemStats } from '../../shared/graphql/generated';

@Component({
  selector: 'app-stats-panel',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule],
  template: `
    @if (stats()) {
      <div class="stats-grid">
        <mat-card class="stat-card">
          <mat-card-content>
            <mat-icon>bookmark</mat-icon>
            <div class="stat-value">{{ stats()!.bookmarkCount }}</div>
            <div class="stat-label">Bookmarks</div>
          </mat-card-content>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-content>
            <mat-icon>rss_feed</mat-icon>
            <div class="stat-value">{{ stats()!.feedCount }}</div>
            <div class="stat-label">Feeds</div>
            @if (stats()!.feedsWithFailures > 0) {
              <div class="failure-badge">{{ stats()!.feedsWithFailures }} failing</div>
            }
          </mat-card-content>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-content>
            <mat-icon>article</mat-icon>
            <div class="stat-value">{{ stats()!.feedItemCount }}</div>
            <div class="stat-label">Feed Items</div>
            <div class="stat-sub">{{ stats()!.unreadFeedItemCount }} unread</div>
          </mat-card-content>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-content>
            <mat-icon>smart_display</mat-icon>
            <div class="stat-value">{{ stats()!.youtubeListCount }}</div>
            <div class="stat-label">YouTube Lists</div>
            @if (stats()!.youtubeListsWithFailures > 0) {
              <div class="failure-badge">{{ stats()!.youtubeListsWithFailures }} failing</div>
            }
          </mat-card-content>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-content>
            <mat-icon>videocam</mat-icon>
            <div class="stat-value">{{ stats()!.videoCount }}</div>
            <div class="stat-label">Videos</div>
            <div class="stat-sub">{{ stats()!.downloadedVideoCount }} downloaded</div>
            @if (stats()!.removedVideoCount > 0) {
              <div class="stat-sub warn">{{ stats()!.removedVideoCount }} removed from YT</div>
            }
          </mat-card-content>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-content>
            <mat-icon>label</mat-icon>
            <div class="stat-value">{{ stats()!.tagCount }}</div>
            <div class="stat-label">Tags</div>
          </mat-card-content>
        </mat-card>

        <mat-card class="stat-card wide">
          <mat-card-content>
            <mat-icon>storage</mat-icon>
            <div class="stat-value">{{ formatBytes(stats()!.storageUsedBytes) }}</div>
            <div class="stat-label">Storage Used</div>
          </mat-card-content>
        </mat-card>

        <mat-card class="stat-card wide">
          <mat-card-content>
            <mat-icon>sync</mat-icon>
            <div class="stat-label">Last Feed Sync</div>
            <div class="stat-sub">{{ stats()!.lastFeedSync ? formatDate(stats()!.lastFeedSync) : 'Never' }}</div>
            <div class="stat-label" style="margin-top: 8px">Last YouTube Sync</div>
            <div class="stat-sub">{{ stats()!.lastYoutubeSync ? formatDate(stats()!.lastYoutubeSync) : 'Never' }}</div>
          </mat-card-content>
        </mat-card>
      </div>
    } @else {
      <div class="empty-state">No stats available.</div>
    }
  `,
  styles: [`
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 16px;
      padding: 16px 0;
    }
    .stat-card { text-align: center; }
    .stat-card.wide { grid-column: span 2; text-align: left; }
    mat-card-content { display: flex; flex-direction: column; align-items: center; padding: 16px !important; }
    .stat-card.wide mat-card-content { align-items: flex-start; }
    mat-icon { font-size: 36px; width: 36px; height: 36px; color: #5c6bc0; margin-bottom: 8px; }
    .stat-value { font-size: 2rem; font-weight: 600; line-height: 1; }
    .stat-label { font-size: 0.85rem; color: #666; margin-top: 4px; }
    .stat-sub { font-size: 0.8rem; color: #999; margin-top: 2px; }
    .stat-sub.warn { color: #e65100; }
    .failure-badge { background: #ffccbc; color: #bf360c; padding: 2px 8px; border-radius: 12px; font-size: 0.75rem; margin-top: 4px; }
    .empty-state { padding: 40px; text-align: center; color: #999; }
  `],
})
export class StatsPanelComponent {
  stats = input<SystemStats | null>(null);

  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
  }

  formatDate(ts: any): string {
    if (!ts) return 'Never';
    const d = new Date(ts);
    return d.toLocaleString();
  }
}
