import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BASE_PATH, ProveedorCredenciales } from '../api';
import { DsToastService } from '../design-system';
import { IntegrationCredentials } from './integration-credentials';

const secret = 'component-test-api-key-1357';
const configured = { configuradas: true, ultimosCuatro: '1357' };
const empty = { configuradas: false, ultimosCuatro: '' };

describe('Credenciales propias del tenant', () => {
  let fixture: ComponentFixture<IntegrationCredentials>;
  let component: IntegrationCredentials;
  let http: HttpTestingController;
  let page: HTMLElement;
  const toast = { show: vi.fn() };
  const url = '/api/v1/integraciones/BURO/credenciales';
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
    fixture = TestBed.createComponent(IntegrationCredentials);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('source', ProveedorCredenciales.Buro);
    fixture.componentRef.setInput('name', 'Buró de crédito nacional');
    page = fixture.nativeElement;
    fixture.detectChanges();
  });
  afterEach(() => http.verify());

  function click(label: string): void {
    [...page.querySelectorAll<HTMLButtonElement>('button')]
      .find((button) => button.textContent?.trim() === label)!
      .click();
    fixture.detectChanges();
  }
  function open(metadata = empty): void {
    click('Credenciales propias');
    const request = http.expectOne(url);
    expect(request.request.method).toBe('GET');
    request.flush(metadata);
    fixture.detectChanges();
  }

  it('consulta solo al abrir, etiqueta el diálogo y nunca precarga el secreto', () => {
    expect(page.querySelector('input')).toBeNull();
    http.expectNone(url);
    open(configured);
    const dialog = page.querySelector('dialog')!;
    expect(dialog.open).toBe(true);
    expect(dialog.getAttribute('aria-labelledby')).toBeTruthy();
    const input = page.querySelector('input')!;
    expect(input.type).toBe('password');
    expect(input.value).toBe('');
    expect(input.getAttribute('autocomplete')).toBe('new-password');
    expect(page.querySelector(`label[for="${input.id}"]`)?.textContent).toContain(
      'Nueva clave API',
    );
    expect(page.textContent).toContain('1357');
    expect(page.textContent).not.toContain(secret);
  });

  it('valida la clave sin enviarla y ofrece una corrección accesible', () => {
    open();
    for (const key of ['', 'short', 'invalid secret', '12345678\r\nX: foo']) {
      component.key = key;
      click('Guardar clave');
      expect(page.querySelector('input')?.getAttribute('aria-invalid')).toBe('true');
    }
    http.expectNone((request) => request.method === 'PUT');
    expect(page.textContent).toContain('Ingresa de 8 a 2048 caracteres');
  });

  it('guarda con el cliente generado, bloquea duplicados y borra inmediatamente el secreto', () => {
    open();
    component.key = secret;
    click('Guardar clave');
    component.save();
    const request = http.expectOne(url);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ clave: secret });
    expect(request.request.headers.has('X-Tenant-Id')).toBe(false);
    expect(component.key).toBe('');
    expect(page.querySelector('input')?.value).toBe('');
    expect(page.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(true);
    request.flush(configured);
    fixture.detectChanges();
    expect(component.busy()).toBe(false);
    expect(page.textContent).toContain('1357');
    expect(page.textContent).not.toContain(secret);
    expect(toast.show).toHaveBeenCalledWith('Credenciales propias guardadas.');
  });

  it('conserva los metadatos tras error y pide reingresar la clave sin reflejar el error del servidor', () => {
    open(configured);
    component.key = secret;
    click('Guardar clave');
    http.expectOne(url).flush({ detail: secret }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(component.key).toBe('');
    expect(page.textContent).toContain('ingrésala nuevamente');
    expect(page.textContent).toContain('1357');
    expect(page.textContent).not.toContain(secret);
    expect(page.querySelector('ds-alert')?.getAttribute('role')).toBe('alert');
  });

  it('elimina y vuelve al estado DoggyCredit sin volver a pedir el secreto', () => {
    open(configured);
    click('Eliminar credenciales propias');
    component.remove();
    const request = http.expectOne(url);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(page.textContent).toContain('Se utilizan las credenciales de DoggyCredit');
    expect(page.textContent).not.toContain('1357');
    expect(toast.show).toHaveBeenCalledWith('Se usarán las credenciales de DoggyCredit.');
  });

  it('permite reintentar un fallo de consulta sin habilitar cambios sobre estado desconocido', () => {
    click('Credenciales propias');
    http.expectOne(url).flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    component.key = secret;
    component.save();
    component.remove();
    http.expectNone((request) => request.method !== 'GET');
    expect(page.querySelector('form')).toBeNull();
    click('Reintentar');
    http.expectOne(url).flush(empty);
    fixture.detectChanges();
    expect(page.querySelector('form')).not.toBeNull();
    expect(component.key).toBe('');
  });

  it('separa banco y segundo banco y borra la clave al cambiar de proveedor', () => {
    fixture.componentRef.setInput('source', ProveedorCredenciales.Banco);
    fixture.detectChanges();
    click('Credenciales propias');
    http.expectOne('/api/v1/integraciones/BANCO/credenciales').flush(configured);
    fixture.detectChanges();
    component.key = secret;
    const selector = page.querySelector<HTMLElement>('[role="combobox"]')!;
    for (const key of ['ArrowDown', 'End', 'Enter']) {
      selector.dispatchEvent(
        new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }),
      );
      fixture.detectChanges();
      TestBed.tick();
    }
    http.expectOne('/api/v1/integraciones/BANCO_DOS/credenciales').flush(empty);
    fixture.detectChanges();
    expect(component.key).toBe('');
    expect(component.provider()).toBe(ProveedorCredenciales.BancoDos);
  });

  it('cancela cargas y limpia el secreto al cerrar y al destruir', () => {
    click('Credenciales propias');
    const request = http.expectOne(url);
    component.key = secret;
    component.dialog().close();
    fixture.detectChanges();
    expect(request.cancelled).toBe(true);
    expect(component.key).toBe('');
    expect(page.querySelector('input')).toBeNull();
    open();
    component.key = secret;
    fixture.destroy();
    expect(component.key).toBe('');
  });
});
