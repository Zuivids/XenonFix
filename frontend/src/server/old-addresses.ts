/**
 * Addresses of the old WordPress site, from its sitemap on 2026-11-06, and where they lead now.
 * Pages with the same name need no entry: only their trailing "/" goes.
 */
const MOVED: Record<string, string> = {
  '/apmaksas-nosacijumi-2': '/apmaksas-nosacijumi',
  '/piegades-nosacijumi-2': '/piegades-nosacijumi',
  '/privatuma-politika-2': '/privatuma-politika',
  '/shop': '/store',
  '/cart': '/store',
  '/checkout': '/store',
  '/bildes': '/musu-darbi',
  '/2023/02/12/auto-lukturu-remonts': '/musu-pakalpojumi',
  '/vakances': '/',
  '/my-account-2': '/',
  '/category/uncategorized': '/',
};

/** Whole sections of the old site that now have one page. */
const MOVED_SECTIONS: [prefix: string, target: string][] = [['/product-category/', '/store']];

/** WordPress's own addresses, gone for good, so search engines drop them rather than retry. */
const GONE = [/^\/wp-/, /^\/xmlrpc\.php$/, /^\/feed$/, /^\/comments\/feed$/, /\/feed$/];

/** WordPress's numbered addresses, such as /?p=12. */
const GONE_QUERY = /(?:^|&)(?:p|page_id|attachment_id)=/;

export type OldAddressAnswer = { status: 301; location: string } | { status: 410 } | null;

/**
 * What to answer for an old address, or null to show the page as usual.
 *
 * @param url the path and query as the visitor sent them
 */
export function oldAddressAnswer(url: string): OldAddressAnswer {
  const queryStart = url.indexOf('?');
  const rawPath = queryStart < 0 ? url : url.slice(0, queryStart);
  const query = queryStart < 0 ? '' : url.slice(queryStart);
  // One leading slash only: "//example.com/" must never become a redirect to another site
  const path = rawPath.replace(/^[/\\]+/, '/').replace(/\/+$/, '') || '/';

  if (GONE.some((pattern) => pattern.test(path)) || (path === '/' && GONE_QUERY.test(query.slice(1)))) {
    return { status: 410 };
  }
  const moved = MOVED[path] ?? MOVED_SECTIONS.find(([prefix]) => `${path}/`.startsWith(prefix))?.[1];
  if (moved) {
    return { status: 301, location: moved };
  }
  if (path !== rawPath) {
    return { status: 301, location: path + query };
  }
  return null;
}
