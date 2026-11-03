import { localizedPath, pagePath, urlLanguage } from './languages';

describe('language URLs', () => {
  it('finds the language of a URL', () => {
    expect(urlLanguage('/')).toBe('lv');
    expect(urlLanguage('')).toBe('lv');
    expect(urlLanguage('/par-mums')).toBe('lv');
    expect(urlLanguage('/ru')).toBe('ru');
    expect(urlLanguage('/en/par-mums?a=1')).toBe('en');
    expect(urlLanguage('/russian')).toBe('lv');
  });

  it('builds the URL of a page in a language', () => {
    expect(localizedPath('lv', '/')).toBe('/');
    expect(localizedPath('lv', '/par-mums')).toBe('/par-mums');
    expect(localizedPath('ru', '/')).toBe('/ru');
    expect(localizedPath('en', '/par-mums')).toBe('/en/par-mums');
  });

  it('finds the page in a URL', () => {
    expect(pagePath('/')).toBe('/');
    expect(pagePath('/par-mums')).toBe('/par-mums');
    expect(pagePath('/ru')).toBe('/');
    expect(pagePath('/en/par-mums?a=1#b')).toBe('/par-mums');
  });

  it('does not mistake a page name for a language', () => {
    expect(pagePath('/russian')).toBe('/russian');
    expect(pagePath('/english/page')).toBe('/english/page');
  });
});
