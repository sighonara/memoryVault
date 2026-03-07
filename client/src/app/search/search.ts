import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="padding: 20px;">
      <h1>Global Search</h1>
      <p>Search results implementation in progress (Task 16).</p>
    </div>
  `,
})
export class SearchComponent {}
