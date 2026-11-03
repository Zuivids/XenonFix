import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, TitleStrategy } from '@angular/router';
import { App } from './app';
import { PAGE_PATHS, routes } from './app.routes';
import { SeoTitleStrategy } from './seo/seo-title-strategy';

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
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), { provide: TitleStrategy, useClass: SeoTitleStrategy }],
    });
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
    expect(document.title).toBe('О нас — XenonFix');
  });
});
