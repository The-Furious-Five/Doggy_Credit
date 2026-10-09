import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { ClavesApiService } from '../api/api/clavesApi.service';
import { WebhooksService } from '../api/api/webhooks.service';
import { ClaveApi, ClaveApiEntornoEnum } from '../api/model/claveApi';
import { ClaveApiCreada, ClaveApiCreadaEntornoEnum } from '../api/model/claveApiCreada';
import { Developers } from './developers';

const key: ClaveApi = {
  id: '11111111-1111-4111-8111-111111111111',
  nombre: 'App móvil',
  clientId: 'dc-test-0a1b2c3d4e5f',
  sufijoSecreto: 'x9Kp',
  entorno: ClaveApiEntornoEnum.Sandbox,
  creadaEn: '2026-10-05T14:30:00Z',
};

describe('Desarrolladores › Claves de API', () => {
  const api = {
    listarClavesApi: vi.fn(),
    crearClaveApi: vi.fn(),
    revocarClaveApi: vi.fn(),
  };

  // jsdom no implementa <dialog>. Se asigna siempre (no ??=) y se restaura al final:
  // los specs comparten el mismo jsdom y otro spec pudo dejar un close() que no
  // dispara el evento 'close', con lo que el secreto nunca se borraba.
  const { showModal, close } = HTMLDialogElement.prototype;

  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  });

  afterAll(() => {
    HTMLDialogElement.prototype.showModal = showModal;
    HTMLDialogElement.prototype.close = close;
  });

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.configureTestingModule({
      imports: [Developers],
      providers: [
        { provide: ClavesApiService, useValue: api },
        { provide: WebhooksService, useValue: { obtenerWebhooks: () => of({ eventos: [], secretoConfigurado: false }) } },
      ],
    });
  });

  function render() {
    const fixture = TestBed.createComponent(Developers);
    fixture.detectChanges();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('muestra la carga mientras llega el listado', () => {
    api.listarClavesApi.mockReturnValue(new Subject());
    const { page } = render();
    expect(page.querySelector('[aria-label="Cargando claves de API"]')).not.toBeNull();
  });

  it('no inventa el consumo de la API: sin barra fija ni límites de otro plan (E-09)', () => {
    api.listarClavesApi.mockReturnValue(of([]));
    const { page } = render();
    expect(page.querySelector('[role="progressbar"]')).toBeNull();
    expect(page.textContent).not.toMatch(/Solicitudes por minuto|60 por minuto|plan Gratis/);
    // El ejemplo desplazable se alcanza con el teclado (WCAG 2.1.1).
    const sample = page.querySelector('pre.code')!;
    expect(sample.getAttribute('tabindex')).toBe('0');
    expect(sample.getAttribute('aria-label')).toBe('Ejemplo con curl: token y evaluación');
  });

  it('muestra las claves con el secreto enmascarado', () => {
    api.listarClavesApi.mockReturnValue(of([key]));
    const { page } = render();
    const row = page.querySelector('tbody tr')!;
    expect(row.textContent).toContain('App móvil');
    expect(row.textContent).toContain('dc-test-0a1b2c3d4e5f');
    expect(row.textContent).toContain('••••x9Kp');
    expect(row.textContent).toContain('Sandbox');
  });

  it('muestra el estado vacío cuando no hay claves', () => {
    api.listarClavesApi.mockReturnValue(of([]));
    const { page } = render();
    expect(page.textContent).toContain('Todavía no hay claves');
    expect(page.querySelector('table')).toBeNull();
  });

  it('muestra el error y permite reintentar', () => {
    api.listarClavesApi.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const { fixture, page } = render();
    expect(page.textContent).toContain('No se pudieron cargar las claves');

    api.listarClavesApi.mockReturnValue(of([key]));
    const retry = [...page.querySelectorAll('button')].find((b) => b.textContent?.includes('Reintentar'))!;
    retry.click();
    fixture.detectChanges();
    expect(page.querySelector('tbody tr')?.textContent).toContain('App móvil');
  });

  it('crea una clave y muestra el secreto una sola vez', () => {
    api.listarClavesApi.mockReturnValue(of([]));
    const created: ClaveApiCreada = {
      ...key,
      entorno: ClaveApiCreadaEntornoEnum.Sandbox,
      secreto: 'secreto-que-se-muestra-una-vez',
      tokenUrl: 'https://localhost/auth/realms/doggycredit/protocol/openid-connect/token',
    };
    api.crearClaveApi.mockReturnValue(of(created));
    const { fixture, page } = render();

    fixture.componentInstance.keyName = '  App móvil ';
    fixture.componentInstance.create();
    fixture.detectChanges();

    expect(api.crearClaveApi).toHaveBeenCalledWith({ nombre: 'App móvil', entorno: 'sandbox' });
    expect(page.textContent).toContain('secreto-que-se-muestra-una-vez');
    expect(page.textContent).toContain('Copia el secreto ahora');

    fixture.componentInstance.secretDialog().close();
    fixture.detectChanges();
    expect(page.textContent).not.toContain('secreto-que-se-muestra-una-vez');
  });

  it('explica que producción requiere el plan Pro', () => {
    api.listarClavesApi.mockReturnValue(of([]));
    api.crearClaveApi.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403, error: { code: 'FEATURE_NOT_IN_PLAN' } })),
    );
    const { fixture, page } = render();
    fixture.componentInstance.keyName = 'Producción';
    fixture.componentInstance.create();
    fixture.detectChanges();
    expect(page.textContent).toContain('requieren el plan Pro');
  });

  it('revoca una clave y recarga el listado', () => {
    api.listarClavesApi.mockReturnValue(of([key]));
    api.revocarClaveApi.mockReturnValue(of(undefined));
    const { fixture, page } = render();

    const revoke = page.querySelector<HTMLButtonElement>('[aria-label="Revocar la clave App móvil"]')!;
    revoke.click();
    fixture.detectChanges();
    api.listarClavesApi.mockReturnValue(of([]));
    fixture.componentInstance.revoke(key);
    fixture.detectChanges();

    expect(api.revocarClaveApi).toHaveBeenCalledWith(key.id);
    expect(page.textContent).toContain('Todavía no hay claves');
  });
});
