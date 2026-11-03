import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type ProductStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type ProductLanguage = 'lv' | 'ru' | 'en';

export interface ProductTexts {
  name: string;
  description: string | null;
}

export interface ProductImage {
  id: number;
  url: string;
  thumbnailUrl: string;
  width: number;
  height: number;
}

export interface Product {
  id: number;
  slug: string;
  partNumber: string | null;
  price: number;
  salePrice: number | null;
  stockQuantity: number;
  status: ProductStatus;
  translations: Partial<Record<ProductLanguage, ProductTexts>>;
  images: ProductImage[];
  version: number;
}

export interface ProductSummary {
  id: number;
  name: string;
  slug: string;
  partNumber: string | null;
  price: number;
  salePrice: number | null;
  stockQuantity: number;
  status: ProductStatus;
  thumbnailUrl: string | null;
}

export interface ProductPage {
  items: ProductSummary[];
  page: number;
  totalPages: number;
}

export interface ProductInput {
  slug: string;
  partNumber: string;
  price: number;
  salePrice: number | null;
  stockQuantity: number;
  translations: Record<ProductLanguage, { name: string; description: string }>;
  version?: number;
}

const BASE = '/api/admin/products';

@Injectable({ providedIn: 'root' })
export class ProductApi {
  private readonly http = inject(HttpClient);

  list(status: ProductStatus | null, search: string, page: number): Observable<ProductPage> {
    return this.http.get<ProductPage>(BASE, {
      params: { page, ...(status ? { status } : {}), ...(search ? { search } : {}) },
    });
  }

  get(id: number): Observable<Product> {
    return this.http.get<Product>(`${BASE}/${id}`);
  }

  create(input: ProductInput): Observable<Product> {
    return this.http.post<Product>(BASE, input);
  }

  update(id: number, input: ProductInput): Observable<Product> {
    return this.http.put<Product>(`${BASE}/${id}`, input);
  }

  changeStatus(id: number, status: ProductStatus): Observable<Product> {
    return this.http.post<Product>(`${BASE}/${id}/status`, { status });
  }

  addImage(id: number, file: File): Observable<Product> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<Product>(`${BASE}/${id}/images`, body);
  }

  orderImages(id: number, imageIds: number[]): Observable<Product> {
    return this.http.put<Product>(`${BASE}/${id}/images/order`, { imageIds });
  }

  removeImage(id: number, imageId: number): Observable<Product> {
    return this.http.delete<Product>(`${BASE}/${id}/images/${imageId}`);
  }
}

export const STATUS_NAMES: Record<ProductStatus, string> = {
  DRAFT: 'Melnraksts',
  PUBLISHED: 'Publicēts',
  ARCHIVED: 'Arhivēts',
};
