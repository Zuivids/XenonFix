import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';

export interface Me {
  id: number;
  email: string;
  displayName: string;
  passwordChangeRequired: boolean;
}

export interface Admin {
  id: number;
  email: string;
  displayName: string;
  status: 'ACTIVE' | 'DISABLED';
  locked: boolean;
  passwordChangeRequired: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface AuditEntry {
  occurredAt: string;
  eventType: string;
  adminEmail: string | null;
  targetType: string | null;
  targetId: string | null;
  ipAddress: string | null;
  details: string | null;
}

export interface AuditPage {
  items: AuditEntry[];
  page: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  /** Fetches the CSRF cookie first: a visitor who has not logged in may not have one yet. */
  async login(email: string, password: string): Promise<Me> {
    await firstValueFrom(this.http.get<void>('/api/admin/auth/csrf'));
    const response = await firstValueFrom(
      this.http.post<{ admin: Me }>('/api/admin/auth/login', { email, password }),
    );
    return response.admin;
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/admin/auth/logout', null);
  }

  me(): Observable<Me> {
    return this.http.get<Me>('/api/admin/me');
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/admin/me/password', { currentPassword, newPassword });
  }

  admins(): Observable<Admin[]> {
    return this.http.get<Admin[]>('/api/admin/admins');
  }

  createAdmin(email: string, displayName: string, password: string): Observable<Admin> {
    return this.http.post<Admin>('/api/admin/admins', { email, displayName, password });
  }

  setEnabled(id: number, enabled: boolean): Observable<Admin> {
    return this.http.post<Admin>(`/api/admin/admins/${id}/${enabled ? 'enable' : 'disable'}`, null);
  }

  resetPassword(id: number, password: string): Observable<Admin> {
    return this.http.post<Admin>(`/api/admin/admins/${id}/reset-password`, { password });
  }

  auditLog(page: number): Observable<AuditPage> {
    return this.http.get<AuditPage>('/api/admin/audit-log', { params: { page } });
  }
}
