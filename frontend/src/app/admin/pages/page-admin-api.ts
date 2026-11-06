import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import type { PageImage, PageName } from '../../pages/page-api';

/** A text in the three languages, as the panel edits it. Latvian is required. */
export interface EditableText {
  lv: string;
  ru: string | null;
  en: string | null;
}

/** A page's whole content; its shape is the page's schema (page-schemas.ts). */
export type PageDocument = Record<string, unknown>;

export interface PageSummary {
  page: PageName;
  updatedAt: string;
  updatedBy: string | null;
}

export interface EditablePage {
  page: PageName;
  content: PageDocument;
  version: number;
  updatedAt: string;
  updatedBy: string | null;
}

const BASE = '/api/admin/pages';

@Injectable({ providedIn: 'root' })
export class PageAdminApi {
  private readonly http = inject(HttpClient);

  list(): Observable<PageSummary[]> {
    return this.http.get<PageSummary[]>(BASE);
  }

  get(page: PageName): Observable<EditablePage> {
    return this.http.get<EditablePage>(`${BASE}/${page}`);
  }

  save(page: PageName, content: PageDocument, version: number): Observable<EditablePage> {
    return this.http.put<EditablePage>(`${BASE}/${page}`, { content, version });
  }

  upload(file: File): Observable<PageImage> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<PageImage>(`${BASE}/images`, body);
  }
}
