import { vi } from 'vitest';
import { oldAddressAnswer } from './old-addresses';
import { SitemapProducts, sitemapXml } from './sitemap';

describe('oldAddressAnswer', () => {
  it.each([
    ['/kontakti/', '/kontakti'],
    ['/par-mums/', '/par-mums'],
    ['/ru/par-mums/', '/ru/par-mums'],
    ['/store/?page=2', '/store?page=2'],
    ['/product/audi-a6-c7-4g-2015-2017-prieksejais-kreisais-lukturis/', '/product/audi-a6-c7-4g-2015-2017-prieksejais-kreisais-lukturis'],
    ['/apmaksas-nosacijumi-2/', '/apmaksas-nosacijumi'],
    ['/piegades-nosacijumi-2/', '/piegades-nosacijumi'],
    ['/privatuma-politika-2/', '/privatuma-politika'],
    ['/apmaksas-nosacijumi-2', '/apmaksas-nosacijumi'],
    ['/shop/', '/store'],
    ['/cart/', '/store'],
    ['/checkout/', '/store'],
    ['/product-category/lukturi/', '/store'],
    ['/bildes/', '/musu-darbi'],
    ['/2023/02/12/auto-lukturu-remonts/', '/musu-pakalpojumi'],
    ['/vakances/', '/'],
    ['/my-account-2/', '/'],
    ['/category/uncategorized/', '/'],
  ])('sends %s to %s', (from, to) => {
    expect(oldAddressAnswer(from)).toEqual({ status: 301, location: to });
  });

  it.each(['/feed/', '/comments/feed/', '/wp-login.php', '/wp-admin/', '/wp-content/uploads/2024/01/a.jpg', '/xmlrpc.php', '/?p=12', '/?page_id=5'])(
    'says %s is gone',
    (url) => {
      expect(oldAddressAnswer(url)).toEqual({ status: 410 });
    },
  );

  it.each(['/', '/kontakti', '/ru', '/store?page=2', '/product/audi-a4', '/admin/reservations/7', '/?utm_source=google'])(
    'leaves %s alone',
    (url) => {
      expect(oldAddressAnswer(url)).toBeNull();
    },
  );

  it('never redirects to another site', () => {
    expect(oldAddressAnswer('//example.com/')).toEqual({ status: 301, location: '/example.com' });
    expect(oldAddressAnswer('/\\example.com/')).toEqual({ status: 301, location: '/example.com' });
    expect(oldAddressAnswer('///')).toEqual({ status: 301, location: '/' });
  });
});

describe('sitemapXml', () => {
  const xml = sitemapXml([{ slug: 'audi-a4', updatedAt: '2026-11-05T10:00:00.123Z' }]);

  it('lists every page and product in every language', () => {
    for (const loc of [
      'https://xenonfix.lv/',
      'https://xenonfix.lv/ru',
      'https://xenonfix.lv/en',
      'https://xenonfix.lv/par-mums',
      'https://xenonfix.lv/ru/store',
      'https://xenonfix.lv/en/privatuma-politika',
      'https://xenonfix.lv/product/audi-a4',
      'https://xenonfix.lv/en/product/audi-a4',
    ]) {
      expect(xml).toContain(`<loc>${loc}</loc>`);
    }
    expect(xml.match(/<url>/g)).toHaveLength((1 + 9 + 1) * 3);
  });

  it('links each page to its other languages, as the pages do', () => {
    const product = xml.split('\n').find((line) => line.includes('<loc>https://xenonfix.lv/ru/product/audi-a4</loc>'))!;
    expect(product).toContain('<lastmod>2026-11-05</lastmod>');
    expect(product).toContain('hreflang="lv" href="https://xenonfix.lv/product/audi-a4"');
    expect(product).toContain('hreflang="en" href="https://xenonfix.lv/en/product/audi-a4"');
    expect(product).toContain('hreflang="x-default" href="https://xenonfix.lv/product/audi-a4"');
    expect(xml).toMatch(/^<\?xml version="1.0" encoding="UTF-8"\?>\n<urlset /);
  });

  it('escapes what XML needs', () => {
    expect(sitemapXml([{ slug: 'a&b', updatedAt: '2026-01-01T00:00:00Z' }])).toContain('/product/a%26b');
  });
});

describe('SitemapProducts', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('asks the backend again only after a while, and uses the last list when it fails', async () => {
    let time = 0;
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([{ slug: 'a', updatedAt: '2026-01-01T00:00:00Z' }])))
      .mockRejectedValueOnce(new Error('down'));
    vi.stubGlobal('fetch', fetch);
    const products = new SitemapProducts('http://backend:8090', 1000, () => time);

    expect(await products.get()).toHaveLength(1);
    expect(await products.get()).toHaveLength(1);
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch.mock.calls[0][0]).toBe('http://backend:8090/api/store/sitemap');

    time = 2000;
    expect(await products.get()).toHaveLength(1);
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('fails when the backend never answered', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 503 })));

    await expect(new SitemapProducts('http://backend:8090').get()).rejects.toThrow('503');
  });
});
