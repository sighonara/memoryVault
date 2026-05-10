import { Component, inject, OnInit, ChangeDetectionStrategy, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ReaderStore } from './reader.store';
import { CategorySidebarComponent } from './category-sidebar';
import { FeedToolbarComponent } from './feed-toolbar';
import { FeedListViewComponent } from './feed-list-view';
import { FeedFullViewComponent } from './feed-full-view';
import { TierService } from '../core/services/tier.service';

@Component({
  selector: 'app-reader',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatSidenavModule,
    MatProgressSpinnerModule,
    CategorySidebarComponent,
    FeedToolbarComponent,
    FeedListViewComponent,
    FeedFullViewComponent,
  ],
  providers: [ReaderStore],
  templateUrl: './reader.html',
  styleUrl: './reader.css'
})
export class ReaderComponent implements OnInit {
  readonly store = inject(ReaderStore);
  readonly tierService = inject(TierService);
  readonly feedCount = computed(() => this.store.categories().reduce((sum: number, cat: any) => sum + (cat.feeds?.length ?? 0), 0));

  ngOnInit(): void {
    this.store.init();
  }
}
