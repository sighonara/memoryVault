import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="padding: 20px;">
      <h1>Admin</h1>
      <p>Admin panel implementation in progress (Task 15).</p>
    </div>
  `,
})
export class AdminComponent {}
