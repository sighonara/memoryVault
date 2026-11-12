import { Component, inject, computed, ChangeDetectionStrategy } from '@angular/core';
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

  // Icon-only buttons need an accessible name; the label describes what clicking does.
  readonly viewModeLabel = computed(() => (this.store.viewMode() === 'LIST' ? 'Full view' : 'List view'));
  readonly sortOrderLabel = computed(() => (this.store.sortOrder() === 'NEWEST_FIRST' ? 'Oldest first' : 'Newest first'));

  toggleViewMode(): void {
    this.store.setViewMode(this.store.viewMode() === 'LIST' ? 'FULL' : 'LIST');
  }

  toggleSortOrder(): void {
    this.store.setSortOrder(this.store.sortOrder() === 'NEWEST_FIRST' ? 'OLDEST_FIRST' : 'NEWEST_FIRST');
  }
}
