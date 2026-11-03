import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, TitleStrategy } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { SeoTitleStrategy } from './seo/seo-title-strategy';

async function appAt(url: string) {
  const fixture = TestBed.createComponent(App);
  await TestBed.inject(Router).navigateByUrl(url);
  await fixture.whenStable();
  return fixture;
}

function hrefs(root: HTMLElement) {
  return [...root.querySelectorAll('.language-switcher a')].map((a) => a.getAttribute('href'));
}

describe('App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), { provide: TitleStrategy, useClass: SeoTitleStrategy }],
    });
  });

  it.each([
    ['/', 'lv', 'Lukturu remonts un pārdošana'],
    ['/ru', 'ru', 'Ремонт и продажа фар'],
    ['/en', 'en', 'Headlight repair and sales'],
  ])('renders %s in its language', async (url, language, heading) => {
    const root = (await appAt(url)).nativeElement as HTMLElement;

    expect(root.querySelector('h1')?.textContent).toBe(heading);
    expect(document.documentElement.getAttribute('lang')).toBe(language);
    expect(root.querySelector('[aria-current]')?.getAttribute('hreflang')).toBe(language);
  });

  it('switches language through the switcher link', async () => {
    const fixture = await appAt('/');
    const root = fixture.nativeElement as HTMLElement;
    expect(hrefs(root)).toEqual(['/', '/ru', '/en']);

    root.querySelector<HTMLElement>('a[hreflang="en"]')?.click();
    await fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/en');
    expect(root.querySelector('h1')?.textContent).toBe('Headlight repair and sales');
    expect(hrefs(root)).toEqual(['/', '/ru', '/en']);
  });

  it('keeps the page when the language changes', async () => {
    const root = (await appAt('/ru/nav-tadas-lapas')).nativeElement as HTMLElement;

    expect(hrefs(root)).toEqual(['/nav-tadas-lapas', '/ru/nav-tadas-lapas', '/en/nav-tadas-lapas']);
    expect(root.querySelector('h1')?.textContent).toBe('Страница не найдена');
  });

  it('writes canonical and language links without piling them up', async () => {
    await appAt('/');
    await TestBed.inject(Router).navigateByUrl('/ru');

    const link = (selector: string) =>
      [...document.head.querySelectorAll(selector)].map((l) => l.getAttribute('href'));
    expect(link('link[rel="canonical"]')).toEqual(['https://xenonfix.lv/ru']);
    expect(link('link[hreflang="lv"]')).toEqual(['https://xenonfix.lv/']);
    expect(link('link[hreflang="ru"]')).toEqual(['https://xenonfix.lv/ru']);
    expect(link('link[hreflang="en"]')).toEqual(['https://xenonfix.lv/en']);
    expect(link('link[hreflang="x-default"]')).toEqual(['https://xenonfix.lv/']);
    expect(document.title).toBe('XenonFix — ремонт и продажа фар');
  });
});
