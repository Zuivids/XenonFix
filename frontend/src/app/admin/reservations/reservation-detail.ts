import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../admin-errors';
import {
  changeWarning,
  Reservation,
  RESERVATION_STATUS_NAMES,
  ReservationApi,
  ReservationStatus,
} from './reservation-api';

const ACTIONS: { status: ReservationStatus; label: string }[] = [
  { status: 'CONFIRMED', label: 'Apstiprināt' },
  { status: 'COMPLETED', label: 'Atzīmēt kā pabeigtu' },
  { status: 'PENDING', label: 'Atgriezt uz "Gaida"' },
  { status: 'CANCELLED', label: 'Atcelt' },
];

const LANGUAGE_NAMES = { lv: 'Latviešu', ru: 'Krievu', en: 'Angļu' };

const NOTE_LENGTH = 2000;

@Component({
  selector: 'app-reservation-detail',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink],
  template: `
    <p><a routerLink="/admin/reservations">← Visas rezervācijas</a></p>

    @if (error(); as message) {
      <p class="error" role="alert">{{ message }}</p>
    }

    @if (reservation(); as item) {
      <div class="title">
        <h1>{{ item.reference }}</h1>
        <span [class]="'status status-' + item.status">{{ statusNames[item.status] }}</span>
      </div>

      <section class="status-actions" aria-label="Mainīt statusu">
        @for (action of actions(); track action.status) {
          <button
            type="button"
            [class.secondary]="action.status !== 'CONFIRMED'"
            [disabled]="busy()"
            (click)="changeStatus(action.status)"
          >
            {{ action.label }}
          </button>
        }
      </section>

      <div class="columns">
        <section>
          <h2>Klients</h2>
          <dl>
            <dt>Vārds</dt>
            <dd>{{ item.customerName }}</dd>
            <dt>Telefons</dt>
            <dd><a [href]="'tel:' + phoneLink(item.customerPhone)">{{ item.customerPhone }}</a></dd>
            <dt>E-pasts</dt>
            <dd><a [href]="'mailto:' + item.customerEmail">{{ item.customerEmail }}</a></dd>
            <dt>Valoda</dt>
            <dd>{{ languageNames[item.language] }}</dd>
            @if (item.comment) {
              <dt>Komentārs</dt>
              <dd class="text">{{ item.comment }}</dd>
            }
          </dl>
        </section>

        <section>
          <h2>Rezervācija</h2>
          <dl>
            <dt>Produkts</dt>
            <dd><a [routerLink]="['/admin/products', item.productId]">{{ item.productName }}</a></dd>
            @if (item.partNumber) {
              <dt>Detaļas numurs</dt>
              <dd>{{ item.partNumber }}</dd>
            }
            <dt>Cena par vienību</dt>
            <dd>{{ item.unitPrice | number: '1.2-2' }} €</dd>
            <dt>Daudzums</dt>
            <dd>{{ item.quantity }}</dd>
            <dt>Kopā</dt>
            <dd>{{ item.total | number: '1.2-2' }} €</dd>
            <dt>Izveidota</dt>
            <dd>{{ item.createdAt | date: 'dd.MM.yyyy HH:mm' }}</dd>
            <dt>Mainīta</dt>
            <dd>{{ item.updatedAt | date: 'dd.MM.yyyy HH:mm' }}</dd>
          </dl>
        </section>
      </div>

      <form class="note" (ngSubmit)="saveNote()">
        <label>
          Piezīmes
          <textarea
            name="note"
            rows="4"
            [maxLength]="noteLength"
            [ngModel]="note()"
            (ngModelChange)="note.set($event)"
          ></textarea>
        </label>
        <p class="hint">Redzamas tikai administratoriem. Klients tās neredz.</p>
        <p class="actions">
          <button type="submit" class="secondary" [disabled]="busy() || !noteChanged()">Saglabāt piezīmes</button>
          @if (noteSaved()) {
            <span role="status">Saglabāts.</span>
          }
        </p>
      </form>
    }
  `,
  styleUrls: ['../admin.css', './reservations.css'],
})
export class ReservationDetail implements OnInit {
  private readonly api = inject(ReservationApi);

  /** From the address (see withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly statusNames = RESERVATION_STATUS_NAMES;
  protected readonly languageNames = LANGUAGE_NAMES;
  protected readonly noteLength = NOTE_LENGTH;
  protected readonly reservation = signal<Reservation | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly note = signal('');
  protected readonly noteSaved = signal(false);

  protected readonly actions = computed(() => ACTIONS.filter((action) => action.status !== this.reservation()?.status));
  protected readonly noteChanged = computed(() => this.note().trim() !== (this.reservation()?.adminNote ?? ''));

  async ngOnInit(): Promise<void> {
    try {
      const reservation = await firstValueFrom(this.api.get(Number(this.id())));
      this.reservation.set(reservation);
      this.note.set(reservation.adminNote ?? '');
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }

  /** The address keeps only what a phone can dial. */
  protected phoneLink(phone: string): string {
    return phone.replace(/[^+0-9]/g, '');
  }

  protected async changeStatus(status: ReservationStatus): Promise<void> {
    const item = this.reservation()!;
    const warning = changeWarning(item.status, status, item.quantity);
    if (warning && !window.confirm(`${RESERVATION_STATUS_NAMES[status]}: ${warning} Turpināt?`)) {
      return;
    }
    await this.run(() => this.api.changeStatus(item.id, status, item.version));
  }

  protected async saveNote(): Promise<void> {
    const item = this.reservation()!;
    const saved = await this.run(() => this.api.changeNote(item.id, this.note(), item.version));
    if (saved) {
      this.note.set(saved.adminNote ?? '');
      this.noteSaved.set(true);
    }
  }

  /**
   * After a refusal the text in the notes field stays, so nothing typed is lost. When someone
   * else changed the reservation it is loaded again, because the admin must see the change,
   * and the next attempt then applies to the current version.
   */
  private async run(change: () => ReturnType<ReservationApi['get']>): Promise<Reservation | null> {
    this.busy.set(true);
    this.error.set(null);
    this.noteSaved.set(false);
    try {
      const updated = await firstValueFrom(change());
      this.reservation.set(updated);
      return updated;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.error?.code === 'CHANGED_BY_ANOTHER') {
        this.error.set('Kāds cits tikko mainīja šo rezervāciju. Dati ir atjaunoti: pārbaudiet tos un mēģiniet vēlreiz.');
        await this.reload();
      } else {
        this.error.set(errorMessage(error));
      }
      return null;
    } finally {
      this.busy.set(false);
    }
  }

  private async reload(): Promise<void> {
    try {
      this.reservation.set(await firstValueFrom(this.api.get(this.reservation()!.id)));
    } catch {
      // The message about the change is already shown, and reloading the page still works
    }
  }
}
