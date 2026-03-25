import { Component, inject, ChangeDetectionStrategy, AfterViewInit, OnDestroy, ElementRef } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { DatePipe } from '@angular/common';
import { ReaderStore } from '../reader.store';

@Component({
  selector: 'app-feed-list-view',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatExpansionModule,
    MatDividerModule,
    MatButtonModule,
    MatIconModule,
    DatePipe,
  ],
  templateUrl: './feed-list-view.html',
  styleUrl: './feed-list-view.css',
})
export class FeedListViewComponent implements AfterViewInit, OnDestroy {
  readonly store = inject(ReaderStore);
  private readonly el = inject(ElementRef);
  private observer?: IntersectionObserver;

  ngAfterViewInit(): void {
    this.observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (!entry.isIntersecting && entry.boundingClientRect.top < 0) {
          const itemId = (entry.target as HTMLElement).dataset['itemId'];
          const isRead = (entry.target as HTMLElement).dataset['read'];
          if (itemId && isRead !== 'true') this.store.markAsRead(itemId);
        }
      });
    }, { threshold: 0 });

    this.observeArticles();
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  onPanelOpened(itemId: string, isRead: boolean): void {
    if (!isRead) this.store.markAsRead(itemId);
  }

  toggleReadStatus(itemId: string, isRead: boolean, event: Event): void {
    event.stopPropagation();
    if (isRead) {
      this.store.markAsUnread(itemId);
    } else {
      this.store.markAsRead(itemId);
    }
  }

  private observeArticles(): void {
    requestAnimationFrame(() => {
      const articles = this.el.nativeElement.querySelectorAll('.article-panel');
      articles.forEach((el: Element) => this.observer?.observe(el));
    });
  }
}
