import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatListModule } from '@angular/material/list';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { YoutubeStore } from './youtube.store';
import { YoutubeListDialogComponent } from './youtube-list-dialog';

@Component({
  selector: 'app-youtube',
  standalone: true,
  imports: [
    CommonModule,
    MatListModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatChipsModule,
    MatDialogModule,
    MatTooltipModule,
    MatProgressBarModule,
  ],
  template: `
    <div class="youtube-container">
      <div class="sidebar">
        <div class="sidebar-header">
          <h2>YouTube Lists</h2>
          <button mat-icon-button color="primary" (click)="openAddDialog()" matTooltip="Add List">
            <mat-icon>add</mat-icon>
          </button>
        </div>
        <mat-nav-list>
          <mat-list-item
            *ngFor="let entry of store.lists()"
            [activated]="entry.list.id === store.selectedListId()"
            (click)="store.selectList(entry.list.id)">
            <mat-icon matListItemIcon>playlist_play</mat-icon>
            <div matListItemTitle>{{ entry.list.name }}</div>
            <div matListItemLine>{{ entry.downloadedVideos }} / {{ entry.totalVideos }} videos</div>
            <button mat-icon-button matListItemMeta (click)="$event.stopPropagation(); store.deleteList(entry.list.id)" matTooltip="Delete List">
              <mat-icon>delete</mat-icon>
            </button>
          </mat-list-item>
        </mat-nav-list>
      </div>

      <div class="content">
        <div class="content-header">
          <mat-form-field appearance="outline" class="search-field">
            <mat-label>Search videos</mat-label>
            <input matInput #searchInput (keyup)="store.setSearchQuery(searchInput.value)" placeholder="Title or URL">
            <mat-icon matSuffix>search</mat-icon>
          </mat-form-field>

          <div class="actions">
             <button mat-stroked-button (click)="store.setRemovedOnly(!store.removedOnly())" [color]="store.removedOnly() ? 'warn' : ''">
               <mat-icon>{{ store.removedOnly() ? 'visibility_off' : 'visibility' }}</mat-icon>
               Removed Only
             </button>
             <button mat-icon-button (click)="store.refreshList(store.selectedListId())" matTooltip="Refresh List">
               <mat-icon>refresh</mat-icon>
             </button>
          </div>
        </div>

        <mat-progress-bar *ngIf="store.loadingVideos()" mode="indeterminate"></mat-progress-bar>

        <div class="video-grid">
          <mat-card *ngFor="let video of store.videos()" class="video-card">
            <img mat-card-image [src]="video.thumbnailPath || 'assets/no-thumb.png'" [alt]="video.title || 'Untitled'" class="thumbnail">
            <mat-card-content>
              <h3 class="video-title" [class.removed]="video.removedFromYoutube">{{ video.title }}</h3>
              <p class="video-meta">
                {{ video.downloadedAt | date }}
                <span *ngIf="video.removedFromYoutube" class="removed-badge">REMOVED</span>
              </p>
            </mat-card-content>
            <mat-card-actions align="end">
              <button mat-button (click)="openVideo(video.youtubeUrl)">
                <mat-icon>play_circle_outline</mat-icon> Watch
              </button>
            </mat-card-actions>
          </mat-card>
        </div>

        <div *ngIf="!store.loadingVideos() && store.videos().length === 0" class="no-results">
           <mat-icon>movie_filter</mat-icon>
           <p>No videos found</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .youtube-container { display: flex; height: calc(100vh - 64px); }
    .sidebar { width: 300px; border-right: 1px solid #eee; overflow-y: auto; background: #f9f9f9; }
    .sidebar-header { display: flex; justify-content: space-between; align-items: center; padding: 16px; border-bottom: 1px solid #eee; position: sticky; top: 0; background: #f9f9f9; z-index: 10; }
    .sidebar-header h2 { margin: 0; font-size: 1.1rem; }
    .content { flex: 1; overflow-y: auto; padding: 24px; display: flex; flex-direction: column; }
    .content-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; gap: 16px; }
    .search-field { flex: 1; }
    .actions { display: flex; gap: 8px; align-items: center; }
    .video-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; }
    .video-card { height: 100%; display: flex; flex-direction: column; }
    .thumbnail { height: 160px; object-fit: cover; }
    .video-title { font-size: 1rem; margin: 12px 0 4px; line-height: 1.4; height: 2.8em; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
    .video-title.removed { color: #f44336; text-decoration: line-through; }
    .video-meta { font-size: 0.85rem; color: #666; display: flex; justify-content: space-between; align-items: center; }
    .removed-badge { background: #ffebee; color: #f44336; padding: 2px 6px; border-radius: 4px; font-weight: bold; font-size: 0.7rem; }
    .no-results { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #999; }
    .no-results mat-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 16px; }
  `]
})
export class YoutubeComponent implements OnInit {
  readonly store = inject(YoutubeStore);
  private dialog = inject(MatDialog);

  ngOnInit() {
    this.store.loadLists();
  }

  openAddDialog() {
    const dialogRef = this.dialog.open(YoutubeListDialogComponent, { width: '500px' });
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
