import { Component, inject, OnInit, ChangeDetectionStrategy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Apollo } from 'apollo-angular';
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
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="search-page">
      <div class="search-header">
        @if (query()) {
          <span class="search-label">Results for</span>
          <span class="search-query">"{{ query() }}"</span>
        } @else {
          <span class="search-label">Search</span>
        }
      </div>

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
        <div class="loading"><mat-spinner diameter="28"></mat-spinner></div>
      }

      @if (!loading() && results().length === 0 && query()) {
        <div class="empty-state">
          <mat-icon>search_off</mat-icon>
          <p>No results for "{{ query() }}"</p>
        </div>
      }

      @for (group of groupedResults(); track group.type) {
        <section class="result-group">
          <div class="group-header">
            <mat-icon>{{ typeIcon(group.type) }}</mat-icon>
            <span>{{ typeLabel(group.type) }}</span>
            <span class="group-count">{{ group.items.length }}</span>
          </div>

          @for (item of group.items; track item.id) {
            <a class="result-row" [href]="item.url" target="_blank">
              <span class="result-title">{{ item.title || '(untitled)' }}</span>
              @if (item.url) {
                <span class="result-url">{{ item.url }}</span>
              }
            </a>
          }
        </section>
      }
    </div>
  `,
  styles: [`
    .search-page { max-width: 800px; padding: 0; }
    .search-header {
      padding: 12px 16px; border-bottom: 1px solid #dadce0;
      display: flex; align-items: baseline; gap: 6px;
    }
    .search-label { font-size: 0.8125rem; color: #5f6368; }
    .search-query { font-size: 0.9rem; font-weight: 600; color: #202124; }
    .type-filters { padding: 8px 16px; border-bottom: 1px solid #e8eaed; }
    .type-filters mat-chip-option { font-size: 0.75rem; height: 24px; }
    .loading { display: flex; justify-content: center; padding: 40px; }

    .empty-state {
      display: flex; flex-direction: column; align-items: center;
      padding: 60px; color: #9aa0a6;
    }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 12px; opacity: 0.5; }
    .empty-state p { font-size: 0.8125rem; margin: 0; }

    .result-group { margin-bottom: 0; }
    .group-header {
      display: flex; align-items: center; gap: 6px;
      padding: 8px 16px; background: #f8f9fa; border-bottom: 1px solid #e8eaed;
      font-size: 0.75rem; font-weight: 600; color: #5f6368;
      text-transform: uppercase; letter-spacing: 0.04em;
    }
    .group-header mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .group-count {
      background: #e8eaed; color: #5f6368; padding: 0 6px;
      border-radius: 10px; font-size: 0.65rem; line-height: 16px;
    }

    .result-row {
      display: flex; flex-direction: column; gap: 1px;
      padding: 6px 16px; border-bottom: 1px solid #f1f3f4;
      text-decoration: none; cursor: pointer;
    }
    .result-row:hover { background: #f8f9fa; }
    .result-title {
      font-size: 0.8125rem; font-weight: 500; color: #1a73e8;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .result-url {
      font-size: 0.7rem; color: #80868b;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
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
