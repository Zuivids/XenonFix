import { DOCUMENT, inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { I18n } from '../i18n/i18n';
import { DEFAULT_LANGUAGE, Language, LANGUAGES, localizedPath, pagePath } from '../i18n/languages';
import type { MessageKey } from '../i18n/messages.lv';
import type { ProductLookup } from '../store/store.resolvers';

// Fixed rather than taken from the request: a test server must not call itself canonical
export const SITE_URL = 'https://xenonfix.lv';

/** Search engines show about this much of a description. */
const DESCRIPTION_LENGTH = 155;

/** The start of the product's description on one line, or the general text if it has none. */
function productDescription(description: string | null, fallback: string): string {
  const text = description?.replace(/\s+/g, ' ').trim();
  if (!text) {
    return fallback;
  }
  return text.length > DESCRIPTION_LENGTH ? `${text.slice(0, DESCRIPTION_LENGTH).trimEnd()}…` : text;
}

/**
 * Runs after every navigation, on the server too, and writes what search engines read for
 * the page: language, title, description, canonical URL and links to the other languages.
 * Route titles and the `description` route data are message keys.
 */
@Injectable()
export class SeoTitleStrategy extends TitleStrategy {
  private readonly document = inject(DOCUMENT);
  private readonly i18n = inject(I18n);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    let route = snapshot.root;
    while (route.firstChild) {
      route = route.firstChild;
    }

    if (route.data['admin']) {
      // Latvian only and not for search engines; its route titles are plain text
      this.title.setTitle(route.title ?? 'XenonFix');
      this.meta.removeTag('name="description"');
      this.meta.updateTag({ name: 'robots', content: 'noindex' });
      this.removeLinks();
      return;
    }

    this.meta.removeTag('name="robots"');
    this.document.documentElement.setAttribute('lang', this.i18n.language());
    const lookup = route.data['lookup'] as ProductLookup | undefined;
    const product = lookup?.state === 'found' ? lookup.product : null;
    // A backend problem does not mean the product is gone, so it is not called "not found"
    const titleKey = lookup?.state === 'unavailable' ? 'store.title' : (route.title as MessageKey);
    this.title.setTitle(product ? `${product.name} - XenonFix` : this.i18n.t(titleKey));
    this.meta.updateTag({
      name: 'description',
      content: product
        ? productDescription(product.description, this.i18n.t('store.description'))
        : this.i18n.t(route.data['description'] as MessageKey),
    });
    this.updateLinks(pagePath(snapshot.url));
  }

  private removeLinks(): void {
    this.document.head
      .querySelectorAll('link[rel="canonical"], link[rel="alternate"][hreflang]')
      .forEach((link) => link.remove());
  }

  private updateLinks(page: string): void {
    this.removeLinks();

    const url =(language: Language) => SITE_URL + localizedPath(language, page);
    this.addLink({ rel: 'canonical', href: url(this.i18n.language()) });
    for (const language of LANGUAGES) {
      this.addLink({ rel: 'alternate', hreflang: language, href: url(language) });
    }
    this.addLink({ rel: 'alternate', hreflang: 'x-default', href: url(DEFAULT_LANGUAGE) });
  }

  private addLink(attributes: Record<string, string>): void {
    const link = this.document.createElement('link');
    Object.entries(attributes).forEach(([name, value]) => link.setAttribute(name, value));
    this.document.head.appendChild(link);
  }
}
