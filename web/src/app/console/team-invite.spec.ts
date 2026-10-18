import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BASE_PATH, MiembroEquipo, MiembroEquipoEstadoEnum } from '../api';
import { DsToastService } from '../design-system';
import { HANDLED_PROBLEMS } from '../http/api-error.interceptor';
import { TeamInvite } from './team-invite';

describe('Invitar al equipo (A-10)', () => {
  let fixture: ComponentFixture<TeamInvite>;
  let http: HttpTestingController;
  let page: HTMLElement;
  const toast = { show: vi.fn() };
  const url = '/api/v1/tenant/actual/equipo';
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
    fixture = TestBed.createComponent(TeamInvite);
    page = fixture.nativeElement;
    fixture.detectChanges();
  });
  afterEach(() => http.verify());

  function button(label: string): HTMLButtonElement {
    return [...page.querySelectorAll<HTMLButtonElement>('button')].find(
      (candidate) => candidate.textContent?.trim() === label,
    )!;
  }
  function type(label: string, value: string): void {
    const field = [...page.querySelectorAll('label')].find((l) => l.textContent?.includes(label))!;
    const input = page.querySelector<HTMLInputElement>(`#${field.getAttribute('for')}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }
  async function submit(): Promise<void> {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
  }
  async function fill(): Promise<void> {
    button('Invitar').click();
    fixture.detectChanges();
    await fixture.whenStable();
    type('Nombre completo', ' Rosa Mamani ');
    type('Email', 'rosa@example.test');
  }

  it('abre un diálogo con campos etiquetados y envía nombre, email y rol de analista', async () => {
    const invited = vi.fn();
    fixture.componentInstance.invited.subscribe(invited);
    await fill();
    expect(page.querySelector('dialog')!.open).toBe(true);
    await submit();

    const request = http.expectOne(url);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ nombre: 'Rosa Mamani', email: 'rosa@example.test', rol: 'ANALYST' });
    expect(request.request.context.get(HANDLED_PROBLEMS)).toEqual(['PLAN_LIMIT_REACHED', 'MEMBER_ALREADY_EXISTS']);
    const member: MiembroEquipo = { nombre: 'Rosa Mamani', email: 'rosa@example.test', rol: 'ANALYST', estado: MiembroEquipoEstadoEnum.Invitado };
    request.flush(member, { status: 201, statusText: 'Created' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(invited).toHaveBeenCalledWith(member);
    expect(page.querySelector('dialog')!.open).toBe(false);
    expect(toast.show).toHaveBeenCalledWith('Invitación creada para rosa@example.test.');
  });

  it('no llama a la API con datos inválidos y explica qué corregir', async () => {
    button('Invitar').click();
    fixture.detectChanges();
    type('Email', 'rosa');
    await submit();
    http.expectNone(url);
    expect(page.textContent).toContain('Ingresa el nombre y apellido de la persona.');
    expect(page.textContent).toContain('Ingresa un email válido');
  });

  it('muestra el límite del plan a partir del error del backend y conserva los datos', async () => {
    await fill();
    await submit();
    http.expectOne(url).flush(
      { code: 'PLAN_LIMIT_REACHED', detail: 'El plan Gratis permite un máximo de 3 usuarios.' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(page.textContent).toContain('Tu plan no admite más usuarios');
    expect(page.textContent).toContain('El plan Gratis permite un máximo de 3 usuarios.');
    expect(page.querySelector('dialog')!.open).toBe(true);
    expect(fixture.componentInstance.email).toBe('rosa@example.test');
  });

  it('distingue una cuenta existente de un fallo de conexión', async () => {
    await fill();
    await submit();
    http.expectOne(url).flush({ code: 'MEMBER_ALREADY_EXISTS' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(page.textContent).toContain('Esa persona ya tiene una cuenta');

    await submit();
    http.expectOne(url).flush({ code: 'PROVIDER_UNAVAILABLE' }, { status: 502, statusText: 'Bad Gateway' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(page.textContent).toContain('No pudimos enviar la invitación');
  });
});
