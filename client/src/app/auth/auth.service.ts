import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ConfigService } from '../shared/config';
import { CognitoAuthService } from './cognito-auth.service';

export interface LoginResponse {
  token: string;
  email: string;
  displayName: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private cognitoAuth = inject(CognitoAuthService);
  private config = inject(ConfigService);

  private get useCognito(): boolean {
    return this.config.useCognito;
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.useCognito
      ? this.cognitoAuth.login(email, password)
      : this.http.post<LoginResponse>('/api/auth/login', { email, password });
  }

  getToken(): string | null {
    return localStorage.getItem('auth_token');
  }

  setToken(token: string): void {
    localStorage.setItem('auth_token', token);
  }

  clearToken(): void {
    localStorage.removeItem('auth_token');
  }

  logout(): void {
    if (this.useCognito) {
      this.cognitoAuth.logout();
    }
    this.clearToken();
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;
    if (this.isTokenExpired(token)) {
      this.clearToken();
      return false;
    }
    return true;
  }

  isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return Date.now() > payload.exp * 1000;
    } catch {
      return true;
    }
  }
}
