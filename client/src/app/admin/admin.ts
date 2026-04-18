import { Component, DestroyRef, inject, OnInit } from '@angular/core';
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
          <app-stats-panel
            [stats]="store.stats()"
            [costSummary]="store.costSummary()"
            [costMonths]="store.costMonths()"
            [refreshingCosts]="store.refreshingCosts()"
            (onRefreshCosts)="store.refreshCosts()"
            (onCostMonthsChange)="store.setCostMonths($event)"
          />
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
            [followActive]="store.followActive()"
            (levelFilterChange)="store.setLogLevelFilter($event)"
            (serviceFilterChange)="store.setLogServiceFilter($event)"
            (followToggle)="toggleFollow()"
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
  private destroyRef = inject(DestroyRef);
  private followInterval: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.stopFollow());
  }

  ngOnInit() {
    this.store.loadStats();
    this.store.loadJobs();
    this.store.loadLogs();
    this.store.loadCosts();
  }

  onTabChange(index: number) {
    if (index === 0) { this.store.loadStats(); this.store.loadCosts(); }
    else if (index === 1) this.store.loadJobs();
    else if (index === 2) this.store.loadLogs();

    // Stop follow when navigating away from logs tab
    if (index !== 2) this.stopFollow();
  }

  toggleFollow() {
    if (this.followInterval) {
      this.stopFollow();
    } else {
      this.store.loadLogs();
      this.followInterval = setInterval(() => this.store.loadLogs(), 3000);
      this.store.setFollowActive(true);
    }
  }

  private stopFollow() {
    if (this.followInterval) {
      clearInterval(this.followInterval);
      this.followInterval = null;
    }
    this.store.setFollowActive(false);
  }
}
