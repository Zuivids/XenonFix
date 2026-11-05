import { HttpErrorResponse } from '@angular/common/http';
import { inject, RESPONSE_INIT } from '@angular/core';
import { ResolveFn } from '@angular/router';
import { catchError, map, of, tap } from 'rxjs';
import { urlLanguage } from '../i18n/languages';
import { StoreApi, StoreProduct, StoreProductPage, StoreProductSummary } from './store-api';

const HOME_PRODUCTS = 4;

/** The newest products for the home page, which simply goes without them if there is a problem. */
export const featuredProductsResolver: ResolveFn<StoreProductSummary[]> = (_route, state) =>
  inject(StoreApi)
    .list(urlLanguage(state.url), 0, HOME_PRODUCTS)
    .pipe(
      map((page) => page.items),
      catchError(() => of([])),
    );

/** A page of the store, or null when the backend could not be reached. */
export const storePageResolver: ResolveFn<StoreProductPage | null> = (route, state) => {
  const page = Math.max(0, (Number(route.queryParamMap.get('page')) || 1) - 1);
  return inject(StoreApi)
    .list(urlLanguage(state.url), page)
    .pipe(catchError(() => of(null)));
};

export type ProductLookup =
  | { state: 'found'; product: StoreProduct }
  | { state: 'missing' }
  | { state: 'unavailable' };

/**
 * Resolved before rendering, so the server's HTML has the product, and search engines get a
 * real 404 for an unknown product, or 503 (come back later) when the backend is down.
 */
export const productResolver: ResolveFn<ProductLookup> = (route, state) => {
  const response = inject(RESPONSE_INIT, { optional: true }) as { status?: number } | null;
  return inject(StoreApi)
    .get(route.paramMap.get('slug')!, urlLanguage(state.url))
    .pipe(
      map((product): ProductLookup => ({ state: 'found', product })),
      catchError((error: unknown) =>
        of<ProductLookup>({
          state: error instanceof HttpErrorResponse && error.status === 404 ? 'missing' : 'unavailable',
        }),
      ),
      tap((lookup) => {
        if (response && lookup.state !== 'found') {
          response.status = lookup.state === 'missing' ? 404 : 503;
        }
      }),
    );
};
