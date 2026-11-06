import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { adminRoutes } from '../admin.routes';
import { changeWarning, Reservation, ReservationStatus, ReservationSummary } from './reservation-api';
import { ReservationDetail } from './reservation-detail';
import { ReservationList } from './reservation-list';

const SUMMARY: ReservationSummary = {
  id: 7,
  reference: 'XF-000007',
  productName: 'Audi A5 lukturis',
  quantity: 2,
  total: 799,
  customerName: 'Anna Bērziņa',
  customerPhone: '+371 20000000',
  status: 'PENDING',
  createdAt: '2026-11-05T10:00:00Z',
  anonymized: false,
};

const RESERVATION: Reservation = {
  id: 7,
  reference: 'XF-000007',
  productId: 3,
  productName: 'Audi A5 lukturis',
  partNumber: '8T0.941.032C',
  unitPrice: 399.5,
  quantity: 2,
  total: 799,
  customerName: 'Anna Bērziņa',
  customerPhone: '+371 20000000',
  customerEmail: 'anna@example.com',
  comment: 'Zvaniet pēc 15:00',
  adminNote: null,
  language: 'ru',
  status: 'PENDING',
  createdAt: '2026-11-05T10:00:00Z',
  updatedAt: '2026-11-05T10:00:00Z',
  anonymizedAt: null,
  version: 4,
};

const ERASED: Reservation = {
  ...RESERVATION,
  status: 'COMPLETED',
  customerName: null,
  customerPhone: null,
  customerEmail: null,
  comment: null,
  anonymizedAt: '2026-11-06T10:00:00Z',
  version: 6,
};

describe('changeWarning', () => {
  it.each<[ReservationStatus, ReservationStatus, string | null]>([
    ['PENDING', 'CONFIRMED', 'Klientam tiks nosūtīts e-pasts.'],
    ['PENDING', 'CANCELLED', 'Atlikumā atgriezīsies 2 gab. Klientam tiks nosūtīts e-pasts.'],
    ['CONFIRMED', 'CANCELLED', 'Atlikumā atgriezīsies 2 gab. Klientam tiks nosūtīts e-pasts.'],
    ['COMPLETED', 'CANCELLED', 'Atlikumā atgriezīsies 2 gab.'],
    ['CANCELLED', 'CONFIRMED', 'No atlikuma tiks paņemti 2 gab. Klientam tiks nosūtīts e-pasts.'],
    ['CANCELLED', 'PENDING', 'No atlikuma tiks paņemti 2 gab.'],
    ['CANCELLED', 'COMPLETED', 'No atlikuma tiks paņemti 2 gab.'],
    ['PENDING', 'COMPLETED', null],
    ['CONFIRMED', 'COMPLETED', null],
    ['CONFIRMED', 'PENDING', null],
    ['COMPLETED', 'PENDING', null],
    ['COMPLETED', 'CONFIRMED', null],
  ])('from %s to %s', (from, to, warning) => {
    expect(changeWarning(from, to, 2)).toBe(warning);
  });
});

