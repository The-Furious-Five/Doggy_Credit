import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  CasoRevision,
  CasoRevisionBandaEnum,
  CasoRevisionMotivoEnum,
  PaginaCasosRevision,
  Producto,
  ProductoBandaMinimaEnum,
  ProductoMonedaEnum,
  ProductoTipoEnum,
} from '../api';
import { BASE_PATH } from '../api/variables';
import { DsToastService } from '../design-system';
import { HANDLED_PROBLEMS } from '../http/api-error.interceptor';
import { ManualReview } from './manual-review';
import { SensitiveDataService } from './sensitive-data.service';

const LIST = '/api/v1/revision-manual';
const CASE_ID = '11111111-1111-4111-8111-111111111111';
const resolution = (id = CASE_ID) => `/api/v1/revision-manual/${id}/resolucion`;

const caso = (overrides: Partial<CasoRevision> = {}): CasoRevision => ({
  evaluacionId: CASE_ID,
  solicitanteId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  documento: 'CI-DEMO-0001001',
  creadoEn: '2026-10-08T15:00:00Z',
  score: 590,
  banda: CasoRevisionBandaEnum.C,
  motivo: CasoRevisionMotivoEnum.RevisionManual,
  resultadoParcial: false,
  razones: ['MORA_RECIENTE', 'ENDEUDAMIENTO_ALTO', 'HISTORIAL_CORTO', 'PLAZO_LARGO'],
  montoSolicitado: '15000.00',
  plazoMeses: 24,
  proposito: 'Capital de trabajo',
  ...overrides,
});
const micro: Producto = {
  id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
  tipo: ProductoTipoEnum.Microcredito,
  moneda: ProductoMonedaEnum.Bob,
  montoMinimo: '1000.00',
  montoMaximo: '20000.00',
  plazoMinimoMeses: 3,
  plazoMaximoMeses: 24,
  tasaAnual: '11.50',
  bandaMinima: ProductoBandaMinimaEnum.C,
  activo: true,
};
const page = (
  items: CasoRevision[],
  productos: Producto[] = [micro],
  total = items.length,
): PaginaCasosRevision => ({
  items,
  total,
  productos,
});

