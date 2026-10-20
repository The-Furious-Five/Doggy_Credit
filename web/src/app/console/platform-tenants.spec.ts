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

  async function load(items: object[] | null) {
    const req = http.expectOne(url);
    if (items) req.flush(items);
    else req.flush({}, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    fixture.detectChanges();
  }
  const button = (root: ParentNode, label: string) =>
    [...root.querySelectorAll<HTMLButtonElement>('button')].filter(
      (b) => b.textContent?.trim() === label,
    );

  it('muestra carga y estado vacío', async () => {
    expect(page.textContent).toContain('Cargando instituciones');
    await load([]);
    expect(page.textContent).toContain('No hay instituciones registradas.');
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
