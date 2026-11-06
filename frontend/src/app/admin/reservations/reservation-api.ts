import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';

export interface ReservationSummary {
  id: number;
  reference: string;
  productName: string;
  quantity: number;
  total: number;
  /** Empty once anonymized, like the other customer details. */
  customerName: string | null;
  customerPhone: string | null;
  status: ReservationStatus;
  createdAt: string;
  anonymized: boolean;
}

export interface ReservationPage {
  items: ReservationSummary[];
  page: number;
  totalPages: number;
}

export interface Reservation {
  id: number;
  reference: string;
  productId: number;
  productName: string;
  partNumber: string | null;
  unitPrice: number;
  quantity: number;
  total: number;
  customerName: string | null;
  customerPhone: string | null;
  customerEmail: string | null;
  comment: string | null;
  adminNote: string | null;
  language: 'lv' | 'ru' | 'en';
  status: ReservationStatus;
  createdAt: string;
  updatedAt: string;
  /** When the customer's details were erased; after that nothing can be changed. */
  anonymizedAt: string | null;
  version: number;
}

const BASE = '/api/admin/reservations';

@Injectable({ providedIn: 'root' })
export class ReservationApi {
  private readonly http = inject(HttpClient);

  list(status: ReservationStatus | null, search: string, page: number): Observable<ReservationPage> {
    return this.http.get<ReservationPage>(BASE, {
      params: { page, ...(status ? { status } : {}), ...(search ? { search } : {}) },
    });
  }

  get(id: number): Observable<Reservation> {
    return this.http.get<Reservation>(`${BASE}/${id}`);
  }

  changeStatus(id: number, status: ReservationStatus, version: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${BASE}/${id}/status`, { status, version });
  }

  changeNote(id: number, note: string, version: number): Observable<Reservation> {
    return this.http.put<Reservation>(`${BASE}/${id}/note`, { note, version });
  }

  anonymize(id: number, version: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${BASE}/${id}/anonymize`, { version });
  }
}

export const RESERVATION_STATUS_NAMES: Record<ReservationStatus, string> = {
  PENDING: 'Gaida',
  CONFIRMED: 'Apstiprināta',
  CANCELLED: 'Atcelta',
  COMPLETED: 'Pabeigta',
};

/**
 * What an admin should know before a change that moves stock or emails the customer, or null
 * if the change needs no warning. It describes the rules in the backend's
 * ReservationAdminService, which is what actually applies them.
 */
export function changeWarning(from: ReservationStatus, to: ReservationStatus, quantity: number): string | null {
  const parts: string[] = [];
  if (to === 'CANCELLED') {
    parts.push(`Atlikumā atgriezīsies ${quantity} gab.`);
  } else if (from === 'CANCELLED') {
    parts.push(`No atlikuma tiks paņemti ${quantity} gab.`);
  }
  const emailed =
    (to === 'CONFIRMED' && (from === 'PENDING' || from === 'CANCELLED')) ||
    (to === 'CANCELLED' && (from === 'PENDING' || from === 'CONFIRMED'));
  if (emailed) {
    parts.push('Klientam tiks nosūtīts e-pasts.');
  }
  return parts.length ? parts.join(' ') : null;
}
