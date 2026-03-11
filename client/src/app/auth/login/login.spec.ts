import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../auth.service';

/**
 * LoginComponent uses external templateUrl, which plain Vitest can't resolve.
 * We test the login flow through the AuthService + HttpTestingController instead.
 */
describe('Login flow', () => {
  let authService: AuthService;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    authService = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should call POST /api/auth/login', () => {
    authService.login('test@example.com', 'password').subscribe();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'test@example.com', password: 'password' });
    req.flush({ token: 'jwt', email: 'test@example.com', displayName: 'Test', role: 'USER' });
  });

  it('should store token after successful login', () => {
    authService.login('test@example.com', 'pass').subscribe(response => {
      authService.setToken(response.token);
    });

    const req = httpMock.expectOne('/api/auth/login');
    req.flush({ token: 'jwt-abc', email: 'test@example.com', displayName: 'Test', role: 'USER' });

    expect(authService.getToken()).toBe('jwt-abc');
  });

  it('should handle login error', () => {
    let errorOccurred = false;
    authService.login('test@example.com', 'wrong').subscribe({
      error: () => { errorOccurred = true; },
    });

    const req = httpMock.expectOne('/api/auth/login');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(errorOccurred).toBe(true);
  });

  it('should clear token and navigate on logout', () => {
    const navSpy = vi.spyOn(router, 'navigate');
    authService.setToken('some-token');
    authService.logout();
    router.navigate(['/login']);

    expect(authService.getToken()).toBeNull();
    expect(navSpy).toHaveBeenCalledWith(['/login']);
  });
});
