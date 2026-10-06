import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';
import { authGuard } from './auth.guard';
import { AuthSessionService } from './session.service';

describe('Protección de la consola', () => {
  const auth = { check: vi.fn() };
  const run = () =>
    firstValueFrom(
      TestBed.runInInjectionContext(() => authGuard({} as never, {} as never)) as Observable<
        boolean | UrlTree
      >,
    );
  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthSessionService, useValue: auth }],
    });
  });
  it('deja pasar con sesión', async () => {
    auth.check.mockReturnValue(of({ isAuthenticated: true }));
    expect(await run()).toBe(true);
  });
  it('envía al acceso sin sesión o si falla la comprobación', async () => {
    const access = TestBed.inject(Router).serializeUrl(TestBed.inject(Router).parseUrl('/acceso'));
    auth.check.mockReturnValue(of({ isAuthenticated: false }));
    expect(TestBed.inject(Router).serializeUrl((await run()) as UrlTree)).toBe(access);
    auth.check.mockReturnValue(throwError(() => new Error('offline')));
    expect(TestBed.inject(Router).serializeUrl((await run()) as UrlTree)).toBe(access);
  });
});
