import { Component, inject, OnInit } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
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
    StatsPanelComponent,
    JobsTableComponent,
    LogViewerComponent,
  ],
  providers: [AdminStore],
  template: `
    <div class="admin-page">
      @if (store.loading()) {
        <div class="loading"><mat-spinner diameter="28"></mat-spinner></div>
      }

      <mat-tab-group animationDuration="150ms" (selectedTabChange)="onTabChange($event.index)">
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
    .admin-page { height: 100%; display: flex; flex-direction: column; }
    .loading { display: flex; justify-content: center; padding: 16px; }
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
