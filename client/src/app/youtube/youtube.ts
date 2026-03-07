import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-youtube',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="padding: 20px;">
      <h1>YouTube</h1>
      <p>YouTube feature implementation in progress (Task 14).</p>
    </div>
  `,
})
export class YoutubeComponent {}
