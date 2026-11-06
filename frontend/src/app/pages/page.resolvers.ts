import { inject, RESPONSE_INIT } from '@angular/core';
import { ResolveFn } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { urlLanguage } from '../i18n/languages';
import { PageApi, PageName, SiteInfoContent } from './page-api';
import { SiteInfo } from './site-info';

/**
 * A page's content, resolved before rendering so the server's HTML has it. When the backend
 * cannot be reached the page gets null and says so, and search engines get 503 (come back
 * later), as for the store.
 */
export function pageResolver<T>(page: PageName): ResolveFn<T | null> {
  return (_route, state) => {
    const response = inject(RESPONSE_INIT, { optional: true }) as { status?: number } | null;
    return inject(PageApi)
      .get<T>(page, urlLanguage(state.url))
      .pipe(
        catchError(() => {
          if (response) {
            response.status = 503;
          }
          return of(null);
        }),
      );
  };
}

/** The shop's details for the header, footer and pages, loaded with the language. */
export const siteInfoResolver: ResolveFn<boolean> = (_route, state) => {
  const siteInfo = inject(SiteInfo);
  return inject(PageApi)
    .get<SiteInfoContent>('site-info', urlLanguage(state.url))
    .pipe(
      map((content) => {
        siteInfo.set(content);
        return true;
      }),
      // The built-in details stay, so the header and footer still work
      catchError(() => of(false)),
    );
};
