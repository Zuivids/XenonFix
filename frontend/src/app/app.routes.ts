import { inject, Type } from '@angular/core';
import { Route, Routes } from '@angular/router';
import { About } from './about/about';
import { Contact } from './contact/contact';
import { DocumentPage } from './document-page/document-page';
import { Home } from './home/home';
import { I18n } from './i18n/i18n';
import { Language, languagePrefix } from './i18n/languages';
import { NotFound } from './not-found/not-found';
import { PageName } from './pages/page-api';
import { pageResolver, siteInfoResolver } from './pages/page.resolvers';
import { Services } from './services/services';
import { SiteLayout } from './site-layout/site-layout';
import { ProductPage } from './store/product-page';
import { StoreList } from './store/store-list';
import { featuredProductsResolver, productResolver, storePageResolver } from './store/store.resolvers';
import { Works } from './works/works';

/**
 * A page whose content is edited in the admin panel. Its title and description come from that
 * content (SeoTitleStrategy); the general ones are for when it cannot be loaded.
 */
function editable(path: string, component: Type<unknown>, page: PageName): Route {
  return {
    path,
    component,
    title: 'home.title',
    data: { description: 'home.description' },
    resolve: { page: pageResolver(page) },
  };
}

/**
 * The public pages, the same under each language's prefix. The paths are the old site's, so
 * existing links keep working. Route titles and descriptions are message keys.
 */
const PAGES: Route[] = [
  editable('par-mums', About, 'about'),
  editable('musu-pakalpojumi', Services, 'services'),
  editable('musu-darbi', Works, 'works'),
  editable('kontakti', Contact, 'contact'),
  {
    path: 'store',
    component: StoreList,
    title: 'store.title',
    data: { description: 'store.description' },
    resolve: { result: storePageResolver },
    runGuardsAndResolvers: 'paramsOrQueryParamsChange',
  },
  {
    path: 'privatuma-politika',
    component: DocumentPage,
    title: 'privacy.title',
    data: {
      description: 'privacy.description',
      heading: 'privacy.heading',
      eyebrow: 'privacy.eyebrow',
      document: 'privacy',
    },
  },
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

/**
 * Apart from PAGES, because the address has a parameter. The title and description are for a
 * product that is not found; SeoTitleStrategy uses the product's own when there is one.
 */
export const PRODUCT_PATH = 'product/:slug';

const PRODUCT_ROUTE: Route = {
  path: PRODUCT_PATH,
  component: ProductPage,
  title: 'notFound.title',
  data: { description: 'home.description' },
  resolve: { lookup: productResolver },
};

function languageRoutes(language: Language): Route {
  return {
    path: languagePrefix(language),
    component: SiteLayout,
    resolve: { messages: () => inject(I18n).use(language), siteInfo: siteInfoResolver },
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: Home,
        title: 'home.title',
        data: { description: 'home.description' },
        resolve: { featured: featuredProductsResolver, page: pageResolver('home') },
      },
      ...PAGES,
      PRODUCT_ROUTE,
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
