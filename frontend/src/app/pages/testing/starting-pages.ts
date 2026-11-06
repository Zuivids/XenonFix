// For tests only: the pages' starting content, the same files the backend loads, answered the
// way the backend answers them (one language, Latvian where a translation is empty).
import { Observable, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import about from '../../../../../backend/src/main/resources/pages/about.json';
import contact from '../../../../../backend/src/main/resources/pages/contact.json';
import home from '../../../../../backend/src/main/resources/pages/home.json';
import services from '../../../../../backend/src/main/resources/pages/services.json';
import siteInfo from '../../../../../backend/src/main/resources/pages/site-info.json';
import works from '../../../../../backend/src/main/resources/pages/works.json';
import type { Language } from '../../i18n/languages';
import type { PageApi, PageName } from '../page-api';

export const STARTING_PAGES: Record<PageName, unknown> = {
  home,
  about,
  services,
  works,
  contact,
  'site-info': siteInfo,
};

/** As PageDocuments.inLanguage does in the backend. */
export function inLanguage(value: unknown, language: Language): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => inLanguage(item, language));
  }
  if (value && typeof value === 'object') {
    const object = value as Record<string, unknown>;
    const keys = Object.keys(object).sort().join();
    if (keys === 'en,lv,ru') {
      const text = object[language];
      return typeof text === 'string' && text.trim() ? text : object['lv'];
    }
    return Object.fromEntries(Object.entries(object).map(([key, item]) => [key, inLanguage(item, language)]));
  }
  return value;
}

/** A PageApi for tests; `pages` can be changed, and `failing` makes it answer like a backend that is down. */
export function stubPageApi(pages: Record<PageName, unknown> = structuredClone(STARTING_PAGES)) {
  const stub = {
    pages,
    failing: false,
    get<T>(page: PageName, language: Language): Observable<T> {
      if (stub.failing) {
        return throwError(() => new HttpErrorResponse({ status: 0 }));
      }
      const content = inLanguage(stub.pages[page], language) as Record<string, unknown>;
      if (page === 'home') {
        content['serviceCount'] = (stub.pages.services as { services: unknown[] }).services.length;
      }
      return of(content as T);
    },
  };
  return stub as typeof stub & Pick<PageApi, 'get'>;
}
