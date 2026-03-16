import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { AppLayoutComponent } from './shared/layout/app-layout';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./auth/login/login').then(m => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    component: AppLayoutComponent,
    children: [
      { path: '', redirectTo: 'reader', pathMatch: 'full' },
      {
        path: 'reader',
        loadComponent: () => import('./reader/reader').then(m => m.ReaderComponent)
      },
      {
        path: 'bookmarks',
        loadChildren: () => import('./bookmarks/bookmarks.routes').then(m => m.BOOKMARKS_ROUTES)
      },
      {
        path: 'youtube',
        loadComponent: () => import('./youtube/youtube').then(m => m.YoutubeComponent)
      },
      {
        path: 'admin',
        loadComponent: () => import('./admin/admin').then(m => m.AdminComponent)
      },
      {
        path: 'search',
        loadComponent: () => import('./search/search').then(m => m.SearchComponent)
      },
    ],
  },
];
