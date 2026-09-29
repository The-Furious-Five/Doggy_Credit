import { TestBed } from '@angular/core/testing';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { Subject, of, throwError } from 'rxjs';
import { App } from './app';

describe('Acceso institucional', () => {
  const session = new Subject<{ isAuthenticated: boolean }>();
  const auth = {
    isAuthenticated$: session,
    checkAuth: vi.fn(),
    authorize: vi.fn(),
    logoff: vi.fn(),
  };
  beforeEach(() => {
    vi.clearAllMocks();
    auth.checkAuth.mockReturnValue(of({ isAuthenticated: false }));
    auth.logoff.mockReturnValue(of({}));
    TestBed.configureTestingModule({
      imports: [App],
      providers: [{ provide: OidcSecurityService, useValue: auth }],
    });
  });
  it('inicia el flujo OIDC sin pedir credenciales en la SPA', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Datos simulados');
    expect(page.querySelector('input')).toBeNull();
    page.querySelector('button')!.click();
    expect(auth.authorize).toHaveBeenCalledOnce();
  });
  it('muestra la institución de la sesión y permite salir', () => {
    auth.checkAuth.mockReturnValue(
      of({ isAuthenticated: true, userData: { name: 'Demo', tenant_id: 'tenant-demo' } }),
    );
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('tenant-demo');
    fixture.nativeElement.querySelector('button').click();
    expect(auth.logoff).toHaveBeenCalledOnce();
  });
  it('retira el acceso visible cuando expira la sesión', () => {
    auth.checkAuth.mockReturnValue(of({ isAuthenticated: true, userData: { name: 'Demo' } }));
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    session.next({ isAuthenticated: false });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button').textContent).toContain('Iniciar sesión');
  });
  it('permite reintentar cuando falla la comprobación de sesión', () => {
    auth.checkAuth.mockReturnValue(throwError(() => new Error('offline')));
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('button').textContent).toContain('Iniciar sesión');
  });
});
