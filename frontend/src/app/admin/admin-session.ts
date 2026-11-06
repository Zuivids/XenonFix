import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminApi, Me } from './admin-api';

/** Who is logged in to the admin panel. The backend decides; this only remembers the answer. */
@Injectable({ providedIn: 'root' })
export class AdminSession {
  private readonly api = inject(AdminApi);

  readonly me = signal<Me | null>(null);

  /** @returns the logged-in admin, or null when nobody is logged in */
  async load(): Promise<Me | null> {
    try {
      const me = await firstValueFrom(this.api.me());
      this.me.set(me);
      return me;
    } catch (error) {
      this.me.set(null);
      if (error instanceof HttpErrorResponse && error.status === 401) {
        return null;
      }
      throw error;
    }
  }

  async login(email: string, password: string): Promise<Me> {
    const me = await this.api.login(email, password);
    this.me.set(me);
    return me;
  }

  async verifyMfa(code: string): Promise<Me> {
    const me = await this.api.verifyMfa(code);
    this.me.set(me);
    return me;
  }

  /** After setting up the app: the session is now past the second step. */
  setMe(me: Me): void {
    this.me.set(me);
  }

  async logout(): Promise<void> {
    await firstValueFrom(this.api.logout());
    this.me.set(null);
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await firstValueFrom(this.api.changePassword(currentPassword, newPassword));
    await this.load();
  }
}
