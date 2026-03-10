import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SyncJob } from '../../shared/graphql/generated';

const JOB_TYPES = ['FEED_SYNC', 'YOUTUBE_SYNC'];

@Component({
  selector: 'app-jobs-table',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatChipsModule,
    MatSelectModule,
    MatFormFieldModule,
    MatIconModule,
    MatTooltipModule,
  ],
  template: `
    <div class="jobs-toolbar">
      <mat-form-field appearance="outline" class="filter-field">
        <mat-label>Filter by type</mat-label>
        <mat-select [value]="typeFilter()" (selectionChange)="typeFilterChange.emit($event.value || null)">
          <mat-option [value]="null">All types</mat-option>
          @for (t of jobTypes; track t) {
            <mat-option [value]="t">{{ t }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </div>

    <table mat-table [dataSource]="jobs()" class="jobs-table">
      <ng-container matColumnDef="type">
        <th mat-header-cell *matHeaderCellDef>Type</th>
        <td mat-cell *matCellDef="let job">{{ job.type }}</td>
      </ng-container>

      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let job">
          <mat-chip [class]="'status-chip status-' + job.status.toLowerCase()">
            {{ job.status }}
          </mat-chip>
        </td>
      </ng-container>

      <ng-container matColumnDef="startedAt">
        <th mat-header-cell *matHeaderCellDef>Started</th>
        <td mat-cell *matCellDef="let job">{{ formatDate(job.startedAt) }}</td>
      </ng-container>

      <ng-container matColumnDef="duration">
        <th mat-header-cell *matHeaderCellDef>Duration</th>
        <td mat-cell *matCellDef="let job">{{ formatDuration(job.startedAt, job.completedAt) }}</td>
      </ng-container>

      <ng-container matColumnDef="triggeredBy">
        <th mat-header-cell *matHeaderCellDef>Triggered By</th>
        <td mat-cell *matCellDef="let job">{{ job.triggeredBy }}</td>
      </ng-container>

      <ng-container matColumnDef="error">
        <th mat-header-cell *matHeaderCellDef>Error</th>
        <td mat-cell *matCellDef="let job">
          @if (job.errorMessage) {
            <mat-icon color="warn" [matTooltip]="job.errorMessage">error_outline</mat-icon>
          }
        </td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
    </table>

    @if (jobs().length === 0) {
      <div class="empty-state">No jobs found.</div>
    }
  `,
  styles: [`
    .jobs-toolbar { padding: 8px 0; }
    .filter-field { min-width: 200px; }
    .jobs-table { width: 100%; }
    .status-chip { font-size: 0.75rem; min-height: 24px; padding: 0 8px; }
    .status-running { background: #e3f2fd !important; color: #1565c0 !important; }
    .status-success { background: #e8f5e9 !important; color: #2e7d32 !important; }
    .status-failure { background: #ffebee !important; color: #c62828 !important; }
    .empty-state { padding: 40px; text-align: center; color: #999; }
  `],
})
export class JobsTableComponent {
  jobs = input<SyncJob[]>([]);
  typeFilter = input<string | null>(null);
  typeFilterChange = output<string | null>();

  displayedColumns = ['type', 'status', 'startedAt', 'duration', 'triggeredBy', 'error'];
  jobTypes = JOB_TYPES;

  formatDate(ts: any): string {
    if (!ts) return '—';
    return new Date(ts).toLocaleString();
  }

  formatDuration(start: any, end: any): string {
    if (!start || !end) return '—';
    const ms = new Date(end).getTime() - new Date(start).getTime();
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${Math.floor(ms / 60000)}m ${Math.round((ms % 60000) / 1000)}s`;
  }
}
