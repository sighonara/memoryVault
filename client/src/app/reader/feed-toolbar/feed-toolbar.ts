import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ReaderStore } from '../reader.store';

@Component({
  selector: 'app-feed-toolbar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './feed-toolbar.html',
  styleUrl: './feed-toolbar.css',
})
export class FeedToolbarComponent {
  readonly store = inject(ReaderStore);

  toggleViewMode(): void {
    this.store.setViewMode(this.store.viewMode() === 'LIST' ? 'FULL' : 'LIST');
  }

  toggleSortOrder(): void {
    this.store.setSortOrder(this.store.sortOrder() === 'NEWEST_FIRST' ? 'OLDEST_FIRST' : 'NEWEST_FIRST');
  }
}
