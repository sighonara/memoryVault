import { Component, DestroyRef, effect, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { BookmarksStore } from './bookmarks.store';
import { BookmarkDialogComponent } from './bookmark-dialog';
import { BookmarkTreeComponent } from './bookmark-tree/bookmark-tree';
import { BookmarkListComponent } from './bookmark-list/bookmark-list';
import { IngestPanelComponent } from './ingest-panel/ingest-panel';
import { ConflictReviewComponent } from './conflict-review/conflict-review';

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
    MatSnackBarModule,
    RouterLink,
    BookmarkTreeComponent,
    BookmarkListComponent,
    IngestPanelComponent,
  ],
  providers: [BookmarksStore],
  template: `
    <div class="bookmarks-page">
      <app-ingest-panel />

      @if (store.pendingIngests().length > 0) {
        <div class="pending-ingest-banner">
          <mat-icon>info</mat-icon>
          @for (pending of store.pendingIngests(); track pending.previewId) {
            <span>
              Import pending: {{ pending.totalCount }} bookmark(s) ready to review.
              <a [routerLink]="[]" [queryParams]="{ ingest: pending.previewId }">Review now</a>
            </span>
          }
        </div>
      }

      <mat-toolbar class="page-toolbar">
        <input type="text" class="toolbar-search" placeholder="Search bookmarks..."
               (input)="onSearch($event)" [value]="store.searchQuery()" />
        @if (store.searchQuery()) {
          <span class="result-count">{{ store.filteredBookmarks().length }} result(s)</span>
        }
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
        <div class="bookmark-manager">
          <div class="tree-panel">
            <app-bookmark-tree
              [folders]="store.folders()"
              (folderSelected)="store.selectFolder($event)"
              (contextAction)="onFolderAction($event)" />
          </div>
          <div class="list-panel">
            @if (store.filteredBookmarks().length === 0 && store.searchQuery()) {
              <div class="no-results">
                <mat-icon>search_off</mat-icon>
                <p>No bookmarks match "{{ store.searchQuery() }}"</p>
              </div>
            } @else {
              <app-bookmark-list
                [bookmarks]="store.filteredBookmarks()"
                (bookmarkDeleted)="deleteBookmark($event)"
                (bookmarkMoved)="store.moveBookmark($event)"
                (bulkDeleted)="bulkDelete($event)" />
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .bookmarks-page { display: flex; flex-direction: column; height: 100%; }
    .pending-ingest-banner {
      display: flex; align-items: center; gap: 8px;
      padding: 8px 16px; background: #e8f0fe; border-bottom: 1px solid #c6dafc;
      font-size: 0.8125rem; color: #1a73e8;
    }
    .pending-ingest-banner mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .pending-ingest-banner a { font-weight: 500; cursor: pointer; }
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
    .result-count { font-size: 0.75rem; color: #5f6368; white-space: nowrap; }
    .spacer { flex: 1 1 auto; }
    .tags-bar { padding: 8px 16px; border-bottom: 1px solid #e8eaed; background: #f8f9fa; }
    .tags-bar mat-chip-option { font-size: 0.75rem; height: 24px; }
    .loading { display: flex; justify-content: center; padding: 40px; }

    .bookmark-manager {
      display: flex; flex: 1; overflow: hidden;
    }
    .tree-panel {
      width: 240px; min-width: 200px;
      border-right: 1px solid #e8eaed;
      overflow-y: auto; background: #fff;
    }
    .list-panel {
      flex: 1; overflow-y: auto;
    }
    .no-results {
      display: flex; flex-direction: column; align-items: center;
      padding: 60px 20px; color: #9aa0a6;
    }
    .no-results mat-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 12px; }
    .no-results p { font-size: 0.875rem; margin: 0; }
  `]
})
export class BookmarksComponent implements OnInit {
  readonly store = inject(BookmarksStore);
  private dialog = inject(MatDialog);
  private destroyRef = inject(DestroyRef);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private snackBar = inject(MatSnackBar);
  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // Watch for ingest query param
    this.route.queryParamMap.pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(params => {
      const previewId = params.get('ingest');
      if (previewId) {
        this.store.fetchIngestPreview(previewId);
      }
    });

    // Handle ingest preview: auto-accept if no conflicts, otherwise open review dialog
    effect(() => {
      const preview = this.store.ingestPreview();
      if (!preview) return;

      const hasConflicts = preview.summary.movedCount > 0
        || preview.summary.titleChangedCount > 0
        || preview.summary.previouslyDeletedCount > 0;

      if (!hasConflicts && preview.summary.newCount > 0) {
        // All items are new — auto-accept without review
        const resolutions = preview.items
          .filter(i => i.status === 'NEW')
          .map(i => ({ url: i.url, action: 'ACCEPT' as const }));
        this.store.commitIngest({ previewId: preview.previewId, resolutions });
        this.snackBar.open(
          `Imported ${preview.summary.newCount} bookmark(s)`,
          'OK',
          { duration: 5000 },
        );
        // Clean up the query param
        this.router.navigate([], { queryParams: {}, replaceUrl: true });
      } else if (hasConflicts) {
        this.dialog.open(ConflictReviewComponent, {
          data: preview,
          width: '800px',
          disableClose: true,
        });
      }
    });
  }

  ngOnInit() {
    this.store.loadBookmarks();
    this.store.loadFolders();
    this.store.loadPendingIngests();
  }

  onSearch(event: any) {
    const query = (event.target as HTMLInputElement).value;
    if (this.searchTimeout) clearTimeout(this.searchTimeout);
    this.searchTimeout = setTimeout(() => {
      this.store.setSearchQuery(query);
    }, 300);
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

  bulkDelete(ids: string[]) {
    if (confirm(`Delete ${ids.length} bookmark(s)?`)) {
      ids.forEach(id => this.store.deleteBookmark(id));
    }
  }

  onFolderAction(event: { action: string; folderId: string | null }) {
    switch (event.action) {
      case 'new': {
        const name = prompt('Folder name:');
        if (name) {
          this.store.createFolder({ name, parentId: event.folderId ?? undefined });
        }
        break;
      }
      case 'rename': {
        const newName = prompt('New name:');
        if (newName && event.folderId) {
          this.store.renameFolder({ id: event.folderId, name: newName });
        }
        break;
      }
      case 'delete': {
        if (event.folderId && confirm('Delete this folder? Bookmarks will be moved to the parent folder.')) {
          this.store.deleteFolder(event.folderId);
        }
        break;
      }
    }
  }

  getAllTags(): string[] {
    const tagsSet = new Set<string>();
    this.store.bookmarks().forEach(b => b.tags.forEach(t => tagsSet.add(t.name)));
    return Array.from(tagsSet).sort();
  }
}
