import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router, TitleStrategy } from '@angular/router';
import { App } from '../app';
import { routes } from '../app.routes';
import { of } from 'rxjs';
import { SeoTitleStrategy } from '../seo/seo-title-strategy';
import { StoreApi } from '../store/store-api';
import { Me } from './admin-api';
import { sessionExpiredInterceptor } from './session-expired.interceptor';

const ME: Me = { id: 1, email: 'admin@example.com', displayName: 'Admins', passwordChangeRequired: false };

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
    return fixture;
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
    const me = { ...ME, passwordChangeRequired: true };
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

  it('logs in and opens the panel', async () => {
    const fixture = await open('/admin', null);
    const root = fixture.nativeElement as HTMLElement;
    type(root, 'email', ME.email);
    type(root, 'password', 'correct horse battery');

    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    (await request('/api/admin/auth/csrf')).flush(null);
    const login = await request('/api/admin/auth/login');
    expect(login.request.body).toEqual({ email: ME.email, password: 'correct horse battery' });
    login.flush({ status: 'AUTHENTICATED', admin: ME });
    (await request('/api/admin/me')).flush(ME);
    (await request('/api/admin/products')).flush({ items: [], page: 0, totalPages: 0 });
    await settled(fixture);

    expect(router.url).toBe('/admin/products');
    expect(root.querySelector('h1')?.textContent).toBe('Produkti');
  });

  it('shows a clear message when the login fails, and clears the password', async () => {
    const fixture = TestBed.createComponent(App);
    await router.navigateByUrl('/admin/login');
    await settled(fixture);
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
