import {
  Component,
  ChangeDetectionStrategy,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Bookmark } from '../../shared/graphql/generated';

export function sortByOrder<T extends { sortOrder: number }>(items: T[]): T[] {
  return [...items].sort((a, b) => a.sortOrder - b.sortOrder);
}

@Component({
  selector: 'app-bookmark-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatIconModule,
    MatButtonModule,
    MatCheckboxModule,
    MatChipsModule,
    MatMenuModule,
    MatTooltipModule,
  ],
  template: `
    @if (selectedIds().size > 0) {
      <div class="bulk-toolbar">
        <span class="bulk-count">{{ selectedIds().size }} selected</span>
        <button mat-button [matMenuTriggerFor]="bulkMenu">
          <mat-icon>drive_file_move</mat-icon> Move
        </button>
        <button mat-button (click)="bulkDelete()">
          <mat-icon>delete</mat-icon> Delete
        </button>
        <button mat-icon-button (click)="clearSelection()">
          <mat-icon>close</mat-icon>
        </button>
        <mat-menu #bulkMenu="matMenu">
          <button mat-menu-item (click)="bulkMoved.emit({ ids: Array.from(selectedIds()), folderId: null })">
            Unfiled
          </button>
        </mat-menu>
      </div>
    }

    <div class="bookmark-list">
      @for (bookmark of sortedBookmarks(); track bookmark.id) {
        <div class="bookmark-row" [class.selected]="selectedIds().has(bookmark.id)">
          <mat-checkbox
            [checked]="selectedIds().has(bookmark.id)"
            (change)="toggleSelect(bookmark.id)"
            class="row-checkbox">
          </mat-checkbox>
          <div class="bookmark-info">
            <a class="bookmark-title" [href]="bookmark.url" target="_blank">
              {{ bookmark.title || bookmark.url }}
            </a>
            <span class="bookmark-url">{{ bookmark.url }}</span>
          </div>
          <div class="bookmark-tags">
            @for (tag of bookmark.tags; track tag.name) {
              <span class="tag-label">{{ tag.name }}</span>
            }
          </div>
          <button mat-icon-button
                  [matMenuTriggerFor]="rowMenu"
                  class="row-actions"
                  matTooltip="Actions">
            <mat-icon>more_vert</mat-icon>
          </button>
          <mat-menu #rowMenu="matMenu">
            <button mat-menu-item (click)="bookmarkMoved.emit({ id: bookmark.id, folderId: '' })">
              <mat-icon>drive_file_move</mat-icon> Move to folder
            </button>
            <button mat-menu-item (click)="bookmarkDeleted.emit(bookmark.id)">
              <mat-icon>delete</mat-icon> Delete
            </button>
          </mat-menu>
        </div>
      } @empty {
        <div class="empty-state">
          <mat-icon>bookmark_border</mat-icon>
          <p>No bookmarks in this folder.</p>
        </div>
      }
    </div>
  `,
  styles: [`
    .bulk-toolbar {
      display: flex; align-items: center; gap: 8px;
      padding: 4px 16px; background: #e8f0fe;
      border-bottom: 1px solid #d2e3fc;
      font-size: 0.8125rem;
    }
    .bulk-count { font-weight: 500; color: #1a73e8; margin-right: 8px; }
    .bulk-toolbar button { font-size: 0.8125rem; }

    .bookmark-list { max-width: 900px; }
    .bookmark-row {
      display: flex; align-items: center; gap: 8px;
      padding: 6px 16px;
      border-bottom: 1px solid #f1f3f4;
      min-height: 0;
    }
    .bookmark-row:hover { background: #f8f9fa; }
    .bookmark-row.selected { background: #e8f0fe; }
    .row-checkbox { flex-shrink: 0; }
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
    .row-actions { opacity: 0; transition: opacity 0.15s; flex-shrink: 0; }
    .bookmark-row:hover .row-actions { opacity: 0.6; }
    .row-actions:hover { opacity: 1 !important; }

    .empty-state {
      display: flex; flex-direction: column; align-items: center;
      padding: 60px; color: #9aa0a6;
    }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 12px; opacity: 0.5; }
    .empty-state p { font-size: 0.8125rem; margin: 0; }
  `]
})
export class BookmarkListComponent {
  bookmarks = input.required<Bookmark[]>();
  bookmarkDeleted = output<string>();
  bookmarkMoved = output<{ id: string; folderId: string }>();
  bulkMoved = output<{ ids: string[]; folderId: string | null }>();
  bulkDeleted = output<string[]>();

  selectedIds = signal(new Set<string>());

  protected Array = Array;

  sortedBookmarks = computed(() => sortByOrder(this.bookmarks()));

  toggleSelect(id: string) {
    const current = new Set(this.selectedIds());
    if (current.has(id)) {
      current.delete(id);
    } else {
      current.add(id);
    }
    this.selectedIds.set(current);
  }

  clearSelection() {
    this.selectedIds.set(new Set());
  }

  bulkDelete() {
    this.bulkDeleted.emit(Array.from(this.selectedIds()));
    this.clearSelection();
  }
}
