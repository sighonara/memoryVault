import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { FormsModule } from '@angular/forms';
import { LogEntry } from '../../shared/graphql/generated';

const LOG_LEVELS = ['ERROR', 'WARN', 'INFO', 'DEBUG'];

@Component({
  selector: 'app-log-viewer',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatChipsModule,
    FormsModule,
  ],
  template: `
    <div class="log-toolbar">
      <mat-form-field appearance="outline" class="filter-field">
        <mat-label>Level</mat-label>
        <mat-select [value]="levelFilter()" (selectionChange)="levelFilterChange.emit($event.value || null)">
          <mat-option [value]="null">All levels</mat-option>
          @for (l of logLevels; track l) {
            <mat-option [value]="l">{{ l }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <mat-form-field appearance="outline" class="filter-field">
        <mat-label>Service</mat-label>
        <input
          matInput
          [value]="serviceFilter() ?? ''"
          (input)="serviceFilterChange.emit($any($event.target).value)"
          placeholder="e.g. FeedService"
        />
      </mat-form-field>
    </div>

    <table mat-table [dataSource]="logs()" class="log-table">
      <ng-container matColumnDef="timestamp">
        <th mat-header-cell *matHeaderCellDef>Time</th>
        <td mat-cell *matCellDef="let log" class="mono">{{ formatTime(log.timestamp) }}</td>
      </ng-container>

      <ng-container matColumnDef="level">
        <th mat-header-cell *matHeaderCellDef>Level</th>
        <td mat-cell *matCellDef="let log">
          <span [class]="'level-badge level-' + log.level.toLowerCase()">{{ log.level }}</span>
        </td>
      </ng-container>

      <ng-container matColumnDef="logger">
        <th mat-header-cell *matHeaderCellDef>Logger</th>
        <td mat-cell *matCellDef="let log" class="mono logger">{{ shortLogger(log.logger) }}</td>
      </ng-container>

      <ng-container matColumnDef="message">
        <th mat-header-cell *matHeaderCellDef>Message</th>
        <td mat-cell *matCellDef="let log" class="message-cell">{{ log.message }}</td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns;" [class]="'log-row level-row-' + row.level.toLowerCase()"></tr>
    </table>

    @if (logs().length === 0) {
      <div class="empty-state">No log entries found.</div>
    }
  `,
  styles: [`
    .log-toolbar { display: flex; gap: 16px; padding: 8px 0; flex-wrap: wrap; }
    .filter-field { min-width: 160px; }
    .log-table { width: 100%; font-size: 0.85rem; }
    .mono { font-family: monospace; }
    .logger { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .message-cell { max-width: 600px; word-break: break-word; }
    .level-badge { padding: 2px 6px; border-radius: 4px; font-size: 0.75rem; font-weight: 600; }
    .level-error { background: #ffebee; color: #c62828; }
    .level-warn { background: #fff8e1; color: #f57f17; }
    .level-info { background: #e8f5e9; color: #2e7d32; }
    .level-debug { background: #f3e5f5; color: #6a1b9a; }
    .log-row-error { background: #fff8f8; }
    .log-row-warn { background: #fffde7; }
    .empty-state { padding: 40px; text-align: center; color: #999; }
  `],
})
export class LogViewerComponent {
  logs = input<LogEntry[]>([]);
  levelFilter = input<string | null>(null);
  serviceFilter = input<string | null>(null);
  levelFilterChange = output<string | null>();
  serviceFilterChange = output<string>();

  displayedColumns = ['timestamp', 'level', 'logger', 'message'];
  logLevels = LOG_LEVELS;

  formatTime(ts: any): string {
    if (!ts) return '—';
    return new Date(ts).toLocaleTimeString();
  }

  shortLogger(logger: string): string {
    if (!logger) return '';
    const parts = logger.split('.');
    return parts[parts.length - 1];
  }
}
