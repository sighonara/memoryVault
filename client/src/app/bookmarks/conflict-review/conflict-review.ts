import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
} from '@angular/core';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatExpansionModule } from '@angular/material/expansion';
import { BookmarksStore, IngestPreviewResult } from '../bookmarks.store';

export interface IngestItemView {
  url: string;
  title: string;
  status: string;
  existingBookmarkId?: string | null;
  suggestedFolderId?: string | null;
  browserFolder?: string | null;
}

export function groupByStatus(items: IngestItemView[]): Record<string, IngestItemView[]> {
  const groups: Record<string, IngestItemView[]> = {};
  for (const item of items) {
    if (!groups[item.status]) {
      groups[item.status] = [];
    }
    groups[item.status].push(item);
  }
  return groups;
}

export function buildResolutionArray(resolutions: Map<string, string>): Array<{ url: string; action: string }> {
  return Array.from(resolutions.entries()).map(([url, action]) => ({ url, action }));
}

const STATUS_LABELS: Record<string, string> = {
  NEW: 'New Bookmarks',
  MOVED: 'Moved',
  TITLE_CHANGED: 'Title Changed',
  PREVIOUSLY_DELETED: 'Previously Deleted',
  UNCHANGED: 'Unchanged',
};

const DEFAULT_ACTIONS: Record<string, string> = {
  NEW: 'ACCEPT',
  MOVED: 'ACCEPT',
  TITLE_CHANGED: 'ACCEPT',
  PREVIOUSLY_DELETED: 'SKIP',
  UNCHANGED: 'SKIP',
};

@Component({
  selector: 'app-conflict-review',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatButtonToggleModule,
    MatExpansionModule,
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon>compare_arrows</mat-icon>
      Review Import
    </h2>

    <mat-dialog-content>
      <div class="summary-bar">
        <span class="summary-item">{{ preview.summary.newCount }} new</span>
        <span class="summary-item">{{ preview.summary.movedCount }} moved</span>
        <span class="summary-item">{{ preview.summary.titleChangedCount }} title changed</span>
        <span class="summary-item">{{ preview.summary.previouslyDeletedCount }} previously deleted</span>
        <span class="summary-item muted">{{ preview.summary.unchangedCount }} unchanged</span>
      </div>

      @for (status of visibleStatuses(); track status) {
        <div class="status-group">
          <div class="group-header">
            <span class="group-title">{{ statusLabel(status) }} ({{ grouped()[status].length }})</span>
            <span class="group-actions">
              <button mat-button (click)="setAllInGroup(status, 'ACCEPT')">Accept All</button>
              <button mat-button (click)="setAllInGroup(status, 'SKIP')">Skip All</button>
              @if (status === 'PREVIOUSLY_DELETED') {
                <button mat-button (click)="setAllInGroup(status, 'UNDELETE')">Undelete All</button>
              }
            </span>
          </div>

          @for (item of grouped()[status]; track item.url) {
            <div class="review-item">
              <div class="item-info">
                <span class="item-title">{{ item.title }}</span>
                <span class="item-url">{{ item.url }}</span>
                @if (item.browserFolder) {
                  <span class="item-folder">Browser folder: {{ item.browserFolder }}</span>
                }
              </div>
              <mat-button-toggle-group
                [value]="resolutions().get(item.url)"
                (change)="setResolution(item.url, $event.value)">
                <mat-button-toggle value="ACCEPT">Accept</mat-button-toggle>
                <mat-button-toggle value="SKIP">Skip</mat-button-toggle>
                @if (status === 'PREVIOUSLY_DELETED') {
                  <mat-button-toggle value="UNDELETE">Undelete</mat-button-toggle>
                }
              </mat-button-toggle-group>
            </div>
          }
        </div>
      }

      @if (grouped()['UNCHANGED']?.length) {
        <mat-expansion-panel class="unchanged-panel">
          <mat-expansion-panel-header>
            <mat-panel-title>{{ grouped()['UNCHANGED'].length }} unchanged (skipped)</mat-panel-title>
          </mat-expansion-panel-header>
          @for (item of grouped()['UNCHANGED']; track item.url) {
            <div class="review-item muted">
              <span class="item-title">{{ item.title }}</span>
              <span class="item-url">{{ item.url }}</span>
            </div>
          }
        </mat-expansion-panel>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" (click)="commit()">
        Commit ({{ acceptCount() }} accept, {{ skipCount() }} skip)
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    h2 { display: flex; align-items: center; gap: 8px; }
    .summary-bar {
      display: flex; gap: 16px; padding: 8px 0 16px;
      font-size: 0.8125rem; color: #3c4043;
    }
    .summary-item.muted { color: #9aa0a6; }
    .status-group { margin-bottom: 16px; }
    .group-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 6px 0; border-bottom: 1px solid #e8eaed;
    }
    .group-title { font-weight: 500; font-size: 0.875rem; }
    .group-actions button { font-size: 0.75rem; }
    .review-item {
      display: flex; align-items: center; justify-content: space-between;
      padding: 8px 0; border-bottom: 1px solid #f1f3f4;
      gap: 12px;
    }
    .review-item.muted { opacity: 0.6; }
    .item-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .item-title { font-size: 0.8125rem; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .item-url { font-size: 0.7rem; color: #80868b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .item-folder { font-size: 0.7rem; color: #5f6368; font-style: italic; }
    mat-button-toggle-group { font-size: 0.75rem; flex-shrink: 0; }
    .unchanged-panel { margin-top: 8px; }
  `]
})
export class ConflictReviewComponent {
  private dialogRef = inject(MatDialogRef<ConflictReviewComponent>);
  readonly preview: IngestPreviewResult = inject(MAT_DIALOG_DATA);
  private store = inject(BookmarksStore);

  resolutions = signal(new Map<string, string>());

  grouped = computed(() => groupByStatus(this.preview.items as IngestItemView[]));

  visibleStatuses = computed(() => {
    const g = this.grouped();
    return ['NEW', 'MOVED', 'TITLE_CHANGED', 'PREVIOUSLY_DELETED'].filter(s => g[s]?.length > 0);
  });

  acceptCount = computed(() => {
    let count = 0;
    for (const action of this.resolutions().values()) {
      if (action === 'ACCEPT' || action === 'UNDELETE') count++;
    }
    return count;
  });

  skipCount = computed(() => {
    let count = 0;
    for (const action of this.resolutions().values()) {
      if (action === 'SKIP') count++;
    }
    return count;
  });

  constructor() {
    // Set default resolutions
    const defaults = new Map<string, string>();
    for (const item of this.preview.items) {
      defaults.set(item.url, DEFAULT_ACTIONS[item.status] ?? 'SKIP');
    }
    this.resolutions.set(defaults);
  }

  statusLabel(status: string): string {
    return STATUS_LABELS[status] ?? status;
  }

  setResolution(url: string, action: string) {
    const next = new Map(this.resolutions());
    next.set(url, action);
    this.resolutions.set(next);
  }

  setAllInGroup(status: string, action: string) {
    const next = new Map(this.resolutions());
    for (const item of this.grouped()[status]) {
      next.set(item.url, action);
    }
    this.resolutions.set(next);
  }

  commit() {
    const resolutionArray = buildResolutionArray(this.resolutions());
    this.store.commitIngest({
      previewId: this.preview.previewId,
      resolutions: resolutionArray as any,
    });
    this.dialogRef.close();
  }
}
