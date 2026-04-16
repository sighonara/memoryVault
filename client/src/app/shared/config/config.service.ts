import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface CognitoConfig {
  userPoolId: string;
  clientId: string;
  region: string;
}

export interface PublicConfig {
  cognito: CognitoConfig;
}

const EMPTY: PublicConfig = {
  cognito: { userPoolId: '', clientId: '', region: 'us-east-1' },
};

@Injectable({ providedIn: 'root' })
export class ConfigService {
  private http = inject(HttpClient);
  private _config = signal<PublicConfig>(EMPTY);

  async load(): Promise<void> {
    try {
      const cfg = await firstValueFrom(this.http.get<PublicConfig>('/api/config'));
      this._config.set(cfg);
    } catch (err) {
      console.error('Failed to load /api/config, falling back to REST login', err);
      this._config.set(EMPTY);
    }
  }

  get cognito(): CognitoConfig {
    return this._config().cognito;
  }

  get useCognito(): boolean {
    const c = this._config().cognito;
    return !!c.userPoolId && !!c.clientId;
  }
}
