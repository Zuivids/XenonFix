import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, TitleStrategy, withComponentInputBinding } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { App } from './app';
import { PAGE_PATHS, routes } from './app.routes';
import { PageApi } from './pages/page-api';
import { stubPageApi } from './pages/testing/starting-pages';
import { SeoTitleStrategy } from './seo/seo-title-strategy';
import { StoreApi, StoreProduct, StoreProductSummary } from './store/store-api';

const SUMMARY: StoreProductSummary = {
  slug: 'audi-a4',
  name: 'Audi A4 lukturis',
  price: 450,
  salePrice: 399.5,
  available: true,
  thumbnailUrl: '/uploads/products/1/thumb.jpg',
};

const PRODUCT: StoreProduct = {
  slug: 'audi-a4',
  name: 'Audi A4 lukturis',
  description: 'Kreisais lukturis.\nLabā stāvoklī.',
  partNumber: '8K0941003',
  price: 450,
  salePrice: 399.5,
  available: true,
  images: [{ url: '/uploads/products/1/photo.jpg', thumbnailUrl: '/uploads/products/1/thumb.jpg', width: 800, height: 600 }],
};

/** What the store's backend answers in these tests. */
const store = {
  items: [] as StoreProductSummary[],
  product: PRODUCT as StoreProduct | null,
};

const STUB_API: Partial<StoreApi> = {
  list: () => of({ items: store.items, page: 0, totalPages: store.items.length ? 1 : 0 }),
  get: (slug) =>
    store.product && slug === store.product.slug
      ? of(store.product)
      : throwError(() => new HttpErrorResponse({ status: 404 })),
};

async function appAt(url: string) {
  const fixture = TestBed.createComponent(App);
  await TestBed.inject(Router).navigateByUrl(url);
  await fixture.whenStable();
  return fixture;
}

function hrefs(root: HTMLElement, selector = '.language-switcher a') {
  return [...root.querySelectorAll(selector)].map((a) => a.getAttribute('href'));
}

