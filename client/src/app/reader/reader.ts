import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ReaderStore } from './reader.store';
import { CategorySidebarComponent } from './category-sidebar';
import { FeedToolbarComponent } from './feed-toolbar';
import { FeedListViewComponent } from './feed-list-view';
import { FeedFullViewComponent } from './feed-full-view';

@Component({
  selector: 'app-reader',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
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

  ngOnInit(): void {
    this.store.init();
  }
}
