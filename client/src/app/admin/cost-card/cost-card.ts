import { Component, input, output, signal, computed } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { CostSummary } from '../../shared/graphql/generated';

@Component({
  selector: 'app-cost-card',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, MatProgressSpinnerModule, MatSelectModule],
  template: `
    <div class="cost-card" [class.expanded]="expanded()">
      <div class="cost-header" (click)="expanded.set(!expanded())">
        <mat-icon>payments</mat-icon>
        <div class="cost-body">
          @if (costSummary()?.current) {
            <span class="stat-value">\${{ costSummary()!.current!.totalCostUsd }}</span>
            <span class="stat-label">{{ currentMonthLabel() }}</span>
            @if (trend()) {
              <span class="trend" [class.up]="trend()! > 0" [class.down]="trend()! < 0">
                {{ trend()! > 0 ? '+' : '' }}{{ trend()!.toFixed(0) }}% vs last month
              </span>
            }
          } @else {
            <span class="stat-label">No cost data available</span>
          }
        </div>
        <mat-icon class="expand-icon">{{ expanded() ? 'expand_less' : 'expand_more' }}</mat-icon>
      </div>

      @if (expanded()) {
        <div class="cost-detail">
          @if (costSummary()?.current) {
            <div class="section">
              <div class="section-header">
                <span>Per-Service Breakdown</span>
                <button mat-icon-button (click)="onRefresh.emit(); $event.stopPropagation()" [disabled]="refreshing()">
                  @if (refreshing()) {
                    <mat-spinner diameter="18"></mat-spinner>
                  } @else {
                    <mat-icon>refresh</mat-icon>
                  }
                </button>
              </div>
              <table class="breakdown-table">
                @for (entry of serviceEntries(); track entry.name) {
                  <tr>
                    <td>{{ entry.name }}</td>
                    <td class="amount">\${{ entry.cost }}</td>
                  </tr>
                }
              </table>
              <div class="fetched-at">Last updated: {{ formatDate(costSummary()!.current!.fetchedAt) }}</div>
            </div>
          }

          @if (costSummary()?.monthlyTotals?.length) {
            <div class="section">
              <div class="section-header">
                <span>Monthly History</span>
                <mat-select [value]="months()" (selectionChange)="onMonthsChange.emit($event.value)" class="month-select">
                  <mat-option [value]="3">3 months</mat-option>
                  <mat-option [value]="6">6 months</mat-option>
                  <mat-option [value]="12">12 months</mat-option>
                  <mat-option [value]="120">All</mat-option>
                </mat-select>
              </div>
              <table class="breakdown-table">
                @for (m of costSummary()!.monthlyTotals; track m.month) {
                  <tr>
                    <td>{{ m.month }}</td>
                    <td class="amount">\${{ m.totalCostUsd }}</td>
                  </tr>
                }
              </table>
            </div>
          }

          @if (!costSummary()?.current) {
            <div class="no-data">
              <button mat-stroked-button (click)="onRefresh.emit()">
                <mat-icon>refresh</mat-icon> Fetch cost data
              </button>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .cost-card { border-bottom: 1px solid #e8eaed; border-right: 1px solid #e8eaed; }
    .cost-header {
      display: flex; align-items: flex-start; gap: 10px;
      padding: 12px 16px; cursor: pointer;
    }
    .cost-header:hover { background: #f8f9fa; }
    .cost-header mat-icon:first-child { color: #5f6368; font-size: 20px; width: 20px; height: 20px; margin-top: 2px; overflow: visible; flex-shrink: 0; }
    .cost-body { display: flex; flex-direction: column; flex: 1; }
    .stat-value { font-size: 1.25rem; font-weight: 600; color: #202124; line-height: 1; }
    .stat-label { font-size: 0.75rem; color: #5f6368; margin-top: 2px; }
    .trend { font-size: 0.7rem; margin-top: 2px; }
    .trend.up { color: #c62828; }
    .trend.down { color: #2e7d32; }
    .expand-icon { color: #9aa0a6; margin-left: auto; }
    .cost-detail { padding: 0 16px 16px 46px; }
    .section { margin-bottom: 16px; }
    .section-header { display: flex; align-items: center; justify-content: space-between; font-size: 0.8125rem; font-weight: 500; color: #5f6368; margin-bottom: 8px; }
    .breakdown-table { width: 100%; font-size: 0.8125rem; border-collapse: collapse; }
    .breakdown-table tr { border-bottom: 1px solid #f1f3f4; }
    .breakdown-table td { padding: 4px 0; }
    .breakdown-table .amount { text-align: right; font-variant-numeric: tabular-nums; }
    .fetched-at { font-size: 0.6875rem; color: #9aa0a6; margin-top: 8px; }
    .month-select { width: 120px; }
    .no-data { padding: 16px 0; text-align: center; }
  `],
})
export class CostCardComponent {
  costSummary = input<CostSummary | null>(null);
  months = input<number>(6);
  refreshing = input<boolean>(false);
  onRefresh = output<void>();
  onMonthsChange = output<number>();

  expanded = signal(false);

  serviceEntries = computed(() => {
    const current = this.costSummary()?.current;
    if (!current) return [];
    const costs: Record<string, string> = JSON.parse(current.serviceCosts);
    return Object.entries(costs)
      .map(([name, cost]) => ({ name, cost }))
      .sort((a, b) => parseFloat(b.cost) - parseFloat(a.cost));
  });

  currentMonthLabel = computed(() => {
    const current = this.costSummary()?.current;
    if (!current) return '';
    const date = new Date(current.billingDate + 'T00:00:00');
    return date.toLocaleString('default', { month: 'long', year: 'numeric' });
  });

  trend = computed(() => {
    const totals = this.costSummary()?.monthlyTotals;
    if (!totals || totals.length < 2) return null;
    const current = parseFloat(totals[0].totalCostUsd);
    const previous = parseFloat(totals[1].totalCostUsd);
    if (previous === 0) return null;
    return ((current - previous) / previous) * 100;
  });

  formatDate(ts: any): string {
    if (!ts) return 'Unknown';
    return new Date(ts).toLocaleString();
  }
}