describe('App', () => {
  let pages: ReturnType<typeof stubPageApi>;

  beforeEach(() => {
    pages = stubPageApi();
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        { provide: TitleStrategy, useClass: SeoTitleStrategy },
        { provide: StoreApi, useValue: STUB_API },
        { provide: PageApi, useValue: pages },
      ],
    });
    store.items = [];
    store.product = PRODUCT;
  });

  it.each([
    ['/', 'lv', 'Auto priekšējo lukturu remonts'],
    ['/ru', 'ru', 'Ремонт передних автомобильных фар'],
    ['/en', 'en', 'Car headlight repair'],
  ])('renders %s in its language', async (url, language, heading) => {
    const root = (await appAt(url)).nativeElement as HTMLElement;

    expect(root.querySelector('h1')?.textContent).toBe(heading);
    expect(document.documentElement.getAttribute('lang')).toBe(language);
    expect(root.querySelector('.language-switcher [aria-current]')?.getAttribute('hreflang')).toBe(language);
  });

  it.each(
    PAGE_PATHS.flatMap((path) => [
      [`/${path}`, `/ru/${path}`, `/en/${path}`].map((url) => [url] as const),
    ]).flat(),
  )('%s has a translated title, heading and description', async (url) => {
    const root = (await appAt(url)).nativeElement as HTMLElement;

    const heading = root.querySelector('h1')?.textContent ?? '';
    expect(heading).not.toBe('');
    expect(heading).not.toMatch(/^[a-z]+\.[a-zA-Z]+$/);
    expect(document.title).toContain('XenonFix');
    expect(document.querySelector('meta[name="description"]')?.getAttribute('content')).toBeTruthy();
    expect(root.querySelector('main')?.textContent?.length).toBeGreaterThan(50);
  });

  it('links to the pages in the current language', async () => {
    const root = (await appAt('/ru/kontakti')).nativeElement as HTMLElement;

    expect(hrefs(root, '.main-nav a')).toEqual([
      '/ru',
      '/ru/par-mums',
      '/ru/musu-pakalpojumi',
      '/ru/store',
      '/ru/musu-darbi',
      '/ru/kontakti',
    ]);
    expect(root.querySelector('.main-nav a.active')?.getAttribute('href')).toBe('/ru/kontakti');
    expect(hrefs(root)).toEqual(['/kontakti', '/ru/kontakti', '/en/kontakti']);
  });

  it('marks only the home link as active on the home page', async () => {
    const root = (await appAt('/')).nativeElement as HTMLElement;

    expect(hrefs(root, '.main-nav a.active')).toEqual(['/']);
  });

  it('shows the shop details', async () => {
    const root = (await appAt('/kontakti')).nativeElement as HTMLElement;

    expect(root.querySelector('main a[href="tel:+37128803119"]')).not.toBeNull();
    expect(root.querySelector('main a[href="mailto:info@xenonfix.lv"]')).not.toBeNull();
    expect(root.querySelector('footer')?.textContent).toContain('40203498222');
  });

  it('switches language through the switcher link', async () => {
    const fixture = await appAt('/');
    const root = fixture.nativeElement as HTMLElement;
    expect(hrefs(root)).toEqual(['/', '/ru', '/en']);

    root.querySelector<HTMLElement>('a[hreflang="en"]')?.click();
    await fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/en');
    expect(root.querySelector('h1')?.textContent).toBe('Car headlight repair');
    expect(hrefs(root)).toEqual(['/', '/ru', '/en']);
  });

  it('keeps the page when the language changes', async () => {
    const root = (await appAt('/ru/nav-tadas-lapas')).nativeElement as HTMLElement;

    expect(hrefs(root)).toEqual(['/nav-tadas-lapas', '/ru/nav-tadas-lapas', '/en/nav-tadas-lapas']);
    expect(root.querySelector('h1')?.textContent).toBe('Страница не найдена');
  });

  it('writes canonical and language links without piling them up', async () => {
    await appAt('/');
    await TestBed.inject(Router).navigateByUrl('/ru/par-mums');

    const link = (selector: string) =>
      [...document.head.querySelectorAll(selector)].map((l) => l.getAttribute('href'));
    expect(link('link[rel="canonical"]')).toEqual(['https://xenonfix.lv/ru/par-mums']);
    expect(link('link[hreflang="lv"]')).toEqual(['https://xenonfix.lv/par-mums']);
    expect(link('link[hreflang="ru"]')).toEqual(['https://xenonfix.lv/ru/par-mums']);
    expect(link('link[hreflang="en"]')).toEqual(['https://xenonfix.lv/en/par-mums']);
    expect(link('link[hreflang="x-default"]')).toEqual(['https://xenonfix.lv/par-mums']);
    expect(document.title).toBe('О нас - XenonFix');
  });

  it('shows products on the store page and links to them in the current language', async () => {
    store.items = [SUMMARY, { ...SUMMARY, slug: 'bmw', name: 'BMW', salePrice: null, available: false }];

    const root = (await appAt('/en/store')).nativeElement as HTMLElement;

    expect(hrefs(root, 'app-product-card a')).toEqual(['/en/product/audi-a4', '/en/product/bmw']);
    expect(root.querySelector('app-product-card')?.textContent).toContain('In stock');
    expect(root.querySelectorAll('app-product-card')[1].textContent).toContain('Out of stock');
    expect(root.querySelector('app-product-card s')?.textContent).toContain('450');
  });

  it('says so when the store has no products', async () => {
    const root = (await appAt('/store')).nativeElement as HTMLElement;

    expect(root.querySelector('main')?.textContent).toContain('Šobrīd veikalā nav produktu');
  });

  it('shows the newest products on the home page only when there are some', async () => {
    expect(((await appAt('/')).nativeElement as HTMLElement).querySelector('app-product-card')).toBeNull();
  });

  it('shows the products section on the home page', async () => {
    store.items = [SUMMARY];

    const root = (await appAt('/')).nativeElement as HTMLElement;

    expect(hrefs(root, 'main app-product-card a')).toEqual(['/product/audi-a4']);
  });

  it('shows a product with its own title and description', async () => {
    const root = (await appAt('/ru/product/audi-a4')).nativeElement as HTMLElement;

    expect(root.querySelector('h1')?.textContent).toBe('Audi A4 lukturis');
    expect(root.querySelector('app-reservation-form')).not.toBeNull();
    expect(root.textContent).toContain('8K0941003');
    expect(document.title).toBe('Audi A4 lukturis - XenonFix');
    expect(document.querySelector('meta[name="description"]')?.getAttribute('content')).toBe(
      'Kreisais lukturis. Labā stāvoklī.',
    );
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://xenonfix.lv/ru/product/audi-a4',
    );
  });

  it('offers no form when the product is out of stock', async () => {
    store.product = { ...PRODUCT, available: false };

    const root = (await appAt('/product/audi-a4')).nativeElement as HTMLElement;

    expect(root.querySelector('app-reservation-form')).toBeNull();
    expect(root.querySelector('main')?.textContent).toContain('Nav pieejams');
    expect(root.querySelector('main a[href="tel:+37128803119"]')).not.toBeNull();
  });

  it('shows the not found page for an unknown product', async () => {
    const root = (await appAt('/product/nav-tada')).nativeElement as HTMLElement;

    expect(root.querySelector('h1')?.textContent).toBe('Lapa nav atrasta');
    expect(document.title).toBe('Lapa nav atrasta - XenonFix');
  });

  it('does not call a product missing when the backend has a problem', async () => {
    const api = TestBed.inject(StoreApi);
    api.get = () => throwError(() => new HttpErrorResponse({ status: 500 }));

    const root = (await appAt('/product/audi-a4')).nativeElement as HTMLElement;

    expect(root.querySelector('h1')?.textContent).toBe('Veikals');
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('nav pieejams');
    expect(document.title).toBe('Veikals - XenonFix');
    api.get = STUB_API.get!;
  });

  it('shows the pages as edited, with Latvian where a translation is empty', async () => {
    const about = pages.pages.about as { heading: Record<string, string>; points: Record<string, string>[] };
    about.heading = { lv: 'Par mums jauns', ru: '', en: 'About us, new' };
    about.points.push({ lv: 'Piektais punkts', ru: 'Пятый пункт', en: '' });

    const ru = (await appAt('/ru/par-mums')).nativeElement as HTMLElement;
    expect(ru.querySelector('h1')?.textContent).toBe('Par mums jauns');
    expect(ru.querySelectorAll('.promises li')).toHaveLength(5);
    expect(ru.querySelector('.promises li:last-child')?.textContent).toContain('Пятый пункт');

    const en = (await appAt('/en/par-mums')).nativeElement as HTMLElement;
    expect(en.querySelector('h1')?.textContent).toBe('About us, new');
    expect(en.querySelector('.promises li:last-child')?.textContent).toContain('Piektais punkts');
  });

  it('uses the edited title and description of a page', async () => {
    (pages.pages.works as { seo: { title: Record<string, string> } }).seo.title = {
      lv: 'Darbi - XenonFix',
      ru: '',
      en: '',
    };

    await appAt('/musu-darbi');

    expect(document.title).toBe('Darbi - XenonFix');
  });

  it('shows the edited contact details in the header, footer and pages', async () => {
    Object.assign(pages.pages['site-info'] as object, {
      phone: '+371 200 00 999',
      street: 'Jaunā iela 1',
      hours: [{ day: { lv: 'Katru dienu', ru: '', en: 'Every day' }, time: { lv: '8:00–20:00', ru: '', en: '' } }],
    });

    const root = (await appAt('/en/kontakti')).nativeElement as HTMLElement;

    expect(root.querySelector('.header-phone')?.getAttribute('href')).toBe('tel:+37120000999');
    expect(root.querySelector('main a[href="tel:+37120000999"]')?.textContent).toBe('+371 200 00 999');
    expect(root.querySelector('footer')?.textContent).toContain('Jaunā iela 1');
    expect(root.querySelector('main dl')?.textContent).toContain('Every day');
    expect(root.querySelector('main dl')?.textContent).toContain('8:00–20:00');
    expect(root.querySelector('main a[href*="google.com/maps"]')?.getAttribute('href')).toContain('Jaun%C4%81%20iela%201');
  });

  it('shows the gallery as edited, separate from the home page tiles', async () => {
    const works = pages.pages.works as { photos: unknown[] };
    works.photos = [works.photos[2], works.photos[0]];

    const root = (await appAt('/musu-darbi')).nativeElement as HTMLElement;

    expect([...root.querySelectorAll('.gallery img')].map((img) => img.getAttribute('src'))).toEqual([
      '/images/service-restoration.jpg',
      '/images/service-glass.jpg',
    ]);
    const home = (await appAt('/')).nativeElement as HTMLElement;
    expect(home.querySelectorAll('.bento .tile:not(.tile-all)')).toHaveLength(6);
  });

  it('says a page is unavailable when the backend cannot be reached', async () => {
    pages.failing = true;

    const root = (await appAt('/par-mums')).nativeElement as HTMLElement;

    expect(root.querySelector('main')?.textContent).toContain('Lapa šobrīd nav pieejama');
    expect(root.querySelector('main a[href="tel:+37128803119"]')).not.toBeNull();
    expect(document.title).toBe('XenonFix - auto priekšējo lukturu remonts Rīgā');
  });

  it('links to the privacy policy from the footer', async () => {
    const root = (await appAt('/en')).nativeElement as HTMLElement;

    expect(hrefs(root, '.copyright a')).toEqual(['/en/privatuma-politika']);
  });
});