describe('Bandeja de revisión manual (E-10, RF-DEC-03)', () => {
  let http: HttpTestingController;
  const { showModal, close } = HTMLDialogElement.prototype;

  // jsdom no implementa el diálogo modal nativo; se restaura al final (los specs comparten jsdom).
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
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: '/api/v1' },
        {
          provide: SensitiveDataService,
          useValue: { document: (v: string) => `••••${v.slice(-4)}`, amount: (v: string) => v },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  async function render(): Promise<{ f: ComponentFixture<ManualReview>; load: TestRequest }> {
    const f = TestBed.createComponent(ManualReview);
    await settle(f);
    const load = http.expectOne((r) => r.url === LIST);
    return { f, load };
  }
  async function loaded(data = page([caso()])): Promise<ComponentFixture<ManualReview>> {
    const { f, load } = await render();
    load.flush(data);
    await settle(f);
    return f;
  }
  /**
   * rxResource y ngModel resuelven de forma asíncrona. No se espera whenStable: con una
   * petición abierta (la carga) la aplicación nunca queda estable.
   */
  async function settle(f: ComponentFixture<ManualReview>): Promise<void> {
    f.detectChanges();
    TestBed.tick();
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    f.detectChanges();
  }
  const el = (f: ComponentFixture<ManualReview>) => f.nativeElement as HTMLElement;
  const text = (f: ComponentFixture<ManualReview>) => el(f).textContent ?? '';
  const dialog = (f: ComponentFixture<ManualReview>) =>
    el(f).querySelector('dialog') as HTMLDialogElement;
  const button = (f: ComponentFixture<ManualReview>, label: string) =>
    [...el(f).querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === label,
    ) as HTMLButtonElement;

  async function openFirst(f: ComponentFixture<ManualReview>): Promise<void> {
    (el(f).querySelector('button[aria-label^="Resolver caso de"]') as HTMLButtonElement).click();
    await settle(f);
  }

  it('consulta la bandeja paginada del contrato y muestra la carga sin datos fijos', async () => {
    const { f, load } = await render();
    expect(load.request.method).toBe('GET');
    expect(load.request.params.get('pagina')).toBe('1');
    expect(load.request.params.get('tamano')).toBe('10');
    // La bandeja explica el 403 por rol: el interceptor no lo repite en un toast.
    expect(load.request.context.get(HANDLED_PROBLEMS)).toEqual(['INSUFFICIENT_ROLE']);
    expect(text(f)).toContain('Cargando casos…');
    expect(el(f).querySelector('table')).toBeNull();
    load.flush(page([]));
  });

  it('lista los casos con cliente enmascarado, score, motivo, monto y plazo', async () => {
    const f = await loaded(
      page([
        caso(),
        caso({
          evaluacionId: '22222222-2222-4222-8222-222222222222',
          motivo: CasoRevisionMotivoEnum.SolicitarDatos,
          resultadoParcial: true,
          documento: 'NIT-9876543',
          montoSolicitado: undefined,
          plazoMeses: undefined,
        }),
      ]),
    );
    const rows = el(f).querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('••••1001');
    expect(rows[0].textContent).not.toContain('CI-DEMO-0001001');
    expect(rows[0].textContent).toContain('590 · C');
    expect(rows[0].textContent).toContain('Revisión manual');
    expect(rows[0].textContent).toContain('15.000');
    expect(rows[0].textContent).toContain('24 m');
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe(
      '/app/clientes/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    );
    expect(rows[1].textContent).toContain('Solicitar datos');
    expect(rows[1].textContent).toContain('Resultado parcial');
    expect(rows[1].textContent).toContain('—');
    expect(rows[1].querySelector('button')?.getAttribute('aria-label')).toBe(
      'Resolver caso de ••••6543',
    );
  });

  it('sin casos muestra el estado vacío', async () => {
    const f = await loaded(page([]));
    expect(text(f)).toContain('Sin casos pendientes');
    expect(el(f).querySelector('table')).toBeNull();
  });

  it('un error permite reintentar', async () => {
    const { f, load } = await render();
    load.flush({ code: 'INTERNAL' }, { status: 500, statusText: 'Server Error' });
    await settle(f);
    expect(text(f)).toContain('No pudimos cargar la bandeja');
    button(f, 'Reintentar').click();
    await settle(f);
    http.expectOne((r) => r.url === LIST).flush(page([caso()]));
    await settle(f);
    expect(el(f).querySelectorAll('tbody tr')).toHaveLength(1);
  });

  it('un rol sin permiso (403) ve por qué, no un error genérico', async () => {
    const { f, load } = await render();
    load.flush({ code: 'INSUFFICIENT_ROLE' }, { status: 403, statusText: 'Forbidden' });
    await settle(f);
    expect(text(f)).toContain('Tu rol no resuelve casos en revisión manual');
    expect(text(f)).not.toContain('Reintentar');
  });

  it('el diálogo muestra el caso y nada preseleccionado', async () => {
    const f = await loaded();
    await openFirst(f);
    expect(dialog(f).open).toBe(true);
    expect(dialog(f).textContent).toContain('590 · Banda C');
    expect(dialog(f).textContent).toContain('Capital de trabajo');
    // Solo las 3 razones de mayor impacto.
    expect(dialog(f).querySelectorAll('li.ds-mono')).toHaveLength(3);
    expect(
      [...dialog(f).querySelectorAll<HTMLInputElement>('input[type="radio"]')].some(
        (r) => r.checked,
      ),
    ).toBe(false);
  });

  it('no se puede enviar sin justificación', async () => {
    const f = await loaded();
    await openFirst(f);
    const component = f.componentInstance;
    component.decision = component.approve;
    component.productId = micro.id;
    component.justification = '   ';
    button(f, 'Resolver caso').click();
    await settle(f);
    http.expectNone(resolution());
    expect(dialog(f).textContent).toContain('La justificación es obligatoria.');
    expect(document.activeElement?.id).toBe('revision-justificacion');
    expect(document.getElementById('revision-justificacion')?.getAttribute('aria-invalid')).toBe(
      'true',
    );
  });

  it('tampoco con una justificación de menos de 10 caracteres', async () => {
    const f = await loaded();
    await openFirst(f);
    f.componentInstance.decision = f.componentInstance.reject;
    f.componentInstance.justification = 'Muy poco';
    button(f, 'Resolver caso').click();
    await settle(f);
    http.expectNone(resolution());
    expect(dialog(f).textContent).toContain('Escribe al menos 10 caracteres (llevas 8).');
  });

  it('exige elegir aprobar o rechazar, y el producto al aprobar', async () => {
    const f = await loaded();
    await openFirst(f);
    f.componentInstance.justification = 'Ingresos estables verificados en el banco.';
    button(f, 'Resolver caso').click();
    await settle(f);
    http.expectNone(resolution());
    expect(dialog(f).textContent).toContain('Elige si apruebas o rechazas el caso');
    expect((document.activeElement as HTMLInputElement | null)?.name).toBe('decision');

    f.componentInstance.decision = f.componentInstance.approve;
    button(f, 'Resolver caso').click();
    await settle(f);
    http.expectNone(resolution());
    expect(dialog(f).textContent).toContain('Elige el producto que se otorga.');
  });

  it('aprobar envía decisión, producto y justificación; avisa, cierra y recarga la bandeja', async () => {
    const f = await loaded();
    await openFirst(f);
    // Interacción real: el radio y el textarea alimentan el formulario.
    (dialog(f).querySelector('input[type="radio"]') as HTMLInputElement).click();
    const textarea = document.getElementById('revision-justificacion') as HTMLTextAreaElement;
    textarea.value = '  Ingresos estables verificados en el banco.  ';
    textarea.dispatchEvent(new Event('input'));
    f.componentInstance.productId = micro.id;
    await settle(f);
    expect(dialog(f).textContent).toContain('Producto que se otorga');

    button(f, 'Resolver caso').click();
    await settle(f);
    const post = http.expectOne(resolution());
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({
      decision: 'APROBADO',
      productoId: micro.id,
      justificacion: 'Ingresos estables verificados en el banco.',
    });
    expect(post.request.context.get(HANDLED_PROBLEMS)).toEqual(
      expect.arrayContaining(['VALIDATION_ERROR', 'CONCURRENT_UPDATE', 'PRODUCT_NOT_FOUND']),
    );
    post.flush({
      evaluacionId: CASE_ID,
      decision: 'APROBADO',
      productoId: micro.id,
      justificacion: 'x',
      resueltoPor: 'ana',
      resueltoEn: '2026-10-09T10:00:00Z',
    });
    await settle(f);
    expect(TestBed.inject(DsToastService).message()).toBe('Caso aprobado con Microcrédito.');
    expect(dialog(f).open).toBe(false);
    http.expectOne((r) => r.url === LIST).flush(page([]));
    await settle(f);
    expect(text(f)).toContain('Sin casos pendientes');
    // La fila con el botón que abrió el diálogo ya no existe: el foco va al título.
    expect(document.activeElement?.textContent?.trim()).toBe('Revisión manual');
  });

  it('rechazar no pide producto ni lo envía', async () => {
    const f = await loaded();
    await openFirst(f);
    f.componentInstance.decision = f.componentInstance.reject;
    f.componentInstance.justification = 'Mora reciente sin explicación del cliente.';
    await settle(f);
    expect(dialog(f).textContent).not.toContain('Producto que se otorga');
    button(f, 'Resolver caso').click();
    await settle(f);
    const post = http.expectOne(resolution());
    expect(post.request.body).toEqual({
      decision: 'RECHAZADO',
      justificacion: 'Mora reciente sin explicación del cliente.',
    });
    post.flush({
      evaluacionId: CASE_ID,
      decision: 'RECHAZADO',
      justificacion: 'x',
      resueltoPor: 'ana',
      resueltoEn: '2026-10-09T10:00:00Z',
    });
    await settle(f);
    expect(TestBed.inject(DsToastService).message()).toBe('Caso rechazado.');
    http.expectOne((r) => r.url === LIST).flush(page([]));
  });

  async function resolveWith(
    f: ComponentFixture<ManualReview>,
    status: number,
    code: string,
  ): Promise<void> {
    await openFirst(f);
    f.componentInstance.decision = f.componentInstance.approve;
    f.componentInstance.productId = micro.id;
    f.componentInstance.justification = 'Ingresos estables verificados en el banco.';
    button(f, 'Resolver caso').click();
    await settle(f);
    http.expectOne(resolution()).flush({ code }, { status, statusText: 'Error' });
    await settle(f);
  }

  it('si otra persona ya lo resolvió (409), lo explica y vuelve a la bandeja actualizada', async () => {
    const f = await loaded();
    await resolveWith(f, 409, 'CONCURRENT_UPDATE');
    expect(dialog(f).textContent).toContain('Este caso ya no está pendiente');
    expect(button(f, 'Resolver caso')).toBeUndefined();
    button(f, 'Volver a la bandeja').click();
    await settle(f);
    expect(dialog(f).open).toBe(false);
    http.expectOne((r) => r.url === LIST).flush(page([]));
  });

  it('si el producto se desactivó (404 PRODUCT_NOT_FOUND), pide otro y actualiza la lista', async () => {
    const f = await loaded();
    await resolveWith(f, 404, 'PRODUCT_NOT_FOUND');
    expect(dialog(f).textContent).toContain('Ese producto ya no está disponible');
    expect(f.componentInstance.productId).toBeNull();
    http.expectOne((r) => r.url === LIST).flush(page([caso()], []));
    await settle(f);
    expect(dialog(f).textContent).toContain('No hay productos activos');
  });

  it('un error de red conserva la decisión y la justificación', async () => {
    const f = await loaded();
    await resolveWith(f, 0, '');
    expect(dialog(f).textContent).toContain('No pudimos guardar la resolución');
    expect(dialog(f).open).toBe(true);
    expect(f.componentInstance.justification).toBe('Ingresos estables verificados en el banco.');
    expect(f.componentInstance.decision).toBe('APROBADO');
  });

  it('al resolver el único caso de la última página vuelve a la anterior', async () => {
    const f = await loaded(page([caso()], [micro], 11));
    f.componentInstance.page.set(2);
    await settle(f);
    http
      .expectOne((r) => r.url === LIST && r.params.get('pagina') === '2')
      .flush(page([caso()], [micro], 11));
    await settle(f);
    await openFirst(f);
    f.componentInstance.decision = f.componentInstance.reject;
    f.componentInstance.justification = 'Mora reciente sin explicación del cliente.';
    button(f, 'Resolver caso').click();
    await settle(f);
    http
      .expectOne(resolution())
      .flush({
        evaluacionId: CASE_ID,
        decision: 'RECHAZADO',
        justificacion: 'x',
        resueltoPor: 'ana',
        resueltoEn: '2026-10-09T10:00:00Z',
      });
    await settle(f);
    http
      .expectOne((r) => r.url === LIST && r.params.get('pagina') === '1')
      .flush(page([caso()], [micro], 10));
  });
});
