import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// Purge any expired JWT before Angular bootstraps, so no code path can
// attach a stale token to an outgoing request.
(function purgeExpiredToken() {
  const token = localStorage.getItem('auth_token');
  if (!token) return;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    if (Date.now() > payload.exp * 1000) {
      localStorage.removeItem('auth_token');
    }
  } catch {
    localStorage.removeItem('auth_token');
  }
})();

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
