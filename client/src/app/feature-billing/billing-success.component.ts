import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { TierService } from '../core/services/tier.service';

@Component({
  selector: 'app-billing-success',
  standalone: true,
  imports: [RouterLink, MatButtonModule],
  template: `
    <div style="text-align:center;padding:64px">
      <h2>Subscription activated!</h2>
      <p>Your new tier is now active.</p>
      <a mat-raised-button color="primary" routerLink="/billing">View Billing</a>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BillingSuccessComponent implements OnInit {
  private tierService = inject(TierService);
  ngOnInit() { this.tierService.load(); }
}
