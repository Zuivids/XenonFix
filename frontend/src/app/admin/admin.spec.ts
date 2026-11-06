import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router, TitleStrategy } from '@angular/router';
import { App } from '../app';
import { routes } from '../app.routes';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { SeoTitleStrategy } from '../seo/seo-title-strategy';
import { PageApi } from '../pages/page-api';
import { stubPageApi } from '../pages/testing/starting-pages';
import { StoreApi } from '../store/store-api';
import { Me } from './admin-api';
import { sessionExpiredInterceptor } from './session-expired.interceptor';

const ME: Me = {
  id: 1,
  email: 'admin@example.com',
  displayName: 'Admins',
  passwordChangeRequired: false,
  step: 'READY',
};

describe('Admin panel', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([sessionExpiredInterceptor])),
        provideHttpClientTesting(),
        { provide: TitleStrategy, useClass: SeoTitleStrategy },
        // For the public pages some of these tests pass through
        { provide: StoreApi, useValue: { list: () => of({ items: [], page: 0, totalPages: 0 }) } },
        { provide: PageApi, useValue: stubPageApi() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  /** Waits for a request: lazy routes and awaited calls take a few turns to send it. */
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

  /** Lets everything after a response finish, including the render. */
  async function settled(fixture: ComponentFixture<App>): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 10));
    await fixture.whenStable();
  }

  async function open(url: string, me: Me | null) {
    const fixture = TestBed.createComponent(App);
    const navigation = router.navigateByUrl(url);
    const pending = await request('/api/admin/me');
    if (me) {
      pending.flush(me);
    } else {
      pending.flush({}, { status: 401, statusText: 'Unauthorized' });
    }
    await navigation;
    await settled(fixture);
    if (!me) {
      await loginPageAsksWhoIsLoggedIn(fixture);
    }
    return fixture;
  }

  /** The login page checks whether a code is still awaited; nobody is logged in here. */
  async function loginPageAsksWhoIsLoggedIn(fixture: ComponentFixture<App>): Promise<void> {
    (await request('/api/admin/me')).flush({}, { status: 401, statusText: 'Unauthorized' });
    await settled(fixture);
  }

  async function submitPassword(fixture: ComponentFixture<App>): Promise<TestRequest> {
    const root = fixture.nativeElement as HTMLElement;
    type(root, 'email', ME.email);
    type(root, 'password', 'correct horse battery');
    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    (await request('/api/admin/auth/csrf')).flush(null);
    return request('/api/admin/auth/login');
  }

  function click(root: HTMLElement, text: string): void {
    [...root.querySelectorAll('button')].find((button) => button.textContent?.trim() === text)!.click();
  }

  function type(root: HTMLElement, name: string, value: string): void {
    const input = root.querySelector<HTMLInputElement>(`input[formControlName="${name}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('sends visitors to the login page', async () => {
    await open('/admin', null);

    expect(router.url).toBe('/admin/login');
  });

  it('sends an admin with a temporary password to change it', async () => {
    const me: Me = { ...ME, passwordChangeRequired: true, step: 'PASSWORD_CHANGE_REQUIRED' };
    const fixture = TestBed.createComponent(App);
    const navigation = router.navigateByUrl('/admin/admins');
    (await request('/api/admin/me')).flush(me);
    // The guard runs again for the page it redirected to
    (await request('/api/admin/me')).flush(me);
    await navigation;
    await settled(fixture);

    expect(router.url).toBe('/admin/password');
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[role="status"]')?.textContent).toContain('pagaidu parole');
    expect(root.querySelector('nav')?.textContent).not.toContain('Administratori');
  });

  it('logs in with the password and the code, and opens the panel', async () => {
    const fixture = await open('/admin', null);
    const root = fixture.nativeElement as HTMLElement;

    const login = await submitPassword(fixture);
    expect(login.request.body).toEqual({ email: ME.email, password: 'correct horse battery' });
    login.flush({ status: 'MFA_REQUIRED', admin: { ...ME, step: 'MFA_REQUIRED' } });
    await settled(fixture);

    expect(router.url).toBe('/admin/login');
    expect(root.querySelector('h1')?.textContent).toBe('Apstipriniet pieslēgšanos');
    const codeInput = root.querySelector<HTMLInputElement>('input[formControlName="code"]')!;
    expect(codeInput.getAttribute('autocomplete')).toBe('one-time-code');
    type(root, 'code', ' 123456 ');
    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    const code = await request('/api/admin/auth/mfa');
    expect(code.request.body).toEqual({ code: '123456' });
    code.flush({ status: 'AUTHENTICATED', admin: ME });
    (await request('/api/admin/me')).flush(ME);
    (await request('/api/admin/products')).flush({ items: [], page: 0, totalPages: 0 });
    await settled(fixture);

    expect(router.url).toBe('/admin/products');
    expect(root.querySelector('h1')?.textContent).toBe('Produkti');
  });

  it('explains a wrong code and lets the admin try a backup code', async () => {
    const fixture = await open('/admin', null);
    const root = fixture.nativeElement as HTMLElement;
    (await submitPassword(fixture)).flush({ status: 'MFA_REQUIRED', admin: { ...ME, step: 'MFA_REQUIRED' } });
    await settled(fixture);

    type(root, 'code', '000000');
    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    (await request('/api/admin/auth/mfa')).flush({ code: 'INVALID_CODE' }, { status: 400, statusText: 'Bad Request' });
    await settled(fixture);

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Kods nav pareizs');
    expect(root.querySelector<HTMLInputElement>('input[formControlName="code"]')?.value).toBe('');
    expect(router.url).toBe('/admin/login');

    click(root, 'Nav tālruņa? Izmantot rezerves kodu');
    await settled(fixture);
    expect(root.querySelector('label')?.textContent).toContain('Rezerves kods');
    type(root, 'code', 'K7QF2-M9XPD');
    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    expect((await request('/api/admin/auth/mfa')).request.body).toEqual({ code: 'K7QF2-M9XPD' });
  });

  it('goes back to the password when the code step has run out of time', async () => {
    const fixture = await open('/admin', null);
    const root = fixture.nativeElement as HTMLElement;
    (await submitPassword(fixture)).flush({ status: 'MFA_REQUIRED', admin: { ...ME, step: 'MFA_REQUIRED' } });
    await settled(fixture);

    type(root, 'code', '123456');
    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    (await request('/api/admin/auth/mfa')).flush({}, { status: 401, statusText: 'Unauthorized' });
    await settled(fixture);

    expect(root.querySelector('h1')?.textContent).toBe('Pieslēgšanās');
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('laiks beidzās');
  });

  it('sets up the app, shows the backup codes once and then opens the panel', async () => {
    const setupMe: Me = { ...ME, step: 'MFA_SETUP_REQUIRED' };
    const fixture = await open('/admin', null);
    const root = fixture.nativeElement as HTMLElement;
    (await submitPassword(fixture)).flush({ status: 'MFA_SETUP_REQUIRED', admin: setupMe });
    (await request('/api/admin/me')).flush(setupMe);
    (await request('/api/admin/me')).flush(setupMe);
    (await request('/api/admin/me/mfa/setup')).flush({ qrCode: 'data:image/png;base64,iVBORw0KGgo=', secret: 'ABCD EFGH' });
    await settled(fixture);

    expect(router.url).toBe('/admin/mfa');
    expect(root.querySelector('img.qr')?.getAttribute('src')).toBe('data:image/png;base64,iVBORw0KGgo=');
    expect(root.querySelector('.secret')?.textContent).toBe('ABCD EFGH');
    expect(root.querySelector('nav')?.textContent?.trim()).toBe('');

    type(root, 'code', '654321');
    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    const done = await request('/api/admin/me/mfa/setup');
    expect(done.request.method).toBe('POST');
    expect(done.request.body).toEqual({ code: '654321' });
    const codes = Array.from({ length: 10 }, (_, i) => `AAAAA-BBBB${i}`);
    done.flush({ codes, admin: ME });
    await settled(fixture);

    expect([...root.querySelectorAll('.codes code')].map((code) => code.textContent)).toEqual(codes);
    const next = [...root.querySelectorAll('button')].find((button) => button.textContent?.trim() === 'Turpināt')!;
    expect(next.disabled).toBe(true);
    root.querySelector<HTMLInputElement>('.check input')!.click();
    await settled(fixture);
    next.click();
    (await request('/api/admin/products')).flush({ items: [], page: 0, totalPages: 0 });
    await settled(fixture);

    expect(router.url).toBe('/admin/products');
    expect(root.querySelector('nav')?.textContent).toContain('Rezervācijas');
  });

  it('after setting up the app, a temporary password is changed next', async () => {
    const setupMe: Me = { ...ME, passwordChangeRequired: true, step: 'MFA_SETUP_REQUIRED' };
    const fixture = await open('/admin', null);
    const root = fixture.nativeElement as HTMLElement;
    (await submitPassword(fixture)).flush({ status: 'MFA_SETUP_REQUIRED', admin: setupMe });
    (await request('/api/admin/me')).flush(setupMe);
    (await request('/api/admin/me')).flush(setupMe);
    (await request('/api/admin/me/mfa/setup')).flush({ qrCode: 'data:image/png;base64,iVBORw0KGgo=', secret: 'ABCD' });
    await settled(fixture);
    type(root, 'code', '654321');
    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    (await request('/api/admin/me/mfa/setup')).flush({
      codes: ['AAAAA-BBBBB'],
      admin: { ...setupMe, step: 'PASSWORD_CHANGE_REQUIRED' },
    });
    await settled(fixture);

    root.querySelector<HTMLInputElement>('.check input')!.click();
    await settled(fixture);
    click(root, 'Turpināt');
    await settled(fixture);

    expect(router.url).toBe('/admin/password');
    expect(root.querySelector('[role="status"]')?.textContent).toContain('pagaidu parole');
  });

  it('lets an admin reset another admin\'s app setup after asking', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = await open('/admin/admins', ME);
    const root = fixture.nativeElement as HTMLElement;
    const other = {
      id: 2,
      email: 'other@example.com',
      displayName: 'Cits',
      status: 'ACTIVE',
      locked: false,
      passwordChangeRequired: false,
      mfaEnabled: true,
      lastLoginAt: null,
      createdAt: '2026-11-01T10:00:00Z',
    };
    (await request('/api/admin/admins')).flush([{ ...other, id: 1, email: ME.email }, other]);
    await settled(fixture);
    expect(root.querySelector('tbody')?.textContent).toContain('Iestatīta');

    click(root, 'Atiestatīt divpakāpju pieslēgšanos');

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('other@example.com'));
    const reset = await request('/api/admin/admins/2/reset-mfa');
    expect(reset.request.method).toBe('POST');
    reset.flush({ ...other, mfaEnabled: false });
    (await request('/api/admin/admins')).flush([{ ...other, mfaEnabled: false }]);
    await settled(fixture);
    expect(root.querySelector('tbody')?.textContent).toContain('Vēl nav');
    confirm.mockRestore();
  });

  it('makes new backup codes with a code from the app', async () => {
    const fixture = await open('/admin/password', ME);
    const root = fixture.nativeElement as HTMLElement;
    (await request('/api/admin/me/mfa/recovery-codes')).flush({ left: 3 });
    await settled(fixture);
    expect(root.querySelector('.recovery')?.textContent).toContain('Neizmantoti: 3 no 10');

    type(root, 'code', '112233');
    root.querySelectorAll('form')[1].dispatchEvent(new Event('submit'));
    const renew = await request('/api/admin/me/mfa/recovery-codes');
    expect(renew.request.method).toBe('POST');
    expect(renew.request.body).toEqual({ code: '112233' });
    renew.flush({ codes: ['CCCCC-DDDDD', 'EEEEE-FFFFF'], admin: ME });
    await settled(fixture);

    expect([...root.querySelectorAll('.codes code')].map((code) => code.textContent)).toEqual([
      'CCCCC-DDDDD',
      'EEEEE-FFFFF',
    ]);
  });

  it('shows a clear message when the login fails, and clears the password', async () => {
    const fixture = TestBed.createComponent(App);
    await router.navigateByUrl('/admin/login');
    await settled(fixture);
    await loginPageAsksWhoIsLoggedIn(fixture);
    const root = fixture.nativeElement as HTMLElement;
    type(root, 'email', ME.email);
    type(root, 'password', 'wrong password');

    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    (await request('/api/admin/auth/csrf')).flush(null);
    (await request('/api/admin/auth/login')).flush(
      { code: 'INVALID_CREDENTIALS' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await settled(fixture);

    expect(router.url).toBe('/admin/login');
    expect(root.querySelector('[role="alert"]')?.textContent).toBe('Nepareizs e-pasts vai parole.');
    expect(root.querySelector<HTMLInputElement>('input[type="password"]')?.value).toBe('');
  });

  it('goes back to the login page when the session has ended', async () => {
    const fixture = await open('/admin/audit', ME);

    (await request('/api/admin/audit-log')).flush({}, { status: 401, statusText: 'Unauthorized' });
    await settled(fixture);

    expect(router.url).toBe('/admin/login');
  });

  it('keeps the panel out of search engines, and the public pages in', async () => {
    await open('/admin', null);

    expect(document.head.querySelector('meta[name="robots"]')?.getAttribute('content')).toBe('noindex');
    expect(document.head.querySelector('link[rel="canonical"]')).toBeNull();
    expect(document.title).toBe('Pieslēgšanās - XenonFix');

    await router.navigateByUrl('/');

    expect(document.head.querySelector('meta[name="robots"]')).toBeNull();
    expect(document.head.querySelector('link[rel="canonical"]')).not.toBeNull();
  });
});
