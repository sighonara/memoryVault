import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
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
    FormsModule,
  ],
  template: `
    <div class="log-toolbar">
      <mat-form-field appearance="outline" class="filter-field">
        <mat-label>Level</mat-label>
        <mat-select [value]="levelFilter()" (selectionChange)="levelFilterChange.emit($event.value || null)">
          <mat-option [value]="null">All</mat-option>
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
          <span [class]="'level level-' + log.level.toLowerCase()">{{ log.level }}</span>
        </td>
      </ng-container>

      <ng-container matColumnDef="logger">
        <th mat-header-cell *matHeaderCellDef>Logger</th>
        <td mat-cell *matCellDef="let log" class="mono logger-cell">{{ shortLogger(log.logger) }}</td>
      </ng-container>

      <ng-container matColumnDef="message">
        <th mat-header-cell *matHeaderCellDef>Message</th>
        <td mat-cell *matCellDef="let log" class="message-cell">{{ log.message }}</td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns;" [class]="'log-row-' + row.level.toLowerCase()"></tr>
    </table>

    @if (logs().length === 0) {
      <div class="empty">No log entries found.</div>
    }
  `,
  styles: [`
    .log-toolbar { display: flex; gap: 12px; padding: 8px 16px; }
    .filter-field { min-width: 120px; }
    .log-table { width: 100%; }
    .mono { font-family: "SF Mono", "Fira Code", monospace; font-size: 0.75rem; }
    .logger-cell { max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .message-cell { max-width: 500px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .level { font-size: 0.65rem; font-weight: 600; padding: 1px 4px; border-radius: 2px; }
    .level-error { background: #ffebee; color: #c62828; }
    .level-warn { background: #fff8e1; color: #f57f17; }
    .level-info { background: #e8f5e9; color: #2e7d32; }
    .level-debug { background: #f3e5f5; color: #6a1b9a; }
    .log-row-error { background: #fffafa; }
    .log-row-warn { background: #fffde7; }
    .empty { padding: 40px; text-align: center; color: #9aa0a6; font-size: 0.8125rem; }
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
