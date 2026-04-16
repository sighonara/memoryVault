import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AuthenticationDetails,
  CognitoUser,
  CognitoUserPool,
} from 'amazon-cognito-identity-js';
import { ConfigService } from '../shared/config';
import { LoginResponse } from './auth.service';

@Injectable({ providedIn: 'root' })
export class CognitoAuthService {
  private config = inject(ConfigService);
  private _userPool: CognitoUserPool | null = null;

  private get userPool(): CognitoUserPool {
    if (!this._userPool) {
      this._userPool = new CognitoUserPool({
        UserPoolId: this.config.cognito.userPoolId,
        ClientId: this.config.cognito.clientId,
      });
    }
    return this._userPool;
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return new Observable<LoginResponse>((subscriber) => {
      const cognitoUser = new CognitoUser({
        Username: email,
        Pool: this.userPool,
      });
      const authDetails = new AuthenticationDetails({
        Username: email,
        Password: password,
      });

      cognitoUser.authenticateUser(authDetails, {
        onSuccess: (result) => {
          const idToken = result.getIdToken();
          const payload = idToken.payload;
          subscriber.next({
            token: idToken.getJwtToken(),
            email: payload['email'],
            displayName: payload['email'],
            role: payload['custom:role'] ?? 'VIEWER',
          });
          subscriber.complete();
        },
        onFailure: (err) => subscriber.error(err),
      });
    });
  }

  logout(): void {
    if (!this._userPool) return;
    const user = this._userPool.getCurrentUser();
    if (user) {
      user.signOut();
    }
  }
}
