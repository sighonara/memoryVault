import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService, LoginResponse } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('token management', () => {
    it('should return null when no token is stored', () => {
      expect(service.getToken()).toBeNull();
    });

    it('should store and retrieve a token', () => {
      service.setToken('test-token');
      expect(service.getToken()).toBe('test-token');
    });

    it('should clear a stored token', () => {
      service.setToken('test-token');
      service.clearToken();
      expect(service.getToken()).toBeNull();
    });

    it('should clear token on logout', () => {
      service.setToken('test-token');
      service.logout();
      expect(service.getToken()).toBeNull();
    });
  });

  describe('isTokenExpired', () => {
    function makeJwt(payload: object): string {
      const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
      const body = btoa(JSON.stringify(payload));
      return `${header}.${body}.signature`;
    }

    it('should return false for a non-expired token', () => {
      const futureExp = Math.floor(Date.now() / 1000) + 3600;
      const token = makeJwt({ exp: futureExp });
      expect(service.isTokenExpired(token)).toBe(false);
    });

    it('should return true for an expired token', () => {
      const pastExp = Math.floor(Date.now() / 1000) - 3600;
      const token = makeJwt({ exp: pastExp });
      expect(service.isTokenExpired(token)).toBe(true);
    });

    it('should return true for a malformed token', () => {
      expect(service.isTokenExpired('not-a-jwt')).toBe(true);
    });

    it('should return true for an empty string', () => {
      expect(service.isTokenExpired('')).toBe(true);
    });
  });

  describe('isAuthenticated', () => {
    function makeJwt(payload: object): string {
      const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
      const body = btoa(JSON.stringify(payload));
      return `${header}.${body}.signature`;
    }

    it('should return false when no token exists', () => {
      expect(service.isAuthenticated()).toBe(false);
    });

    it('should return true when a valid token exists', () => {
      const futureExp = Math.floor(Date.now() / 1000) + 3600;
      service.setToken(makeJwt({ exp: futureExp }));
      expect(service.isAuthenticated()).toBe(true);
    });

    it('should return false when the token is expired', () => {
      const pastExp = Math.floor(Date.now() / 1000) - 3600;
      service.setToken(makeJwt({ exp: pastExp }));
      expect(service.isAuthenticated()).toBe(false);
    });
  });

  describe('login', () => {
    it('should POST to /api/auth/login and return the response', () => {
      const mockResponse: LoginResponse = {
        token: 'jwt-token',
        email: 'test@example.com',
        displayName: 'Test User',
        role: 'USER',
      };

      let result: LoginResponse | undefined;
      service.login('test@example.com', 'password').subscribe(r => (result = r));

      const req = httpMock.expectOne('/api/auth/login');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'test@example.com', password: 'password' });
      req.flush(mockResponse);

      expect(result).toEqual(mockResponse);
    });
  });
});
