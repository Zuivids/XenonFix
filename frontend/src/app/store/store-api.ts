import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import { Language } from '../i18n/languages';

export interface StoreImage {
  url: string;
  thumbnailUrl: string;
  width: number;
  height: number;
}

export interface StoreProductSummary {
  slug: string;
  name: string;
  price: number;
  salePrice: number | null;
  available: boolean;
  thumbnailUrl: string | null;
}

export interface StoreProductPage {
  items: StoreProductSummary[];
  page: number;
  totalPages: number;
}

export interface StoreProduct {
  slug: string;
  name: string;
  description: string | null;
  partNumber: string | null;
  price: number;
  salePrice: number | null;
  available: boolean;
  images: StoreImage[];
}

export interface ReservationInput {
  slug: string;
  quantity: number;
  name: string;
  phone: string;
  email: string;
  comment: string;
  language: Language;
  privacyAccepted: boolean;
  /** A field people never see and bots fill in. */
  website: string;
}

export interface ReservationResult {
  reference: string;
}

const PRODUCTS = '/api/store/products';

/** Pages wait for these before showing, so they must not hang when the backend does not answer. */
const READ_TIMEOUT_MS = 4000;

@Injectable({ providedIn: 'root' })
export class StoreApi {
  private readonly http = inject(HttpClient);

  list(language: Language, page = 0, size = 24): Observable<StoreProductPage> {
    return this.http
      .get<StoreProductPage>(PRODUCTS, { params: { lang: language, page, size } })
      .pipe(timeout(READ_TIMEOUT_MS));
  }

  get(slug: string, language: Language): Observable<StoreProduct> {
    return this.http
      .get<StoreProduct>(`${PRODUCTS}/${encodeURIComponent(slug)}`, { params: { lang: language } })
      .pipe(timeout(READ_TIMEOUT_MS));
  }

  reserve(input: ReservationInput): Observable<ReservationResult> {
    return this.http.post<ReservationResult>('/api/store/reservations', input);
  }
}

/** The code the backend gives an expected error, if it was one. */
export function errorCode(error: unknown): string | null {
  return error instanceof HttpErrorResponse ? (error.error?.code ?? null) : null;
}
