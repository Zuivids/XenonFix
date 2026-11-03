import { inject } from '@angular/core';
import { Route, Routes } from '@angular/router';
import { About } from './about/about';
import { Contact } from './contact/contact';
import { DocumentPage } from './document-page/document-page';
import { Home } from './home/home';
import { I18n } from './i18n/i18n';
import { Language, languagePrefix } from './i18n/languages';
import { NotFound } from './not-found/not-found';
import { Services } from './services/services';
import { SiteLayout } from './site-layout/site-layout';
import { Works } from './works/works';

/**
 * The public pages, the same under each language's prefix. The paths are the old site's, so
 * existing links keep working. Route titles and descriptions are message keys.
 */
const PAGES: Route[] = [
  { path: 'par-mums', component: About, title: 'about.title', data: { description: 'about.description' } },
  {
    path: 'musu-pakalpojumi',
    component: Services,
    title: 'services.title',
    data: { description: 'services.description' },
  },
  { path: 'musu-darbi', component: Works, title: 'works.title', data: { description: 'works.description' } },
  { path: 'kontakti', component: Contact, title: 'contact.title', data: { description: 'contact.description' } },
  {
    path: 'apmaksas-nosacijumi',
    component: DocumentPage,
    title: 'payment.title',
    data: { description: 'payment.description', heading: 'payment.heading', document: 'payment' },
  },
  {
    path: 'piegades-nosacijumi',
    component: DocumentPage,
    title: 'delivery.title',
    data: { description: 'delivery.description', heading: 'delivery.heading', document: 'delivery' },
  },
  {
    path: 'garantijas-nosacijumi',
    component: DocumentPage,
    title: 'warranty.title',
    data: { description: 'warranty.description', heading: 'warranty.heading', document: 'warranty' },
  },
];

export const PAGE_PATHS = PAGES.map((page) => page.path!);

function languageRoutes(language: Language): Route {
  return {
    path: languagePrefix(language),
    component: SiteLayout,
    resolve: { messages: () => inject(I18n).use(language) },
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: Home,
        title: 'home.title',
        data: { description: 'home.description' },
      },
      ...PAGES,
      {
        path: '**',
        component: NotFound,
        title: 'notFound.title',
        data: { description: 'home.description' },
      },
    ],
  };
}

export const routes: Routes = [
  { path: 'lv', pathMatch: 'full', redirectTo: '/' },
  {
    path: 'admin',
    data: { admin: true },
    loadChildren: () => import('./admin/admin.routes').then((m) => m.adminRoutes),
  },
  languageRoutes('ru'),
  languageRoutes('en'),
  // Last: no prefix, so it also takes every other URL
  languageRoutes('lv'),
];
