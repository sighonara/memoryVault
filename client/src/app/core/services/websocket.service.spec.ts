import { TestBed } from '@angular/core/testing';
import { WebSocketService } from './websocket.service';
import { AuthService } from '../../auth/auth.service';

describe('WebSocketService', () => {
  let service: WebSocketService;
  let mockAuthService: { getToken: ReturnType<typeof vi.fn>; isAuthenticated: ReturnType<typeof vi.fn> };

  function makeJwt(payload: object): string {
    const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const body = btoa(JSON.stringify(payload));
    return `${header}.${body}.signature`;
  }

  beforeEach(() => {
    mockAuthService = {
      getToken: vi.fn(),
      isAuthenticated: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        WebSocketService,
        { provide: AuthService, useValue: mockAuthService },
      ],
    });
    service = TestBed.inject(WebSocketService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should extract userId from JWT', () => {
    const token = makeJwt({
      exp: Math.floor(Date.now() / 1000) + 3600,
      userId: '00000000-0000-0000-0000-000000000001',
    });
    mockAuthService.getToken.mockReturnValue(token);

    const userId = (service as any).getUserIdFromToken();
    expect(userId).toBe('00000000-0000-0000-0000-000000000001');
  });

  it('should return null userId when no token', () => {
    mockAuthService.getToken.mockReturnValue(null);
    const userId = (service as any).getUserIdFromToken();
    expect(userId).toBeNull();
  });

  it('should build correct topic path', () => {
    const token = makeJwt({
      exp: Math.floor(Date.now() / 1000) + 3600,
      userId: 'test-user-id',
    });
    mockAuthService.getToken.mockReturnValue(token);

    const topic = (service as any).buildTopic('feeds');
    expect(topic).toBe('/topic/user/test-user-id/feeds');
  });
});
