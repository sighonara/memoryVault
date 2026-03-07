import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';
import { BookmarksStore } from './bookmarks.store';
import { BookmarkDialogComponent } from './bookmark-dialog';

@Component({
  selector: 'app-bookmarks',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatToolbarModule,
  ],
  providers: [BookmarksStore],
  template: `
    <div class="bookmarks-container">
      <mat-toolbar color="default" class="search-toolbar">
        <mat-form-field appearance="outline" class="search-field">
          <mat-label>Search bookmarks</mat-label>
          <input matInput (input)="onSearch($event)" [value]="store.searchQuery()" />
          <mat-icon matPrefix>search</mat-icon>
        </mat-form-field>
        <span class="spacer"></span>
        <button mat-raised-button color="primary" (click)="openAddDialog()">
          <mat-icon>add</mat-icon> Add Bookmark
        </button>
      </mat-toolbar>

      <div class="tags-container" *ngIf="getAllTags().length > 0">
        <mat-chip-listbox multiple>
          <mat-chip-option
            *ngFor="let tag of getAllTags()"
            [selected]="store.selectedTags().includes(tag)"
            (click)="toggleTag(tag)">
            {{ tag }}
          </mat-chip-option>
        </mat-chip-listbox>
      </div>

      <div class="loading-overlay" *ngIf="store.loading()">
        <mat-spinner diameter="40"></mat-spinner>
      </div>

      <div class="bookmarks-grid" *ngIf="!store.loading()">
        <mat-card *ngFor="let bookmark of store.bookmarks()" class="bookmark-card">
          <mat-card-header>
            <mat-card-title class="bookmark-title">
              <a [href]="bookmark.url" target="_blank">{{ bookmark.title || bookmark.url }}</a>
            </mat-card-title>
            <mat-card-subtitle class="bookmark-url">{{ bookmark.url }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <mat-chip-set class="bookmark-tags">
              <mat-chip *ngFor="let tag of bookmark.tags">{{ tag.name }}</mat-chip>
            </mat-chip-set>
          </mat-card-content>
          <mat-card-actions align="end">
            <button mat-icon-button color="warn" (click)="deleteBookmark(bookmark.id)">
              <mat-icon>delete</mat-icon>
            </button>
          </mat-card-actions>
        </mat-card>

        <div class="empty-state" *ngIf="store.bookmarks().length === 0">
          <mat-icon>bookmark_border</mat-icon>
          <p>No bookmarks found.</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .bookmarks-container { padding: 20px; max-width: 1200px; margin: 0 auto; }
    .search-toolbar { background: transparent; padding: 0; margin-bottom: 16px; gap: 16px; }
    .search-field { flex: 1; margin-top: 16px; }
    .spacer { flex: 1 1 auto; }
    .tags-container { margin-bottom: 24px; }
    .bookmarks-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(350px, 1fr)); gap: 20px; position: relative; min-height: 200px; }
    .bookmark-card { display: flex; flex-direction: column; transition: transform 0.2s; }
    .bookmark-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
    .bookmark-title { font-size: 1.1rem; word-break: break-word; line-height: 1.3; }
    .bookmark-title a { text-decoration: none; color: inherit; }
    .bookmark-title a:hover { color: #3f51b5; }
    .bookmark-url { font-size: 0.85rem; word-break: break-all; margin-top: 4px; }
    .bookmark-tags { margin-top: 8px; }
    .loading-overlay { display: flex; justify-content: center; padding: 40px; }
    .empty-state { grid-column: 1 / -1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 60px; color: #666; }
    .empty-state mat-icon { font-size: 64px; width: 64px; height: 64px; margin-bottom: 16px; opacity: 0.5; }
  `]
})
export class BookmarksComponent implements OnInit {
  readonly store = inject(BookmarksStore);
  private dialog = inject(MatDialog);

  ngOnInit() {
    this.store.loadBookmarks();
  }

  onSearch(event: any) {
    this.store.setSearchQuery((event.target as HTMLInputElement).value);
  }

  toggleTag(tag: string) {
    this.store.toggleTag(tag);
  }

  openAddDialog() {
    const dialogRef = this.dialog.open(BookmarkDialogComponent, { width: '500px' });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.store.addBookmark(result);
      }
    });
  }

  deleteBookmark(id: string) {
    if (confirm('Are you sure you want to delete this bookmark?')) {
      this.store.deleteBookmark(id);
    }
  }

  getAllTags(): string[] {
    const tagsSet = new Set<string>();
    this.store.bookmarks().forEach(b => b.tags.forEach(t => tagsSet.add(t.name)));
    return Array.from(tagsSet).sort();
  }
}
