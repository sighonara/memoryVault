import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DatePipe } from '@angular/common';

export interface BackupProviderView {
  id: string;
  type: string;
  name: string;
  isPrimary: boolean;
  createdAt: string;
}

export interface BackupStatsView {
  total: number;
  backedUp: number;
  pending: number;
  lost: number;
  failed: number;
}

@Component({
  selector: 'app-backup-panel',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatListModule, MatTooltipModule, DatePipe],
  template: `
    <div class="backup-panel">
      <h3>Backup Providers</h3>

      @if (providers().length === 0) {
        <p class="empty">No backup providers configured.</p>
      } @else {
        <mat-list>
          @for (p of providers(); track p.id) {
            <mat-list-item>
              <mat-icon matListItemIcon>{{ p.isPrimary ? 'cloud_done' : 'cloud_queue' }}</mat-icon>
              <span matListItemTitle>{{ p.name }}</span>
              <span matListItemLine>{{ p.type }}{{ p.isPrimary ? ' (primary)' : '' }} &mdash; added {{ p.createdAt | date:'mediumDate' }}</span>
              <button mat-icon-button matListItemMeta (click)="onDelete.emit(p.id)" matTooltip="Remove">
                <mat-icon>delete_outline</mat-icon>
              </button>
            </mat-list-item>
          }
        </mat-list>
      }

      <div class="actions">
        <button mat-stroked-button (click)="onAdd.emit()">
          <mat-icon>add</mat-icon> Add Provider
        </button>
        <button mat-stroked-button (click)="onBackfill.emit()" [disabled]="providers().length === 0">
          <mat-icon>backup</mat-icon> Backfill All
        </button>
      </div>

      @if (stats(); as s) {
        <div class="stats-grid">
          <div class="stat-item">
            <span class="stat-value">{{ s.backedUp }}</span>
            <span class="stat-label">Backed Up</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ s.pending }}</span>
            <span class="stat-label">Pending</span>
          </div>
          <div class="stat-item">
            <span class="stat-value warn">{{ s.lost }}</span>
            <span class="stat-label">Lost</span>
          </div>
          <div class="stat-item">
            <span class="stat-value warn">{{ s.failed }}</span>
            <span class="stat-label">Failed</span>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .backup-panel { padding: 16px; }
    h3 { font-size: 0.875rem; font-weight: 600; color: #202124; margin: 0 0 12px; }
    .empty { font-size: 0.8125rem; color: #80868b; }
    .actions { display: flex; gap: 8px; margin: 12px 0; }
    .stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-top: 16px; }
    .stat-item { text-align: center; }
    .stat-value { display: block; font-size: 1.5rem; font-weight: 600; color: #202124; }
    .stat-value.warn { color: #c62828; }
    .stat-label { font-size: 0.7rem; color: #80868b; text-transform: uppercase; }
  `]
})
export class BackupPanelComponent {
  providers = input.required<BackupProviderView[]>();
  stats = input.required<BackupStatsView | null>();

  onAdd = output<void>();
  onDelete = output<string>();
  onBackfill = output<void>();
}
