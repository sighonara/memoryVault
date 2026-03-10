import { Component, inject, OnInit, ChangeDetectionStrategy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SearchDocument, SearchResult } from '../shared/graphql/generated';

const TYPE_ICONS: Record<string, string> = {
  BOOKMARK: 'bookmark',
  FEED_ITEM: 'article',
  VIDEO: 'smart_display',
};

const TYPE_LABELS: Record<string, string> = {
  BOOKMARK: 'Bookmarks',
  FEED_ITEM: 'Feed Items',
  VIDEO: 'Videos',
};

const TYPE_ROUTES: Record<string, string> = {
  BOOKMARK: '/bookmarks',
  FEED_ITEM: '/reader',
  VIDEO: '/youtube',
};

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="search-container">
      <h1 class="search-heading">
        @if (query()) {
          Results for "{{ query() }}"
        } @else {
          Search
        }
      </h1>

      <div class="type-filters">
        <mat-chip-listbox multiple>
          @for (t of allTypes; track t) {
            <mat-chip-option
              [selected]="activeTypes().includes(t)"
              (click)="toggleType(t)">
              <mat-icon matChipAvatar>{{ typeIcon(t) }}</mat-icon>
              {{ typeLabel(t) }}
            </mat-chip-option>
          }
        </mat-chip-listbox>
      </div>

      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      }

      @if (!loading() && results().length === 0 && query()) {
        <div class="empty-state">
          <mat-icon>search_off</mat-icon>
          <p>No results found for "{{ query() }}"</p>
        </div>
      }

      @for (group of groupedResults(); track group.type) {
        <section class="result-group">
          <h2 class="group-heading">
            <mat-icon>{{ typeIcon(group.type) }}</mat-icon>
            {{ typeLabel(group.type) }}
            <span class="group-count">({{ group.items.length }})</span>
          </h2>

          @for (item of group.items; track item.id) {
            <mat-card class="result-card" [routerLink]="typeRoute(item.type)">
              <mat-card-content class="result-content">
                <div class="result-title">{{ item.title || '(untitled)' }}</div>
                @if (item.url) {
                  <div class="result-url">{{ item.url }}</div>
                }
              </mat-card-content>
            </mat-card>
          }
        </section>
      }
    </div>
  `,
  styles: [`
    .search-container { padding: 20px; max-width: 900px; margin: 0 auto; }
    .search-heading { font-size: 1.5rem; font-weight: 500; margin-bottom: 16px; }
    .type-filters { margin-bottom: 24px; }
    .loading { display: flex; justify-content: center; padding: 40px; }
    .empty-state { display: flex; flex-direction: column; align-items: center; padding: 60px; color: #666; }
    .empty-state mat-icon { font-size: 64px; width: 64px; height: 64px; margin-bottom: 16px; opacity: 0.5; }
    .result-group { margin-bottom: 32px; }
    .group-heading { display: flex; align-items: center; gap: 8px; font-size: 1.1rem; font-weight: 500; margin-bottom: 12px; color: #333; }
    .group-count { color: #999; font-weight: 400; }
    .result-card { margin-bottom: 8px; cursor: pointer; transition: transform 0.15s; }
    .result-card:hover { transform: translateX(4px); box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
    .result-content { display: flex; flex-direction: column; gap: 2px; }
    .result-title { font-weight: 500; }
    .result-url { font-size: 0.8rem; color: #888; word-break: break-all; }
  `],
})
export class SearchComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private apollo = inject(Apollo);

  allTypes = ['BOOKMARK', 'FEED_ITEM', 'VIDEO'];
  query = signal('');
  results = signal<SearchResult[]>([]);
  loading = signal(false);
  activeTypes = signal<string[]>([]);

  groupedResults = computed(() => {
    const active = this.activeTypes();
    const filtered = active.length > 0
      ? this.results().filter(r => active.includes(r.type))
      : this.results();

    const groups: Record<string, SearchResult[]> = {};
    for (const item of filtered) {
      (groups[item.type] ??= []).push(item);
    }
    return Object.entries(groups).map(([type, items]) => ({ type, items }));
  });

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      const q = params['q'] || '';
      this.query.set(q);
      if (q) this.search(q);
    });
  }

  toggleType(type: string) {
    const current = this.activeTypes();
    this.activeTypes.set(
      current.includes(type)
        ? current.filter(t => t !== type)
        : [...current, type]
    );
  }

  typeIcon(type: string): string { return TYPE_ICONS[type] ?? 'search'; }
  typeLabel(type: string): string { return TYPE_LABELS[type] ?? type; }
  typeRoute(type: string): string { return TYPE_ROUTES[type] ?? '/'; }

  private search(query: string) {
    this.loading.set(true);
    this.apollo.query({
      query: SearchDocument,
      variables: { query },
      fetchPolicy: 'network-only',
    }).subscribe({
      next: (result: any) => {
        this.results.set(result.data.search);
        this.loading.set(false);
      },
      error: () => {
        this.results.set([]);
        this.loading.set(false);
      },
    });
  }
}
