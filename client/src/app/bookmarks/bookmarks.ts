import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';
import { BookmarksStore } from './bookmarks.store';
import { BookmarkDialogComponent } from './bookmark-dialog';

@Component({
  selector: 'app-bookmarks',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatToolbarModule,
  ],
  providers: [BookmarksStore],
  template: `
    <div class="bookmarks-page">
      <mat-toolbar class="page-toolbar">
        <input type="text" class="toolbar-search" placeholder="Search bookmarks..." (input)="onSearch($event)" [value]="store.searchQuery()" />
        <span class="spacer"></span>
        <button mat-stroked-button (click)="openAddDialog()">
          <mat-icon>add</mat-icon> Add
        </button>
      </mat-toolbar>

      @if (getAllTags().length > 0) {
        <div class="tags-bar">
          <mat-chip-listbox multiple>
            @for (tag of getAllTags(); track tag) {
              <mat-chip-option
                [selected]="store.selectedTags().includes(tag)"
                (click)="toggleTag(tag)">
                {{ tag }}
              </mat-chip-option>
            }
          </mat-chip-listbox>
        </div>
      }

      @if (store.loading()) {
        <div class="loading">
          <mat-spinner diameter="32"></mat-spinner>
        </div>
      } @else {
        <div class="bookmark-list">
          @for (bookmark of store.bookmarks(); track bookmark.id) {
            <div class="bookmark-row">
              <div class="bookmark-info">
                <a class="bookmark-title" [href]="bookmark.url" target="_blank">{{ bookmark.title || bookmark.url }}</a>
                <span class="bookmark-url">{{ bookmark.url }}</span>
              </div>
              <div class="bookmark-tags">
                @for (tag of bookmark.tags; track tag.name) {
                  <span class="tag-label">{{ tag.name }}</span>
                }
              </div>
              <button mat-icon-button (click)="deleteBookmark(bookmark.id)" class="delete-btn">
                <mat-icon>close</mat-icon>
              </button>
            </div>
          } @empty {
            <div class="empty-state">
              <mat-icon>bookmark_border</mat-icon>
              <p>No bookmarks found.</p>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .bookmarks-page { display: flex; flex-direction: column; height: 100%; }
    .page-toolbar {
      background: #fff; border-bottom: 1px solid #dadce0;
      min-height: 40px; height: 40px; padding: 0 16px;
      gap: 12px;
    }
    .toolbar-search {
      flex: 1; max-width: 400px; height: 30px;
      border: 1px solid #dadce0; border-radius: 4px; padding: 0 10px;
      font-size: 0.8125rem; font-family: inherit; color: #202124;
      background: #fff; outline: none;
    }
    .toolbar-search:focus { border-color: #1a73e8; }
    .spacer { flex: 1 1 auto; }
    .tags-bar { padding: 8px 16px; border-bottom: 1px solid #e8eaed; background: #f8f9fa; }
    .tags-bar mat-chip-option { font-size: 0.75rem; height: 24px; }
    .loading { display: flex; justify-content: center; padding: 40px; }

    .bookmark-list { max-width: 900px; }
    .bookmark-row {
      display: flex; align-items: center; gap: 12px;
      padding: 8px 16px;
      border-bottom: 1px solid #f1f3f4;
      min-height: 0;
    }
    .bookmark-row:hover { background: #f8f9fa; }
    .bookmark-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
    .bookmark-title {
      font-size: 0.875rem; font-weight: 500; color: #1a73e8;
      text-decoration: none;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .bookmark-title:hover { text-decoration: underline; }
    .bookmark-url {
      font-size: 0.7rem; color: #80868b;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .bookmark-tags { display: flex; gap: 4px; flex-shrink: 0; }
    .tag-label {
      font-size: 0.65rem; padding: 1px 6px; border-radius: 3px;
      background: #e8eaed; color: #5f6368;
    }
    .delete-btn { opacity: 0; transition: opacity 0.15s; flex-shrink: 0; }
    .bookmark-row:hover .delete-btn { opacity: 0.6; }
    .delete-btn:hover { opacity: 1 !important; }

    .empty-state {
      display: flex; flex-direction: column; align-items: center;
      padding: 60px; color: #9aa0a6;
    }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 12px; opacity: 0.5; }
    .empty-state p { font-size: 0.8125rem; margin: 0; }
  `]
})
export class BookmarksComponent implements OnInit {
  readonly store = inject(BookmarksStore);
  private dialog = inject(MatDialog);
  private destroyRef = inject(DestroyRef);

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
    const dialogRef = this.dialog.open(BookmarkDialogComponent, { width: '420px' });
    dialogRef.afterClosed().pipe(
      takeUntilDestroyed(this.destroyRef),
      filter((result): result is { url: string; title?: string; tags?: string[] } => !!result)
    ).subscribe(result => this.store.addBookmark(result));
  }

  deleteBookmark(id: string) {
    if (confirm('Delete this bookmark?')) {
      this.store.deleteBookmark(id);
    }
  }

  getAllTags(): string[] {
    const tagsSet = new Set<string>();
    this.store.bookmarks().forEach(b => b.tags.forEach(t => tagsSet.add(t.name)));
    return Array.from(tagsSet).sort();
  }
}
