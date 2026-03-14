import { Routes } from '@angular/router';

export const BOOKMARKS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./bookmarks').then(m => m.BookmarksComponent),
    // Supports ?ingest=<previewId> query param for ingest review flow
  },
];
