import { Location } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideClientHydration, withHttpTransferCacheOptions } from '@angular/platform-browser';
import { provideRouter, TitleStrategy, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { sessionExpiredInterceptor } from './admin/session-expired.interceptor';
import { routes } from './app.routes';
import { I18n } from './i18n/i18n';
import { urlLanguage } from './i18n/languages';
import { SeoTitleStrategy } from './seo/seo-title-strategy';
import { backendUrlInterceptor } from './store/backend-url.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ scrollPositionRestoration: 'enabled' })),
    { provide: TitleStrategy, useClass: SeoTitleStrategy },
    // The browser reuses what the server fetched for this page. Spring Security marks API answers
    // as not cacheable, which is right for browser caches and does not apply to this one handover.
    provideClientHydration(withHttpTransferCacheOptions({ includeNonCacheableRequests: true })),
    // withFetch: Node has no XMLHttpRequest, so server rendering needs the Fetch-based backend
    provideHttpClient(withFetch(), withInterceptors([backendUrlInterceptor, sessionExpiredInterceptor])),
    // The first render must already have the language, so it matches the server's HTML.
    // Later navigations load theirs in the route resolver.
    provideAppInitializer(() => inject(I18n).use(urlLanguage(inject(Location).path()))),
  ],
};
