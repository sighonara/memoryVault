import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatCheckboxModule } from '@angular/forms';
import { ReaderStore } from './reader.store';

@Component({
  selector: 'app-reader',
  standalone: true,
  imports: [
    CommonModule,
    MatSidenavModule,
    MatListModule,
    MatBadgeModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    MatCardModule,
    MatExpansionModule,
    MatProgressSpinnerModule,
    MatToolbarModule,
  ],
  providers: [ReaderStore],
  templateUrl: './reader.html',
  styleUrl: './reader.css'
})
export class ReaderComponent implements OnInit {
  readonly store = inject(ReaderStore);

  ngOnInit(): void {
    this.store.loadFeeds();
  }

  onSelectFeed(feedId: string): void {
    this.store.selectFeed(feedId);
  }

  onMarkRead(itemId: string): void {
    this.store.markAsRead(itemId);
  }

  onToggleUnread(unreadOnly: boolean): void {
    this.store.setUnreadOnly(unreadOnly);
  }

  getSelectedFeedTitle(): string {
    const selectedId = this.store.selectedFeedId();
    if (!selectedId) return 'Select a feed';
    const feedWithStats = this.store.feeds().find(f => f.feed.id === selectedId);
    return feedWithStats?.feed.title || 'Select a feed';
  }
}
