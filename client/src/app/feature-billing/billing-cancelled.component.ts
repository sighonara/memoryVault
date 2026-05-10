import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-billing-cancelled',
  standalone: true,
  imports: [RouterLink, MatButtonModule],
  template: `
    <div style="text-align:center;padding:64px">
      <h2>Checkout cancelled</h2>
      <p>No charges were made.</p>
      <a mat-button routerLink="/billing">Back to Billing</a>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BillingCancelledComponent {}
