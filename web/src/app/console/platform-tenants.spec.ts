import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BASE_PATH } from '../api';
import { DsToastService } from '../design-system';
import { PlatformTenants } from './platform-tenants';

describe('Administración de tenants (RF-TEN-04)', () => {
  let fixture: ComponentFixture<PlatformTenants>;
  let http: HttpTestingController;
  let page: HTMLElement;
  const toast = { show: vi.fn() };
  const url = '/api/v1/admin/tenants';
  const metricsUrl = '/api/v1/operador/metricas';
  const metrics = (over: object = {}) => ({
    desde: '2026-10-01',
    hasta: '2026-10-31',
    tenantsPorPlan: [
      { plan: 'FREE', cantidad: 3, evaluaciones: 10 },
      { plan: 'PRO', cantidad: 2, evaluaciones: 1250 },
      { plan: 'ENTERPRISE', cantidad: 1, evaluaciones: 0 },
    ],
    tenantsActivos: 5,
    tenantsSuspendidos: 1,
    tenantsActivados: 4,
    conversionesFreeAPro: 2,
    evaluaciones: 1260,
    ...over,
  });
  const tenant = (over: object) => ({
    id: 't1',
    nombre: 'Alfa',
    nit: '1',
    tipo: 'IMF',
    plan: 'FREE',
    pais: 'BO',
    moneda: 'BOB',
    activo: true,
    evaluacionesMes: 3,
    limiteMensual: 10,
    ...over,
  });
  const { showModal, close } = HTMLDialogElement.prototype;

  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function () {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function () {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  });
  afterAll(() => {
    HTMLDialogElement.prototype.showModal = showModal;
    HTMLDialogElement.prototype.close = close;
  });
  beforeEach(() => {
    toast.show.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: '/api/v1' },
        { provide: DsToastService, useValue: toast },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PlatformTenants);
    page = fixture.nativeElement;
    fixture.detectChanges();
  });
  afterEach(() => http.verify());

  /** La lista y las métricas se piden juntas al abrir la vista; las dos se responden antes de esperar. */
  async function load(items: object[] | null, operatorMetrics: object | null = metrics()) {
    const req = http.expectOne(url);
    if (items) req.flush(items);
    else req.flush({}, { status: 403, statusText: 'Forbidden' });
    answerMetrics(operatorMetrics);
    await fixture.whenStable();
    fixture.detectChanges();
  }
  function answerMetrics(body: object | null) {
    TestBed.tick();
    const req = http.expectOne(metricsUrl);
    expect(req.request.params.keys()).toEqual([]);
    if (body) req.flush(body);
    else req.flush({}, { status: 500, statusText: 'Server Error' });
  }
  /** `Etiqueta · valor · detalle` de cada ds-stat. */
  const stats = () =>
    [...page.querySelectorAll('ds-stat')].map((stat) =>
      [...stat.querySelectorAll('p')].map((p) => p.textContent?.trim()).join(' | '),
    );
  const button = (root: ParentNode, label: string) =>
    [...root.querySelectorAll<HTMLButtonElement>('button')].filter(
      (b) => b.textContent?.trim() === label,
    );

  it('muestra carga y estado vacío', async () => {
    expect(page.textContent).toContain('Cargando instituciones');
    await load([]);
    expect(page.textContent).toContain('No hay instituciones registradas.');
  });

  describe('métricas de la plataforma (D-14 / RF-ANA-04)', () => {
    it('muestra la carga y después los cuatro indicadores del mes', async () => {
      TestBed.tick();
      fixture.detectChanges();
      expect(page.textContent).toContain('Cargando métricas');
      await load([tenant({})]);
      expect(page.textContent).toContain('1/10/2026 – 31/10/2026 (UTC)');
      expect(stats()).toEqual([
        'Tenants | 6 | Gratis 3 · Pro 2 · Enterprise 1 · 1 suspendido',
        'Activados | 4 | Con al menos una evaluación en el período',
        'Conversiones | 2 | Subidas desde Gratis a Pro o Enterprise',
        'Evaluaciones | 1.260 | Gratis 10 · Pro 1.250 · Enterprise 0',
      ]);
      expect(page.textContent).not.toContain('Sin actividad en este período');
    });

    it('muestra el estado vacío cuando nadie evaluó ni subió de plan', async () => {
      await load(
        [tenant({})],
        metrics({ tenantsActivados: 0, conversionesFreeAPro: 0, evaluaciones: 0, tenantsSuspendidos: 0 }),
      );
      expect(page.textContent).toContain('Sin actividad en este período');
      expect(stats()[0]).toContain('0 suspendidos');
    });

    it('muestra el error sin ocultar la tabla y reintenta', async () => {
      await load([tenant({})], null);
      expect(page.textContent).toContain('No se pudieron cargar las métricas');
      expect(page.querySelectorAll('tbody tr')).toHaveLength(1);
      expect(page.querySelectorAll('ds-stat')).toHaveLength(0);

      button(page, 'Reintentar')[0].click();
      answerMetrics(metrics());
      await fixture.whenStable();
      fixture.detectChanges();
      expect(page.textContent).not.toContain('No se pudieron cargar las métricas');
      expect(stats()).toHaveLength(4);
    });

    it('tolera un servidor que no envía el consumo por plan', async () => {
      await load(
        [tenant({})],
        metrics({ tenantsPorPlan: [{ plan: 'FREE', cantidad: 3 }, { plan: 'PRO', cantidad: 2 }, { plan: 'ENTERPRISE', cantidad: 1 }] }),
      );
      expect(stats()[3]).toBe('Evaluaciones | 1.260 | En el período');
    });
  });

  it('muestra el error de carga', async () => {
    await load(null);
    expect(page.textContent).toContain('No se pudo cargar la lista');
  });

  it('muestra el consumo del mes con y sin límite', async () => {
    await load([
      tenant({}),
      tenant({ id: 't2', nombre: 'Beta', evaluacionesMes: 7, limiteMensual: 0 }),
    ]);
    const cells = [...page.querySelectorAll('tbody tr')].map((r) =>
      r.querySelectorAll('td')[2].textContent?.trim(),
    );
    expect(cells).toEqual(['3 de 10', '7 · sin límite']);
  });

  it('no suspende sin confirmar y suspende al confirmar', async () => {
    await load([tenant({})]);
    button(page.querySelector('table')!, 'Suspender institución')[0].click();
    fixture.detectChanges();
    const dialog = page.querySelector('dialog')!;
    expect(dialog.open).toBe(true);
    expect(dialog.textContent).toContain('dejarán de operar');
    expect(dialog.textContent).toContain('No se borra ningún dato');
    http.expectNone(`${url}/t1`);

    button(dialog, 'Cancelar')[0].click();
    http.expectNone(`${url}/t1`);

    button(page.querySelector('table')!, 'Suspender institución')[0].click();
    fixture.detectChanges();
    button(dialog, 'Suspender institución')[0].click();
    const req = http.expectOne(`${url}/t1`);
    expect(req.request.body).toEqual({ activo: false });
    req.flush(tenant({ activo: false }));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(page.textContent).toContain('Suspendido');
    expect(button(page.querySelector('table')!, 'Reactivar institución')).toHaveLength(1);
  });

  it('reactiva sin diálogo', async () => {
    await load([tenant({ activo: false })]);
    button(page, 'Reactivar institución')[0].click();
    const req = http.expectOne(`${url}/t1`);
    expect(req.request.body).toEqual({ activo: true });
    req.flush(tenant({}));
    await fixture.whenStable();
    expect(toast.show).toHaveBeenCalledWith('Institución reactivada.');
  });
});
