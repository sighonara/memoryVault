import { Component, DestroyRef, effect, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { filter } from 'rxjs';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';
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
    BookmarkTreeComponent,
    BookmarkListComponent,
    IngestPanelComponent,
  ],
  providers: [BookmarksStore],
  template: `
    <div class="bookmarks-page">
      <app-ingest-panel />

      <mat-toolbar class="page-toolbar">
        <input type="text" class="toolbar-search" placeholder="Search bookmarks..."
               (input)="onSearch($event)" [value]="store.searchQuery()" />
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
            <app-bookmark-list
              [bookmarks]="store.filteredBookmarks()"
              (bookmarkDeleted)="deleteBookmark($event)"
              (bookmarkMoved)="store.moveBookmark($event)"
              (bulkDeleted)="bulkDelete($event)" />
          </div>
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
  `]
})
export class BookmarksComponent implements OnInit {
  readonly store = inject(BookmarksStore);
  private dialog = inject(MatDialog);
  private destroyRef = inject(DestroyRef);
  private route = inject(ActivatedRoute);

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

    // Open conflict review dialog when ingest preview is loaded
    effect(() => {
      const preview = this.store.ingestPreview();
      if (preview) {
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
