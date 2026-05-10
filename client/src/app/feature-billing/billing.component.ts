import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { TierService } from '../core/services/tier.service';

interface SubscriptionInfo {
  tier: string;
  status: string;
  billingInterval: string;
  gracePeriodEndsAt?: string;
}

@Component({
  selector: 'app-billing',
  standalone: true,
  imports: [DatePipe, RouterLink, MatCardModule, MatButtonModule, MatDividerModule],
  templateUrl: './billing.component.html',
  styleUrl: './billing.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BillingComponent implements OnInit {
  private http = inject(HttpClient);
  readonly tierService = inject(TierService);

  subscription = signal<SubscriptionInfo | null>(null);
  loading = signal(false);

  ngOnInit(): void {
    this.http.get<SubscriptionInfo | null>('/api/billing/subscription').subscribe(s => this.subscription.set(s));
  }

  upgradeToPremium(interval: 'MONTHLY' | 'ANNUAL'): void {
    this.initiateCheckout('PREMIUM', interval);
  }

  upgradeToArchive(interval: 'MONTHLY' | 'ANNUAL'): void {
    this.initiateCheckout('ARCHIVE', interval);
  }

  openPortal(): void {
    this.loading.set(true);
    this.http.post<{ url: string }>('/api/billing/portal', {}).subscribe({
      next: r => window.location.href = r.url,
      error: () => this.loading.set(false)
    });
  }

  private initiateCheckout(tier: string, interval: string): void {
    this.loading.set(true);
    this.http.post<{ url: string }>('/api/billing/checkout', { tier, interval }).subscribe({
      next: r => window.location.href = r.url,
      error: () => this.loading.set(false)
    });
  }
}
