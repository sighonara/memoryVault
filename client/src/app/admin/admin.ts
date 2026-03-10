import { Component, inject, OnInit } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { AdminStore } from './admin.store';
import { StatsPanelComponent } from './stats-panel/stats-panel';
import { JobsTableComponent } from './jobs-table/jobs-table';
import { LogViewerComponent } from './log-viewer/log-viewer';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [
    MatTabsModule,
    MatProgressSpinnerModule,
    MatIconModule,
    StatsPanelComponent,
    JobsTableComponent,
    LogViewerComponent,
  ],
  providers: [AdminStore],
  template: `
    <div class="admin-container">
      <div class="admin-header">
        <mat-icon>admin_panel_settings</mat-icon>
        <h1>Admin</h1>
      </div>

      @if (store.loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      }

      <mat-tab-group animationDuration="200ms" (selectedTabChange)="onTabChange($event.index)">
        <mat-tab label="Stats">
          <app-stats-panel [stats]="store.stats()" />
        </mat-tab>

        <mat-tab label="Jobs">
          <app-jobs-table
            [jobs]="store.jobs()"
            [typeFilter]="store.jobTypeFilter()"
            (typeFilterChange)="store.setJobTypeFilter($event)"
          />
        </mat-tab>

        <mat-tab label="Logs">
          <app-log-viewer
            [logs]="store.logs()"
            [levelFilter]="store.logLevelFilter()"
            [serviceFilter]="store.logServiceFilter()"
            (levelFilterChange)="store.setLogLevelFilter($event)"
            (serviceFilterChange)="store.setLogServiceFilter($event)"
          />
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .admin-container { padding: 20px; max-width: 1400px; margin: 0 auto; }
    .admin-header { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; }
    .admin-header h1 { margin: 0; font-size: 1.8rem; font-weight: 500; }
    .admin-header mat-icon { font-size: 32px; width: 32px; height: 32px; color: #5c6bc0; }
    .loading { display: flex; justify-content: center; padding: 20px; }
    mat-tab-group { background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
  `],
})
export class AdminComponent implements OnInit {
  readonly store = inject(AdminStore);

  ngOnInit() {
    this.store.loadStats();
    this.store.loadJobs();
    this.store.loadLogs();
  }

  onTabChange(index: number) {
    if (index === 0) this.store.loadStats();
    else if (index === 1) this.store.loadJobs();
    else if (index === 2) this.store.loadLogs();
  }
}