describe('Admin reservations', () => {
  let http: HttpTestingController;
  let confirm: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'admin', children: adminRoutes }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  async function request(url: string): Promise<TestRequest> {
    for (let turn = 0; turn < 100; turn++) {
      const [found] = http.match((pending) => pending.url === url);
      if (found) {
        return found;
      }
      await new Promise((resolve) => setTimeout(resolve));
    }
    throw new Error(`No request to ${url}`);
  }

  async function settled(fixture: ComponentFixture<unknown>): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 10));
    await fixture.whenStable();
  }

  async function openDetail(reservation = RESERVATION): Promise<ComponentFixture<ReservationDetail>> {
    const fixture = TestBed.createComponent(ReservationDetail);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges();
    (await request('/api/admin/reservations/7')).flush(reservation);
    await settled(fixture);
    return fixture;
  }

  function buttons(root: HTMLElement): string[] {
    return [...root.querySelectorAll('.status-actions button')].map((b) => b.textContent!.trim());
  }

  function click(root: HTMLElement, text: string): void {
    [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text)!.click();
  }

  function typeNote(root: HTMLElement, text: string): void {
    const note = root.querySelector('textarea')!;
    note.value = text;
    note.dispatchEvent(new Event('input'));
  }

  it('lists reservations, marks the waiting ones and links to the details', async () => {
    const fixture = TestBed.createComponent(ReservationList);
    fixture.detectChanges();
    (await request('/api/admin/reservations')).flush({
      items: [SUMMARY, { ...SUMMARY, id: 8, reference: 'XF-000008', status: 'COMPLETED' }],
      page: 0,
      totalPages: 1,
    });
    await settled(fixture);
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');

    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe('/admin/reservations/7');
    expect(rows[0].textContent).toContain('Anna Bērziņa');
    expect(rows[0].textContent).toContain('799.00 €');
    expect(rows[0].textContent).toContain('Gaida');
    expect(rows[0].classList).toContain('waiting');
    expect(rows[1].textContent).toContain('Pabeigta');
    expect(rows[1].classList).not.toContain('waiting');
  });

  it('searches and filters by status', async () => {
    const fixture = TestBed.createComponent(ReservationList);
    fixture.detectChanges();
    (await request('/api/admin/reservations')).flush({ items: [], page: 0, totalPages: 0 });
    await settled(fixture);
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Nav atrasta neviena rezervācija');

    const search = root.querySelector<HTMLInputElement>('input[type="search"]')!;
    search.value = ' xf-7 ';
    search.dispatchEvent(new Event('input'));
    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    const searched = await request('/api/admin/reservations');
    expect(searched.request.params.get('search')).toBe('xf-7');
    searched.flush({ items: [], page: 0, totalPages: 0 });
    await settled(fixture);

    const status = root.querySelector('select')!;
    status.selectedIndex = 3;
    status.dispatchEvent(new Event('change'));
    const filtered = await request('/api/admin/reservations');
    expect(filtered.request.params.get('status')).toBe('CANCELLED');
    filtered.flush({ items: [], page: 0, totalPages: 0 });
  });

  it('shows the details, with a button for every other status', async () => {
    const root = (await openDetail()).nativeElement as HTMLElement;

    expect(root.querySelector('h1')?.textContent).toBe('XF-000007');
    expect(root.querySelector('a[href="tel:+37120000000"]')).not.toBeNull();
    expect(root.querySelector('a[href="mailto:anna@example.com"]')).not.toBeNull();
    expect(root.querySelector('a[href="/admin/products/3"]')?.textContent).toContain('Audi A5 lukturis');
    expect(root.textContent).toContain('Zvaniet pēc 15:00');
    expect(root.textContent).toContain('8T0.941.032C');
    expect(buttons(root)).toEqual(['Apstiprināt', 'Atzīmēt kā pabeigtu', 'Atcelt']);
  });

  it('offers reopening a cancelled reservation', async () => {
    const root = (await openDetail({ ...RESERVATION, status: 'CANCELLED' })).nativeElement as HTMLElement;

    expect(buttons(root)).toEqual(['Apstiprināt', 'Atzīmēt kā pabeigtu', 'Atgriezt uz "Gaida"']);
  });

  it('asks before a change that moves stock, and sends the version', async () => {
    const fixture = await openDetail();
    const root = fixture.nativeElement as HTMLElement;

    click(root, 'Atcelt');

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('Atlikumā atgriezīsies 2 gab.'));
    const sent = await request('/api/admin/reservations/7/status');
    expect(sent.request.body).toEqual({ status: 'CANCELLED', version: 4 });
    sent.flush({ ...RESERVATION, status: 'CANCELLED', version: 5 });
    await settled(fixture);
    expect(root.querySelector('.title .status')?.textContent).toBe('Atcelta');
    expect(buttons(root)).toContain('Atgriezt uz "Gaida"');
  });

  it('sends nothing when the admin says no', async () => {
    confirm.mockReturnValue(false);
    const fixture = await openDetail();

    click(fixture.nativeElement, 'Atcelt');
    await settled(fixture);

    http.expectNone('/api/admin/reservations/7/status');
  });

  it('does not ask when nothing moves and nobody is emailed', async () => {
    const fixture = await openDetail();

    click(fixture.nativeElement, 'Atzīmēt kā pabeigtu');

    expect(confirm).not.toHaveBeenCalled();
    (await request('/api/admin/reservations/7/status')).flush({ ...RESERVATION, status: 'COMPLETED', version: 5 });
  });

  it('explains when there is not enough stock to reopen', async () => {
    const fixture = await openDetail({ ...RESERVATION, status: 'CANCELLED' });
    const root = fixture.nativeElement as HTMLElement;

    click(root, 'Apstiprināt');
    (await request('/api/admin/reservations/7/status')).flush(
      { code: 'OUT_OF_STOCK' },
      { status: 409, statusText: 'Conflict' },
    );
    await settled(fixture);

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('nepietiek');
    expect(root.querySelector('.title .status')?.textContent).toBe('Atcelta');
  });

  it('saves the note and keeps it as saved', async () => {
    const fixture = await openDetail();
    const root = fixture.nativeElement as HTMLElement;

    typeNote(root, '  Atnāks piektdien  ');
    await settled(fixture);
    click(root, 'Saglabāt piezīmes');
    const sent = await request('/api/admin/reservations/7/note');
    expect(sent.request.body).toEqual({ note: '  Atnāks piektdien  ', version: 4 });
    sent.flush({ ...RESERVATION, adminNote: 'Atnāks piektdien', version: 5 });
    await settled(fixture);

    expect(root.querySelector('textarea')?.value).toBe('Atnāks piektdien');
    expect(root.querySelector('[role="status"]')?.textContent).toBe('Saglabāts.');
  });

  it('marks anonymized reservations in the list', async () => {
    const fixture = TestBed.createComponent(ReservationList);
    fixture.detectChanges();
    (await request('/api/admin/reservations')).flush({
      items: [{ ...SUMMARY, customerName: null, customerPhone: null, status: 'COMPLETED', anonymized: true }],
      page: 0,
      totalPages: 1,
    });
    await settled(fixture);

    expect((fixture.nativeElement as HTMLElement).querySelector('tbody tr')?.textContent).toContain('Dati dzēsti');
  });

  it('offers erasing the customer only once the reservation is finished', async () => {
    const pending = (await openDetail()).nativeElement as HTMLElement;
    expect(pending.querySelector('.erase button')).toBeNull();
    expect(pending.querySelector('.erase')?.textContent).toContain('atcelta vai pabeigta');
  });

  it('asks before erasing the customer, then shows the reservation without them', async () => {
    const fixture = await openDetail({ ...RESERVATION, status: 'COMPLETED' });
    const root = fixture.nativeElement as HTMLElement;

    click(root, 'Dzēst klienta datus');

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('nevar atsaukt'));
    const sent = await request('/api/admin/reservations/7/anonymize');
    expect(sent.request.body).toEqual({ version: 4 });
    sent.flush(ERASED);
    await settled(fixture);

    expect(root.textContent).toContain('Klienta dati dzēsti 06.11.2026');
    expect(root.textContent).not.toContain('Anna Bērziņa');
    expect(root.querySelector('.status-actions')).toBeNull();
    expect(root.querySelector('textarea')).toBeNull();
    expect(root.querySelector('.erase')).toBeNull();
  });

  it('erases nothing when the admin says no', async () => {
    confirm.mockReturnValue(false);
    const fixture = await openDetail({ ...RESERVATION, status: 'CANCELLED' });

    click(fixture.nativeElement, 'Dzēst klienta datus');
    await settled(fixture);

    http.expectNone('/api/admin/reservations/7/anonymize');
  });

  it('keeps the typed note and loads the news when someone else changed the reservation', async () => {
    const fixture = await openDetail();
    const root = fixture.nativeElement as HTMLElement;
    typeNote(root, 'Mans teksts');
    await settled(fixture);

    click(root, 'Saglabāt piezīmes');
    (await request('/api/admin/reservations/7/note')).flush(
      { code: 'CHANGED_BY_ANOTHER' },
      { status: 409, statusText: 'Conflict' },
    );
    (await request('/api/admin/reservations/7')).flush({ ...RESERVATION, status: 'CONFIRMED', version: 5 });
    await settled(fixture);

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Kāds cits');
    expect(root.querySelector('.title .status')?.textContent).toBe('Apstiprināta');
    expect(root.querySelector('textarea')?.value).toBe('Mans teksts');

    click(root, 'Saglabāt piezīmes');
    expect((await request('/api/admin/reservations/7/note')).request.body).toEqual({
      note: 'Mans teksts',
      version: 5,
    });
  });
});
