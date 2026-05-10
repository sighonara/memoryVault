import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { UserProfile, Tier } from '../models/user-profile.model';

@Injectable({ providedIn: 'root' })
export class TierService {
  private http = inject(HttpClient);

  readonly profile = signal<UserProfile | null>(null);
  readonly tier = signal<Tier>('FREE');

  load(): void {
    this.http.get<UserProfile>('/api/me').subscribe({
      next: (p) => {
        this.profile.set(p);
        this.tier.set(p.tier);
      },
      error: () => {
        this.profile.set(null);
        this.tier.set('FREE');
      }
    });
  }

  isAtLeast(required: Tier): boolean {
    const order: Tier[] = ['FREE', 'PREMIUM', 'ARCHIVE'];
    return order.indexOf(this.tier()) >= order.indexOf(required);
  }

  isOwner(): boolean {
    return this.profile()?.role === 'OWNER';
  }
}
