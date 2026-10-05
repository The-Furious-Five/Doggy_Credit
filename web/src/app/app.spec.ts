import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
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
      providers: [provideRouter([]), { provide: OidcSecurityService, useValue: auth }],
    });
  });
  it('inicia el flujo OIDC sin pedir credenciales en la SPA', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    // La pantalla de acceso no muestra datos del simulador: sin avisos de datos de prueba.
    expect(page.textContent).not.toMatch(/simulad|ficti|demostraci|demo\b/i);
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
  it('al volver de Keycloak con sesión entra directo a la consola', () => {
    auth.checkAuth.mockReturnValue(of({ isAuthenticated: true, userData: { name: 'Demo' } }));
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { url: [{ path: 'callback' }] } } });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    TestBed.createComponent(App).detectChanges();
    expect(navigate).toHaveBeenCalledWith('/app/resumen');
  });
});
