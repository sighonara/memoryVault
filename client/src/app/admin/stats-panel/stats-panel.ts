import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { SystemStats, CostSummary } from '../../shared/graphql/generated';
import { CostCardComponent } from '../cost-card';

@Component({
  selector: 'app-stats-panel',
  standalone: true,
  imports: [CommonModule, MatIconModule, CostCardComponent],
  template: `
    @if (stats()) {
      <div class="stats-grid">
        <div class="stat-item">
          <mat-icon>bookmark</mat-icon>
          <div class="stat-body">
            <span class="stat-value">{{ stats()!.bookmarkCount }}</span>
            <span class="stat-label">Bookmarks</span>
          </div>
        </div>

        <div class="stat-item">
          <mat-icon>rss_feed</mat-icon>
          <div class="stat-body">
            <span class="stat-value">{{ stats()!.feedCount }}</span>
            <span class="stat-label">Feeds</span>
            @if (stats()!.feedsWithFailures > 0) {
              <span class="stat-warn">{{ stats()!.feedsWithFailures }} failing</span>
            }
          </div>
        </div>

        <div class="stat-item">
          <mat-icon>article</mat-icon>
          <div class="stat-body">
            <span class="stat-value">{{ stats()!.feedItemCount }}</span>
            <span class="stat-label">Feed Items ({{ stats()!.unreadFeedItemCount }} unread)</span>
          </div>
        </div>

        <div class="stat-item">
          <mat-icon>smart_display</mat-icon>
          <div class="stat-body">
            <span class="stat-value">{{ stats()!.youtubeListCount }}</span>
            <span class="stat-label">YouTube Lists</span>
            @if (stats()!.youtubeListsWithFailures > 0) {
              <span class="stat-warn">{{ stats()!.youtubeListsWithFailures }} failing</span>
            }
          </div>
        </div>

        <div class="stat-item">
          <mat-icon>videocam</mat-icon>
          <div class="stat-body">
            <span class="stat-value">{{ stats()!.videoCount }}</span>
            <span class="stat-label">Videos ({{ stats()!.downloadedVideoCount }} downloaded)</span>
            @if (stats()!.removedVideoCount > 0) {
              <span class="stat-warn">{{ stats()!.removedVideoCount }} removed from YT</span>
            }
          </div>
        </div>

        <div class="stat-item">
          <mat-icon>label</mat-icon>
          <div class="stat-body">
            <span class="stat-value">{{ stats()!.tagCount }}</span>
            <span class="stat-label">Tags</span>
          </div>
        </div>

        <div class="stat-item">
          <mat-icon>storage</mat-icon>
          <div class="stat-body">
            <span class="stat-value">{{ formatBytes(stats()!.storageUsedBytes) }}</span>
            <span class="stat-label">Storage Used</span>
          </div>
        </div>

        <div class="stat-item">
          <mat-icon>sync</mat-icon>
          <div class="stat-body">
            <span class="stat-label">Last Feed Sync: {{ stats()!.lastFeedSync ? formatDate(stats()!.lastFeedSync) : 'Never' }}</span>
            <span class="stat-label">Last YT Sync: {{ stats()!.lastYoutubeSync ? formatDate(stats()!.lastYoutubeSync) : 'Never' }}</span>
          </div>
        </div>

        <app-cost-card
          [costSummary]="costSummary()"
          [months]="costMonths()"
          [refreshing]="refreshingCosts()"
          (onRefresh)="onRefreshCosts.emit()"
          (onMonthsChange)="onCostMonthsChange.emit($event)"
        />
      </div>
    } @else {
      <div class="empty">No stats available.</div>
    }
  `,
  styles: [`
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 0;
      border-top: 1px solid #e8eaed;
    }
    .stat-item {
      display: flex; align-items: flex-start; gap: 10px;
      padding: 12px 16px;
      border-bottom: 1px solid #e8eaed;
      border-right: 1px solid #e8eaed;
    }
    mat-icon { color: #5f6368; font-size: 20px; width: 20px; height: 20px; margin-top: 2px; overflow: visible; flex-shrink: 0; }
    .stat-body { display: flex; flex-direction: column; }
    .stat-value { font-size: 1.25rem; font-weight: 600; color: #202124; line-height: 1; }
    .stat-label { font-size: 0.75rem; color: #5f6368; margin-top: 2px; }
    .stat-warn { font-size: 0.7rem; color: #c62828; margin-top: 2px; }
    .empty { padding: 40px; text-align: center; color: #9aa0a6; font-size: 0.8125rem; }
  `],
})
export class StatsPanelComponent {
  stats = input<SystemStats | null>(null);
  costSummary = input<CostSummary | null>(null);
  costMonths = input<number>(6);
  refreshingCosts = input<boolean>(false);
  onRefreshCosts = output<void>();
  onCostMonthsChange = output<number>();

  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
  }

  formatDate(ts: any): string {
    if (!ts) return 'Never';
    return new Date(ts).toLocaleString();
  }
}
