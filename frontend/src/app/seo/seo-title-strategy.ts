import { DOCUMENT, inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { I18n } from '../i18n/i18n';
import { DEFAULT_LANGUAGE, Language, LANGUAGES, localizedPath, pagePath } from '../i18n/languages';
import type { MessageKey } from '../i18n/messages.lv';

// Fixed rather than taken from the request: a test server must not call itself canonical
export const SITE_URL = 'https://xenonfix.lv';

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

    this.document.documentElement.setAttribute('lang', this.i18n.language());
    this.title.setTitle(this.i18n.t(route.title as MessageKey));
    this.meta.updateTag({
      name: 'description',
      content: this.i18n.t(route.data['description'] as MessageKey),
    });
    this.updateLinks(pagePath(snapshot.url));
  }

  private updateLinks(page: string): void {
    const head = this.document.head;
    head
      .querySelectorAll('link[rel="canonical"], link[rel="alternate"][hreflang]')
      .forEach((link) => link.remove());

    const url = (language: Language) => SITE_URL + localizedPath(language, page);
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
