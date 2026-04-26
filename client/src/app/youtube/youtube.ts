import { Component, inject, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { YoutubeStore } from './youtube.store';
import { YoutubeListDialogComponent } from './youtube-list-dialog';

@Component({
  selector: 'app-youtube',
  standalone: true,
  imports: [
    DatePipe,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatTooltipModule,
    MatProgressBarModule,
    MatToolbarModule,
    MatSidenavModule,
  ],
  template: `
    <mat-sidenav-container class="youtube-container">
      <mat-sidenav mode="side" opened class="yt-sidebar">
        <mat-toolbar class="sidebar-toolbar">
          <span>Lists</span>
          <span class="spacer"></span>
          <button mat-icon-button (click)="openAddDialog()" matTooltip="Add List">
            <mat-icon>add</mat-icon>
          </button>
        </mat-toolbar>
        <mat-nav-list>
          @for (entry of store.lists(); track entry.list.id) {
            <mat-list-item
              [activated]="entry.list.id === store.selectedListId()"
              (click)="store.selectList(entry.list.id)">
              <span matListItemTitle class="truncate">{{ entry.list.name }}</span>
              <span matListItemLine class="list-meta">{{ entry.downloadedVideos }}/{{ entry.totalVideos }} videos</span>
            </mat-list-item>
          }
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content class="yt-content">
        <mat-toolbar class="content-toolbar">
          <input type="text" class="toolbar-search" placeholder="Search videos..."
                 (input)="onSearch($event)" [value]="store.searchQuery()" />
          <span class="spacer"></span>
          <button mat-button (click)="store.setRemovedOnly(!store.removedOnly())" [class.active-filter]="store.removedOnly()">
            <mat-icon>{{ store.removedOnly() ? 'visibility_off' : 'visibility' }}</mat-icon>
            Removed
          </button>
          <button mat-icon-button (click)="store.refreshList(store.selectedListId())" matTooltip="Refresh">
            <mat-icon>refresh</mat-icon>
          </button>
        </mat-toolbar>

        @if (store.loadingVideos()) {
          <mat-progress-bar mode="indeterminate"></mat-progress-bar>
        }

        <details class="backup-legend">
          <summary>Backup status legend</summary>
          <div class="legend-items">
            <span><mat-icon class="shield-icon shield-green">shield</mat-icon> Backed up (IA)</span>
            <span><mat-icon class="shield-icon shield-green">verified_user</mat-icon> Backed up (IA + secondary)</span>
            <span><mat-icon class="shield-icon shield-blue">shield</mat-icon> Backed up (secondary only)</span>
            <span><mat-icon class="shield-icon shield-yellow">shield</mat-icon> Backup lost</span>
            <span><mat-icon class="shield-icon shield-red">shield</mat-icon> Backup failed</span>
            <span><mat-icon class="shield-icon shield-gray">shield</mat-icon> Backup pending</span>
            <span><mat-icon class="shield-icon shield-none">gpp_maybe</mat-icon> Not backed up</span>
          </div>
        </details>

        <div class="video-list">
          @for (video of store.videos(); track video.id) {
            <div class="video-row" (click)="openVideo(video.youtubeUrl)">
              <img
                class="thumb"
                [src]="video.thumbnailPath || ''"
                [alt]="video.title || 'Untitled'"
                (error)="$any($event.target).style.display='none'"
              />
              <div class="video-info">
                <span class="video-title" [class.removed]="video.removedFromYoutube">
                  {{ video.title || '(untitled)' }}
                  @if (video.backupStatus === 'BACKED_UP') {
                    <mat-icon class="shield-icon shield-green" matTooltip="Backed up (IA)">shield</mat-icon>
                  } @else if (video.backupStatus === 'BACKED_UP_BOTH') {
                    <mat-icon class="shield-icon shield-green" matTooltip="Backed up (IA + secondary)">verified_user</mat-icon>
                  } @else if (video.backupStatus === 'BACKED_UP_SECONDARY') {
                    <mat-icon class="shield-icon shield-blue" matTooltip="Backed up (secondary only)">shield</mat-icon>
                  } @else if (video.backupStatus === 'LOST') {
                    <mat-icon class="shield-icon shield-yellow" matTooltip="Backup lost, queued for secondary">shield</mat-icon>
                  } @else if (video.backupStatus === 'FAILED') {
                    <mat-icon class="shield-icon shield-red" matTooltip="Backup failed">shield</mat-icon>
                  } @else if (video.backupStatus === 'PENDING') {
                    <mat-icon class="shield-icon shield-gray" matTooltip="Backup pending">shield</mat-icon>
                  } @else {
                    <mat-icon class="shield-icon shield-none" matTooltip="Not backed up — add a provider in Admin → Backups, then Backfill All">gpp_maybe</mat-icon>
                  }
                </span>
                <span class="video-meta">
                  {{ video.downloadedAt | date:'mediumDate' }}
                  @if (video.removedFromYoutube) {
                    <span class="removed-badge">REMOVED</span>
                  }
                  @if (video.downloadError) {
                    <span class="error-badge" [matTooltip]="video.downloadError">
                      <mat-icon class="error-icon">error_outline</mat-icon> FAILED
                    </span>
                  }
                </span>
              </div>
              <mat-icon class="play-icon">play_circle_outline</mat-icon>
            </div>
          }
        </div>

        @if (!store.loadingVideos() && store.videos().length === 0) {
          <div class="empty-state">
            <mat-icon>movie_filter</mat-icon>
            <p>No videos found</p>
          </div>
        }
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .youtube-container { height: 100%; background: #fff; }

    /* ── Sidebar ── */
    .yt-sidebar {
      width: 220px; background: #f8f9fa;
      border-right: 1px solid #dadce0; overflow-y: auto;
    }
    .sidebar-toolbar {
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.08em;
      text-transform: uppercase; height: 32px; min-height: 32px !important;
      color: #5f6368; background: transparent;
      border-bottom: 1px solid #dadce0; padding: 0 12px;
    }
    .yt-sidebar .mat-mdc-list-item {
      height: auto !important; min-height: 40px; font-size: 0.8125rem;
      border-radius: 0 16px 16px 0; margin-right: 8px;
      padding-top: 4px !important; padding-bottom: 4px !important;
    }
    .truncate { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .list-meta { font-size: 0.7rem; color: #80868b; }
    .spacer { flex: 1 1 auto; }

    /* ── Content area ── */
    .yt-content { display: flex; flex-direction: column; background: #fff; }
    .content-toolbar {
      background: #fff; border-bottom: 1px solid #dadce0;
      min-height: 40px; height: 40px; padding: 0 16px;
      position: sticky; top: 0; z-index: 10;
    }
    .toolbar-search {
      flex: 1; max-width: 300px; height: 30px;
      border: 1px solid #dadce0; border-radius: 4px; padding: 0 10px;
      font-size: 0.8125rem; font-family: inherit; color: #202124;
      background: #fff; outline: none;
    }
    .toolbar-search:focus { border-color: #1a73e8; }
    .active-filter { background: #fce4ec !important; color: #c62828 !important; }

    /* ── Video list (dense rows) ── */
    .video-list { max-width: 900px; }
    .video-row {
      display: flex; align-items: center; gap: 12px;
      padding: 6px 16px; border-bottom: 1px solid #f1f3f4;
      cursor: pointer; transition: background 0.1s;
    }
    .video-row:hover { background: #f8f9fa; }
    .thumb {
      width: 80px; height: 45px; object-fit: cover;
      border-radius: 3px; flex-shrink: 0; background: #e8eaed;
    }
    .video-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
    .video-title {
      font-size: 0.8125rem; font-weight: 500; color: #202124;
      display: inline-flex; align-items: center; gap: 4px;
      max-width: 100%; overflow: hidden; white-space: nowrap;
    }
    .video-title.removed { color: #c62828; text-decoration: line-through; }
    .video-meta { font-size: 0.7rem; color: #80868b; }
    .removed-badge {
      background: #ffebee; color: #c62828; padding: 1px 4px;
      border-radius: 2px; font-weight: 600; font-size: 0.6rem; margin-left: 6px;
    }
    .error-badge {
      display: inline-flex; align-items: center; gap: 2px;
      background: #fff3e0; color: #e65100; padding: 1px 4px;
      border-radius: 2px; font-weight: 600; font-size: 0.6rem; margin-left: 6px;
      cursor: help;
    }
    .error-icon { font-size: 12px; width: 12px; height: 12px; }
    .play-icon { color: #80868b; font-size: 20px; width: 20px; height: 20px; flex-shrink: 0; }
    .video-row:hover .play-icon { color: #1a73e8; }

    /* ── Empty state ── */
    .empty-state {
      display: flex; flex-direction: column; align-items: center;
      padding: 60px; color: #9aa0a6;
    }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 12px; opacity: 0.5; }
    .empty-state p { font-size: 0.8125rem; margin: 0; }

    /* ── Shield icons ── */
    .shield-icon { font-size: 14px; width: 14px; height: 14px; margin-left: 4px; vertical-align: middle; }
    .shield-green { color: #2e7d32; }
    .shield-blue { color: #1565c0; }
    .shield-yellow { color: #f9a825; }
    .shield-red { color: #c62828; }
    .shield-gray { color: #9e9e9e; }
    .shield-none { color: #dadce0; }

    /* ── Backup legend ── */
    .backup-legend {
      padding: 4px 16px; font-size: 0.7rem; color: #5f6368;
      border-bottom: 1px solid #f1f3f4;
    }
    .backup-legend summary { cursor: pointer; user-select: none; }
    .legend-items {
      display: flex; flex-wrap: wrap; gap: 12px; padding: 6px 0;
    }
    .legend-items span { display: inline-flex; align-items: center; gap: 2px; }
  `]
})
export class YoutubeComponent implements OnInit {
  readonly store = inject(YoutubeStore);
  private dialog = inject(MatDialog);
  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  ngOnInit() {
    this.store.loadLists();
  }

  onSearch(event: any) {
    const query = (event.target as HTMLInputElement).value;
    if (this.searchTimeout) clearTimeout(this.searchTimeout);
    this.searchTimeout = setTimeout(() => {
      this.store.setSearchQuery(query);
    }, 300);
  }

  openAddDialog() {
    const dialogRef = this.dialog.open(YoutubeListDialogComponent, { width: '420px' });
    dialogRef.afterClosed().subscribe(result => {
      if (result?.url) {
        this.store.addList(result.url).subscribe();
      }
    });
  }

  openVideo(url: string) {
    window.open(url, '_blank');
  }
}
