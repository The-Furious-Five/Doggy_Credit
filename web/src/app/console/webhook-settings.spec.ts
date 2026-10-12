import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BASE_PATH } from '../api/variables';
import { EventoWebhook } from '../api/model/eventoWebhook';
import { HANDLED_PROBLEMS } from '../http/api-error.interceptor';
import { WebhookSettings } from './webhook-settings';

const event = EventoWebhook.CreditAssessmentCompletedV1;
const secret = 'receiver-test-secret-with-32-characters';
const configured = { url: 'https://receiver.example/events', eventos: [event], secretoConfigurado: true };

describe('Desarrolladores › Webhooks', () => {
  let http: HttpTestingController;
  const { showModal, close } = HTMLDialogElement.prototype;

  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function () { this.open = true; };
    HTMLDialogElement.prototype.close = function () { this.open = false; this.dispatchEvent(new Event('close')); };
  });
  afterAll(() => {
    HTMLDialogElement.prototype.showModal = showModal;
    HTMLDialogElement.prototype.close = close;
  });
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [WebhookSettings],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: BASE_PATH, useValue: '/api/v1' }],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  function render() {
    const fixture = TestBed.createComponent(WebhookSettings);
    fixture.detectChanges();
    const load = http.expectOne('/api/v1/webhooks');
    expect(load.request.method).toBe('GET');
    return { fixture, load, component: fixture.componentInstance, page: fixture.nativeElement as HTMLElement };
  }

  it('presenta carga y luego el formulario vacío', () => {
    const { fixture, load, page } = render();
    expect(page.querySelector('[aria-label="Cargando Webhooks"]')).not.toBeNull();
    load.flush({ url: null, eventos: [], secretoConfigurado: false });
    fixture.detectChanges();
    expect(page.textContent).toContain('Sin Webhooks configurados');
    expect(page.querySelector('form')).not.toBeNull();
  });

  it('el bloqueo real de FREE no ofrece acciones de configuración', () => {
    const { fixture, load, page } = render();
    load.flush({ code: 'FEATURE_NOT_IN_PLAN' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(page.textContent).toContain('Webhooks disponibles con Pro');
    expect(page.querySelector('form')).toBeNull();
    // La tarjeta ya explica el plan: el interceptor no repite FEATURE_NOT_IN_PLAN en un toast (E-09).
    expect(load.request.context.get(HANDLED_PROBLEMS)).toEqual(['FEATURE_NOT_IN_PLAN']);
  });

  it('un error de permisos distinto no se confunde con una restricción de plan', () => {
    const { fixture, load, page } = render();
    load.flush({ code: 'INSUFFICIENT_ROLE' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(page.textContent).toContain('No se pudieron cargar los Webhooks');
  });

  it('un fallo de carga permite reintentar contra la API', async () => {
    const { fixture, load, page } = render();
    load.flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    [...page.querySelectorAll('button')].find(button => button.textContent?.includes('Reintentar'))!.click();
    http.expectOne('/api/v1/webhooks').flush(configured);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(page.querySelector<HTMLInputElement>('[name="webhookUrl"]')?.value).toBe(configured.url);
    expect(page.querySelector<HTMLInputElement>('[name="webhookSecret"]')?.value).toBe('');
  });

  it('envía URL, eventos como arreglo y secreto; evita el doble envío y limpia el secreto al guardar', () => {
    const { fixture, load, component, page } = render();
    load.flush({ eventos: [], secretoConfigurado: false });
    fixture.detectChanges();
    component.url = '  https://receiver.example/events  ';
    component.selected = [event];
    component.secret = secret;
    page.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    component.save();
    const save = http.expectOne('/api/v1/webhooks');
    expect(save.request.method).toBe('PUT');
    expect(save.request.body).toEqual({ url: configured.url, eventos: [event], secreto: secret });
    expect(component.saving()).toBe(true);
    save.flush(configured);
    fixture.detectChanges();
    expect(component.secret).toBe('');
    expect(page.textContent).not.toContain(secret);
    expect(component.saving()).toBe(false);
  });

  it('editar conservando el secreto no lo envía vacío ni lo solicita al servidor', () => {
    const { load, component } = render();
    load.flush(configured);
    component.save();
    const save = http.expectOne('/api/v1/webhooks');
    expect(save.request.body).toEqual({ url: configured.url, eventos: [event] });
    save.flush(configured);
  });

  it('valida URL, eventos y secreto antes de enviar', () => {
    const { load, component } = render();
    load.flush({ eventos: [], secretoConfigurado: false });
    for (const url of ['invalid', 'http://receiver.example', 'https://user:password@receiver.example', 'https://receiver.example/#fragment']) {
      component.url = url;
      component.save();
      expect(component.error()).toContain('HTTPS');
    }
    component.url = configured.url;
    component.save();
    expect(component.error()).toContain('evento');
    component.selected = [event];
    component.secret = 'short';
    component.save();
    expect(component.error()).toContain('secreto');
    http.expectNone(request => request.method === 'PUT');
  });

  it('conserva todos los datos ante un fallo de guardado', () => {
    const { load, component } = render();
    load.flush(configured);
    component.secret = secret;
    component.save();
    http.expectOne('/api/v1/webhooks').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(component.url).toBe(configured.url);
    expect(component.selected).toEqual([event]);
    expect(component.secret).toBe(secret);
    expect(component.error()).toContain('se conservaron');
  });

  it('si el plan cambia durante la edición muestra el bloqueo de la API', () => {
    const { fixture, load, component, page } = render();
    load.flush(configured);
    component.save();
    http.expectOne('/api/v1/webhooks').flush({ code: 'FEATURE_NOT_IN_PLAN' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(page.querySelector('form')).toBeNull();
    expect(page.textContent).toContain('Webhooks disponibles con Pro');
  });

  it('desactivar exige confirmación y después refleja la configuración vacía', () => {
    const { fixture, load, component, page } = render();
    load.flush(configured);
    fixture.detectChanges();
    [...page.querySelectorAll('button')].find(button => button.textContent?.includes('Desactivar'))!.click();
    fixture.detectChanges();
    expect(page.querySelector('dialog')?.open).toBe(true);
    http.expectNone(request => request.method === 'PUT');
    component.disable();
    const save = http.expectOne('/api/v1/webhooks');
    expect(save.request.body).toEqual({ url: null, eventos: [] });
    save.flush({ url: null, eventos: [], secretoConfigurado: false });
    fixture.detectChanges();
    expect(page.querySelector('dialog')?.open).toBe(false);
    expect(page.textContent).toContain('Sin Webhooks configurados');
  });
});
