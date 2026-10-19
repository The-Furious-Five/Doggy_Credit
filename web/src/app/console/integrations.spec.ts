import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BASE_PATH, FuenteIntegracion, Integracion, IntegracionEstadoEnum } from '../api';
import { DsToastService } from '../design-system';
import { AuthSessionService } from '../auth/session.service';
import { signal } from '@angular/core';
import { Integrations } from './integrations';

const catalog = (): Integracion[] => [
  { fuente: FuenteIntegracion.Buro, estado: IntegracionEstadoEnum.Conectado },
  { fuente: FuenteIntegracion.Banco, estado: IntegracionEstadoEnum.Disponible },
  { fuente: FuenteIntegracion.Wallet, estado: IntegracionEstadoEnum.RequierePro },
  { fuente: FuenteIntegracion.OpenBanking, estado: IntegracionEstadoEnum.Proximamente },
  { fuente: FuenteIntegracion.Facturacion, estado: IntegracionEstadoEnum.Proximamente },
  { fuente: FuenteIntegracion.Garantias, estado: IntegracionEstadoEnum.Proximamente },
];

describe('Integraciones conectadas al contrato B-06 (E-03)', () => {
  let requests: HttpTestingController;
  let fixture: ComponentFixture<Integrations>;
  let page: HTMLElement;
  const toast = { show: vi.fn() };
  const url = '/api/v1/integraciones';
  const roles = signal<string[]>(['TENANT_ADMIN']);

  beforeEach(() => {
    toast.show.mockReset();
    roles.set(['TENANT_ADMIN']);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthSessionService, useValue: { session: () => ({ roles: roles() }) } },
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: '/api/v1' },
        { provide: DsToastService, useValue: toast },
      ],
    });
    requests = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Integrations);
    page = fixture.nativeElement;
    update();
  });

  afterEach(() => requests.verify());

  function update(): void {
    fixture.detectChanges();
    TestBed.tick();
    fixture.detectChanges();
  }

  function load(data = catalog()): void {
    const request = requests.expectOne(url);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.headers.has('X-Tenant-Id')).toBe(false);
    request.flush(data);
    update();
  }

  function card(name: string): HTMLElement {
    return [...page.querySelectorAll<HTMLElement>('ds-card')].find((item) =>
      item.querySelector('h2')?.textContent?.includes(name),
    )!;
  }

  function action(name: string): HTMLButtonElement {
    return card(name).querySelector<HTMLButtonElement>('button')!;
  }

  function retry(label: string): void {
    [...page.querySelectorAll<HTMLButtonElement>('button')]
      .find((button) => button.textContent?.includes(label))!
      .click();
    update();
  }

  it('muestra carga accesible sin inventar estados ni un aviso de plan', () => {
    expect(page.querySelector('ds-spinner')?.getAttribute('role')).toBe('status');
    expect(page.textContent).toContain('Cargando integraciones');
    expect(page.querySelectorAll('ds-card')).toHaveLength(0);
    expect(page.textContent).not.toContain('Tu plan Gratis incluye 1 integración');
    load();
  });

  it('usa el cliente generado y presenta los seis conectores con los cuatro estados del servidor', () => {
    load();
    expect(page.querySelectorAll('ds-card')).toHaveLength(6);
    expect(card('Buró de crédito nacional').querySelector('ds-badge')?.textContent).toContain(
      'Conectado',
    );
    expect(card('Core bancario').querySelector('ds-badge')?.textContent).toContain('Disponible');
    expect(card('Billetera móvil').querySelector('ds-badge')?.textContent).toContain('Plan Pro');
    expect(card('Agregador open banking').querySelector('ds-badge')?.textContent).toContain(
      'Próximamente',
    );
    expect(page.textContent).not.toContain('Tu plan Gratis incluye 1 integración');
  });

  it('muestra únicamente las fuentes devueltas por la API', () => {
    load([catalog()[1]]);
    expect(page.querySelectorAll('ds-card')).toHaveLength(1);
    expect(page.textContent).toContain('Core bancario');
    expect(page.textContent).not.toContain('Buró de crédito nacional');
  });

  it('permite filtrar por categoría con el teclado sin volver a consultar la API', () => {
    load();
    const selector = page.querySelector<HTMLElement>('[role="combobox"]')!;
    expect(selector.getAttribute('aria-labelledby')).toBeTruthy();
    for (const key of ['ArrowDown', 'Home', 'ArrowDown', 'ArrowDown', 'Enter']) {
      selector.dispatchEvent(
        new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }),
      );
      update();
    }
    expect(page.querySelectorAll('ds-card')).toHaveLength(1);
    expect(page.textContent).toContain('Core bancario');
    expect(page.textContent).not.toContain('Billetera móvil');
    requests.expectNone(url);
  });

  it('explica el vacío de la categoría y permite volver a todas', () => {
    load([catalog()[1]]);
    fixture.componentInstance.category.set('Wallet');
    update();
    expect(page.textContent).toContain('Sin integraciones en esta categoría');
    retry('Ver todas');
    expect(page.querySelectorAll('ds-card')).toHaveLength(1);
    expect(page.textContent).toContain('Core bancario');
  });

  it('muestra el estado vacío del catálogo', () => {
    load([]);
    expect(page.textContent).toContain('Sin integraciones');
    expect(page.querySelectorAll('ds-card')).toHaveLength(0);
  });

  it('permite reintentar un error de carga conservando el filtro', () => {
    fixture.componentInstance.category.set('Banco');
    requests.expectOne(url).flush({}, { status: 503, statusText: 'Service Unavailable' });
    update();
    expect(page.textContent).toContain('No pudimos cargar las integraciones');
    retry('Reintentar');
    expect(page.textContent).toContain('Cargando integraciones');
    load();
    expect(page.querySelectorAll('ds-card')).toHaveLength(1);
    expect(page.textContent).toContain('Core bancario');
  });

  it('bloquea la desactivación del buró y las fuentes sin implementación', () => {
    load();
    expect(action('Buró de crédito nacional').disabled).toBe(true);
    expect(action('Buró de crédito nacional').textContent).toContain('Obligatorio');
    expect(card('Buró de crédito nacional').textContent).toContain('permanece conectada');
    for (const name of [
      'Agregador open banking',
      'Facturación electrónica',
      'Registro de garantías',
    ]) {
      expect(action(name).disabled).toBe(true);
      action(name).click();
    }
    fixture.componentInstance.act(catalog()[0]);
    fixture.componentInstance.act(catalog()[3]);
    requests.expectNone((request) => request.method === 'PUT');
  });

  it('activa con PUT, evita envíos duplicados y refresca todos los estados sin ocultar el catálogo', () => {
    load();
    const button = action('Core bancario');
    expect(button.getAttribute('aria-label')).toBe('Conectar: Core bancario');
    button.click();
    update();
    expect(button.disabled).toBe(true);
    expect(button.getAttribute('aria-busy')).toBe('true');
    expect(action('Billetera móvil').disabled).toBe(true);
    button.click();
    fixture.componentInstance.act(catalog()[1]);
    fixture.componentInstance.act(catalog()[2]);
    const request = requests.expectOne(`${url}/BANCO`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ activa: true });
    const data = catalog();
    data[1].estado = IntegracionEstadoEnum.Conectado;
    request.flush(data[1]);
    update();
    expect(page.querySelectorAll('ds-card')).toHaveLength(6);
    expect(page.querySelector('ds-spinner')).toBeNull();
    expect(action('Core bancario').disabled).toBe(true);
    data[2].estado = IntegracionEstadoEnum.Disponible;
    load(data);
    expect(card('Core bancario').querySelector('ds-badge')?.textContent).toContain('Conectado');
    expect(card('Billetera móvil').querySelector('ds-badge')?.textContent).toContain('Disponible');
    expect(action('Core bancario').textContent).toContain('Desconectar');
    expect(action('Core bancario').disabled).toBe(false);
    expect(toast.show).toHaveBeenCalledWith('Core bancario: Conectado.');
  });

  it('desactiva con PUT y presenta el estado resultante del servidor', () => {
    const data = catalog();
    data[1].estado = IntegracionEstadoEnum.Conectado;
    load(data);
    action('Core bancario').click();
    const request = requests.expectOne(`${url}/BANCO`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ activa: false });
    data[1].estado = IntegracionEstadoEnum.Disponible;
    request.flush(data[1]);
    load(data);
    expect(action('Core bancario').textContent).toContain('Conectar');
    expect(toast.show).toHaveBeenCalledWith('Core bancario: Disponible.');
  });

  it('muestra el aviso Gratis solo después de PLAN_LIMIT_REACHED y conserva los estados', () => {
    const data = catalog();
    data[1].estado = IntegracionEstadoEnum.RequierePro;
    load(data);
    expect(page.textContent).not.toContain('Tu plan Gratis incluye 1 integración');
    action('Core bancario').click();
    requests
      .expectOne(`${url}/BANCO`)
      .flush({ code: 'PLAN_LIMIT_REACHED' }, { status: 409, statusText: 'Conflict' });
    update();
    expect(page.textContent).toContain('Tu plan Gratis incluye 1 integración');
    expect(page.textContent).toContain('El buró obligatorio ya ocupa ese lugar');
    expect(page.querySelector('ds-alert')?.getAttribute('role')).toBe('alert');
    expect(card('Core bancario').querySelector('ds-badge')?.textContent).toContain('Plan Pro');
    expect(action('Core bancario').disabled).toBe(false);
    expect(toast.show).not.toHaveBeenCalled();
    requests.expectNone(url);
  });

  it('conserva el estado tras otros errores y permite reintentar el cambio', () => {
    load();
    action('Core bancario').click();
    requests
      .expectOne(`${url}/BANCO`)
      .flush({ code: 'FEATURE_NOT_IN_PLAN' }, { status: 403, statusText: 'Forbidden' });
    update();
    expect(page.textContent).toContain('No pudimos guardar el cambio');
    expect(page.textContent).not.toContain('Tu plan Gratis incluye 1 integración');
    expect(card('Core bancario').querySelector('ds-badge')?.textContent).toContain('Disponible');
    action('Core bancario').click();
    const request = requests.expectOne(`${url}/BANCO`);
    expect(request.request.body).toEqual({ activa: true });
    const data = catalog();
    data[1].estado = IntegracionEstadoEnum.Conectado;
    request.flush(data[1]);
    load(data);
    expect(page.textContent).not.toContain('No pudimos guardar el cambio');
  });

  it('conserva el cambio guardado si falla el refresco y exige actualizar antes de otro cambio', () => {
    load();
    action('Core bancario').click();
    const data = catalog();
    data[1].estado = IntegracionEstadoEnum.Conectado;
    requests.expectOne(`${url}/BANCO`).flush(data[1]);
    requests.expectOne(url).flush({}, { status: 503, statusText: 'Service Unavailable' });
    update();
    expect(page.textContent).toContain(
      'El cambio se guardó, pero no pudimos actualizar el catálogo',
    );
    expect(card('Core bancario').querySelector('ds-badge')?.textContent).toContain('Conectado');
    expect(action('Core bancario').disabled).toBe(true);
    fixture.componentInstance.act(data[1]);
    requests.expectNone(`${url}/BANCO`);
    retry('Actualizar estados');
    load(data);
    expect(page.textContent).not.toContain('no pudimos actualizar el catálogo');
    expect(action('Core bancario').disabled).toBe(false);
  });

  it('ofrece credenciales solo en los tres conectores implementados y para administradores', () => {
    load();
    expect(page.querySelectorAll('dc-integration-credentials')).toHaveLength(3);
    roles.set(['ANALYST']);
    update();
    expect(page.querySelectorAll('dc-integration-credentials')).toHaveLength(0);
    roles.set(['PLATFORM_ADMIN']);
    update();
    expect(page.querySelectorAll('dc-integration-credentials')).toHaveLength(0);
    requests.expectNone((request) => request.url.endsWith('/credenciales'));
  });

  it('cancela las peticiones cuando se abandona la vista', () => {
    const request = requests.expectOne(url);
    fixture.destroy();
    expect(request.cancelled).toBe(true);
  });
});
