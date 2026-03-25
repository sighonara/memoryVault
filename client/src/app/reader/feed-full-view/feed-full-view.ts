import { Component, inject, ChangeDetectionStrategy, AfterViewInit, OnDestroy, ElementRef } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { DatePipe } from '@angular/common';
import { ReaderStore } from '../reader.store';

@Component({
  selector: 'app-feed-full-view',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatCardModule,
    MatDividerModule,
    MatButtonModule,
    MatIconModule,
    DatePipe,
  ],
  templateUrl: './feed-full-view.html',
  styleUrl: './feed-full-view.css',
})
export class FeedFullViewComponent implements AfterViewInit, OnDestroy {
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

  toggleReadStatus(itemId: string, isRead: boolean): void {
    if (isRead) {
      this.store.markAsUnread(itemId);
    } else {
      this.store.markAsRead(itemId);
    }
  }

  private observeArticles(): void {
    requestAnimationFrame(() => {
      const articles = this.el.nativeElement.querySelectorAll('.article-card');
      articles.forEach((el: Element) => this.observer?.observe(el));
    });
  }
}
