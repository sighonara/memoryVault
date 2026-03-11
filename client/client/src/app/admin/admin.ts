import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AdminStore } from './admin.store';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatTableModule,
    MatTabsModule,
    MatProgressBarModule,
    MatIconModule,
    MatButtonModule,
  ],
  providers: [DatePipe, DecimalPipe],
  template: `
    <div class="admin-container">
      <div class="header">
        <h1>System Administration</h1>
        <button mat-icon-button (click)="refresh()" [disabled]="store.loading()">
          <mat-icon>refresh</mat-icon>
        </button>
      </div>

      <mat-progress-bar *ngIf="store.loading()" mode="indeterminate"></mat-progress-bar>

      <div class="stats-grid" *ngIf="store.stats() as stats">
        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar>bookmark</mat-icon>
            <mat-card-title>{{ stats.bookmarkCount }}</mat-card-title>
            <mat-card-subtitle>Bookmarks</mat-card-subtitle>
          </mat-card-header>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar>rss_feed</mat-icon>
            <mat-card-title>{{ stats.feedCount }}</mat-card-title>
            <mat-card-subtitle>Feeds ({{ stats.unreadFeedItemCount }} unread)</mat-card-subtitle>
          </mat-card-header>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar>video_library</mat-icon>
            <mat-card-title>{{ stats.youtubeListCount }}</mat-card-title>
            <mat-card-subtitle>YouTube Lists ({{ stats.videoCount }} videos)</mat-card-subtitle>
          </mat-card-header>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar>storage</mat-icon>
            <mat-card-title>{{ formatBytes(stats.storageUsedBytes) }}</mat-card-title>
            <mat-card-subtitle>Storage Used</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
      </div>

      <mat-tab-group class="admin-tabs">
        <mat-tab label="Recent Jobs">
          <table mat-table [dataSource]="store.jobs()" class="admin-table">
            <ng-container matColumnDef="type">
              <th mat-header-cell *matHeaderCellDef>Type</th>
              <td mat-cell *matCellDef="let job">{{ job.type }}</td>
            </ng-container>

            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let job">
                <span class="status-badge" [class]="job.status.toLowerCase()">
                  {{ job.status }}
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="startedAt">
              <th mat-header-cell *matHeaderCellDef>Started</th>
              <td mat-cell *matCellDef="let job">{{ job.startedAt | date:'short' }}</td>
            </ng-container>

            <ng-container matColumnDef="duration">
              <th mat-header-cell *matHeaderCellDef>Duration</th>
              <td mat-cell *matCellDef="let job">{{ getDuration(job) }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="jobColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: jobColumns;"></tr>
          </table>
        </mat-tab>

        <mat-tab label="System Logs">
          <table mat-table [dataSource]="store.logs()" class="admin-table">
            <ng-container matColumnDef="timestamp">
              <th mat-header-cell *matHeaderCellDef>Time</th>
              <td mat-cell *matCellDef="let log">{{ log.timestamp | date:'HH:mm:ss' }}</td>
            </ng-container>

            <ng-container matColumnDef="level">
              <th mat-header-cell *matHeaderCellDef>Level</th>
              <td mat-cell *matCellDef="let log">
                <span class="log-level" [class]="log.level.toLowerCase()">
                  {{ log.level }}
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="message">
              <th mat-header-cell *matHeaderCellDef>Message</th>
              <td mat-cell *matCellDef="let log" class="log-message">{{ log.message }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="logColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: logColumns;"></tr>
          </table>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .admin-container { padding: 24px; max-width: 1200px; margin: 0 auto; }
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; margin-bottom: 32px; }
    .stat-card { background: #f8f9fa; border: 1px solid #e9ecef; }
    .admin-tabs { background: white; border: 1px solid #e9ecef; border-radius: 4px; }
    .admin-table { width: 100%; }
    .status-badge { padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
    .status-badge.completed { background: #e6f4ea; color: #1e7e34; }
    .status-badge.failed { background: #fce8e6; color: #d93025; }
    .status-badge.running { background: #e8f0fe; color: #1967d2; }
    .log-level { font-weight: bold; font-size: 12px; }
    .log-level.error { color: #d93025; }
    .log-level.warn { color: #f29900; }
    .log-level.info { color: #1967d2; }
    .log-message { font-family: monospace; font-size: 13px; }
  `]
})
export class AdminComponent implements OnInit {
  readonly store = inject(AdminStore);

  jobColumns = ['type', 'status', 'startedAt', 'duration'];
  logColumns = ['timestamp', 'level', 'message'];

  ngOnInit() {
    this.refresh();
  }

  refresh() {
    this.store.loadAdminData();
  }

  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  getDuration(job: any): string {
    if (!job.completedAt) return 'Running...';
    const start = new Date(job.startedAt).getTime();
    const end = new Date(job.completedAt).getTime();
    const diff = (end - start) / 1000;
    if (diff < 60) return `${Math.round(diff)}s`;
    return `${Math.round(diff / 60)}m ${Math.round(diff % 60)}s`;
  }
}
