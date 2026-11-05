import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminApi, AuditPage } from '../admin-api';
import { errorMessage } from '../admin-errors';

const EVENT_NAMES: Record<string, string> = {
  LOGIN_SUCCEEDED: 'Pieslēdzās',
  LOGIN_FAILED: 'Neveiksmīga pieslēgšanās',
  LOGGED_OUT: 'Izgāja',
  SESSION_ENDED: 'Sesija beigta',
  BLOCKED_FOR_IP: 'IP adrese bloķēta',
  ACCOUNT_LOCKED: 'Konts bloķēts',
  PASSWORD_CHANGED: 'Nomainīja paroli',
  PASSWORD_CHANGE_FAILED: 'Neveiksmīga paroles maiņa',
  ADMIN_CREATED: 'Pievienots administrators',
  ADMIN_DISABLED: 'Atslēgts administrators',
  ADMIN_ENABLED: 'Ieslēgts administrators',
  ADMIN_PASSWORD_RESET: 'Administratoram iestatīta jauna parole',
  PRODUCT_CREATED: 'Pievienots produkts',
  PRODUCT_UPDATED: 'Mainīts produkts',
  PRODUCT_STOCK_CHANGED: 'Mainīts atlikums',
  PRODUCT_STATUS_CHANGED: 'Mainīts produkta statuss',
  PRODUCT_IMAGE_ADDED: 'Pievienots produkta foto',
  PRODUCT_IMAGE_REMOVED: 'Dzēsts produkta foto',
  RESERVATION_CREATED: 'Jauna rezervācija',
};

@Component({
  selector: 'app-audit-log',
  imports: [DatePipe],
  template: `
    <h1>Notikumu žurnāls</h1>
    @if (error(); as message) {
      <p class="error" role="alert">{{ message }}</p>
    }
    @if (log(); as log) {
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Laiks</th>
              <th>Notikums</th>
              <th>Administrators</th>
              <th>Papildu informācija</th>
              <th>IP adrese</th>
            </tr>
          </thead>
          <tbody>
            @for (entry of log.items; track $index) {
              <tr>
                <td>{{ entry.occurredAt | date: 'dd.MM.yyyy HH:mm:ss' }}</td>
                <td>{{ eventName(entry.eventType) }}</td>
                <td>{{ entry.adminEmail ?? '—' }}</td>
                <td>{{ entry.details ?? '—' }}</td>
                <td>{{ entry.ipAddress ?? '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <p class="actions">
        <button type="button" class="secondary" [disabled]="log.page === 0" (click)="load(log.page - 1)">
          Jaunāki
        </button>
        <button
          type="button"
          class="secondary"
          [disabled]="log.page + 1 >= log.totalPages"
          (click)="load(log.page + 1)"
        >
          Vecāki
        </button>
      </p>
    }
  `,
  styleUrl: '../admin.css',
})
export class AuditLog implements OnInit {
  private readonly api = inject(AdminApi);

  protected readonly log = signal<AuditPage | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    void this.load(0);
  }

  protected eventName(eventType: string): string {
    return EVENT_NAMES[eventType] ?? eventType;
  }

  protected async load(page: number): Promise<void> {
    this.error.set(null);
    try {
      this.log.set(await firstValueFrom(this.api.auditLog(page)));
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }
}
