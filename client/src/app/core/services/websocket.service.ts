import { Injectable, inject, OnDestroy } from '@angular/core';
import { RxStomp } from '@stomp/rx-stomp';
import { Observable, Subject, filter, map } from 'rxjs';
import { AuthService } from '../../auth/auth.service';

export interface VaultSignal {
  eventType: string;
  contentType?: string;
  mutationType?: string;
  [key: string]: any;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private authService = inject(AuthService);
  private rxStomp: RxStomp | null = null;
  private destroy$ = new Subject<void>();

  connect(): void {
    if (this.rxStomp) return;

    if (!this.authService.isAuthenticated()) return;
    const token = this.authService.getToken()!;

    this.rxStomp = new RxStomp();
    this.rxStomp.configure({
      brokerURL: this.getWsUrl(),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 5000,
    });
    this.rxStomp.activate();
  }

  disconnect(): void {
    if (this.rxStomp) {
      this.rxStomp.deactivate();
      this.rxStomp = null;
    }
  }

  /**
   * Subscribe to a user-scoped topic (e.g., 'feeds', 'jobs', 'videos', 'ingests', 'sync').
   * Returns an Observable of VaultSignal for that topic.
   */
  on(topicSuffix: string): Observable<VaultSignal> {
    const topic = this.buildTopic(topicSuffix);
    if (!this.rxStomp) {
      return new Observable<VaultSignal>(); // empty if not connected
    }
    return this.rxStomp.watch(topic).pipe(
      map((message) => {
        try {
          return JSON.parse(message.body) as VaultSignal;
        } catch {
          console.warn('Malformed WebSocket signal:', message.body);
          return null;
        }
      }),
      filter((signal): signal is VaultSignal => signal !== null)
    );
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.disconnect();
  }

  private getUserIdFromToken(): string | null {
    const token = this.authService.getToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.userId || null;
    } catch {
      return null;
    }
  }

  private buildTopic(suffix: string): string {
    const userId = this.getUserIdFromToken();
    return `/topic/user/${userId}/${suffix}`;
  }

  private getWsUrl(): string {
    // In dev, the Angular proxy handles /ws → backend
    // In prod, construct from current origin
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws`;
  }
}
