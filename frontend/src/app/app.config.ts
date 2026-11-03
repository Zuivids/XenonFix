import { Location } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideClientHydration } from '@angular/platform-browser';
import { provideRouter, TitleStrategy, withInMemoryScrolling } from '@angular/router';
import { sessionExpiredInterceptor } from './admin/session-expired.interceptor';
import { routes } from './app.routes';
import { I18n } from './i18n/i18n';
import { urlLanguage } from './i18n/languages';
import { SeoTitleStrategy } from './seo/seo-title-strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'enabled' })),
    { provide: TitleStrategy, useClass: SeoTitleStrategy },
    provideClientHydration(),
    // withFetch: Node has no XMLHttpRequest, so server rendering needs the Fetch-based backend
    provideHttpClient(withFetch(), withInterceptors([sessionExpiredInterceptor])),
    // The first render must already have the language, so it matches the server's HTML.
    // Later navigations load theirs in the route resolver.
    provideAppInitializer(() => inject(I18n).use(urlLanguage(inject(Location).path()))),
  ],
};
