import { PAGE_PATHS } from '../app/app.routes';
import { DEFAULT_LANGUAGE, LANGUAGES, localizedPath } from '../app/i18n/languages';
import { SITE_URL } from '../app/seo/seo-title-strategy';

/** As GET /api/store/sitemap gives them. */
export interface SitemapProduct {
  slug: string;
  updatedAt: string;
}

/**
 * Every public page in every language, each with links to its other languages, the same ones
 * the page itself has (SeoTitleStrategy).
 */
export function sitemapXml(products: SitemapProduct[]): string {
  const pages: { path: string; lastModified?: string }[] = [
    { path: '/' },
    ...PAGE_PATHS.map((path) => ({ path: `/${path}` })),
    ...products.map((product) => ({
      path: `/product/${encodeURIComponent(product.slug)}`,
      lastModified: product.updatedAt.slice(0, 10),
    })),
  ];
  const urls = pages.flatMap(({ path, lastModified }) => {
    const alternates = [
      ...LANGUAGES.map((language) => alternate(language, url(language, path))),
      alternate('x-default', url(DEFAULT_LANGUAGE, path)),
    ].join('');
    return LANGUAGES.map(
      (language) =>
        `<url><loc>${url(language, path)}</loc>` +
        (lastModified ? `<lastmod>${lastModified}</lastmod>` : '') +
        alternates +
        '</url>',
    );
  });
  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n' +
    urls.join('\n') +
    '\n</urlset>\n'
  );
}

function url(language: (typeof LANGUAGES)[number], path: string): string {
  return escapeXml(SITE_URL + localizedPath(language, path));
}

function alternate(hreflang: string, href: string): string {
  return `<xhtml:link rel="alternate" hreflang="${hreflang}" href="${href}"/>`;
}

function escapeXml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/**
 * The products for the sitemap, asked from the backend at most every few minutes. When the
 * backend does not answer, the last list is used; with none, the caller gets an error, so a
 * search engine tries again later rather than seeing a sitemap without products.
 */
export class SitemapProducts {
  private cached: { products: SitemapProduct[]; until: number } | null = null;

  constructor(
    private readonly backendUrl: string,
    private readonly maxAgeMs = 5 * 60_000,
    private readonly now = () => Date.now(),
  ) {}

  async get(): Promise<SitemapProduct[]> {
    if (this.cached && this.cached.until > this.now()) {
      return this.cached.products;
    }
    try {
      const response = await fetch(`${this.backendUrl}/api/store/sitemap`, { signal: AbortSignal.timeout(4000) });
      if (!response.ok) {
        throw new Error(`Backend answered ${response.status}`);
      }
      const products = (await response.json()) as SitemapProduct[];
      this.cached = { products, until: this.now() + this.maxAgeMs };
      return products;
    } catch (error) {
      if (this.cached) {
        return this.cached.products;
      }
      throw error;
    }
  }
}
