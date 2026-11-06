import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../admin-errors';
import { RESERVATION_STATUS_NAMES, ReservationApi, ReservationPage, ReservationStatus } from './reservation-api';

@Component({
  selector: 'app-reservation-list',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink],
  template: `
    <div class="heading">
      <h1>Rezervācijas</h1>
    </div>

    <form class="filters" (ngSubmit)="load(0)">
      <label>
        Meklēt
        <input type="search" name="search" [(ngModel)]="search" placeholder="Numurs, klients, telefons vai produkts" />
      </label>
      <label>
        Statuss
        <select name="status" [(ngModel)]="status" (ngModelChange)="load(0)">
          <option [ngValue]="null">Visas</option>
          @for (option of statusOptions; track option) {
            <option [ngValue]="option">{{ statusNames[option] }}</option>
          }
        </select>
      </label>
      <button type="submit" class="secondary">Meklēt</button>
    </form>

    @if (error(); as message) {
      <p class="error" role="alert">{{ message }}</p>
    }

    @if (reservations(); as reservations) {
      @if (reservations.items.length) {
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Numurs</th>
                <th>Datums</th>
                <th>Produkts</th>
                <th>Daudzums</th>
                <th>Kopā</th>
                <th>Klients</th>
                <th>Telefons</th>
                <th>Statuss</th>
              </tr>
            </thead>
            <tbody>
              @for (reservation of reservations.items; track reservation.id) {
                <tr [class.waiting]="reservation.status === 'PENDING'">
                  <td class="nowrap">
                    <a [routerLink]="['/admin/reservations', reservation.id]">{{ reservation.reference }}</a>
                  </td>
                  <td class="nowrap">{{ reservation.createdAt | date: 'dd.MM.yyyy HH:mm' }}</td>
                  <td>{{ reservation.productName }}</td>
                  <td>{{ reservation.quantity }}</td>
                  <td class="nowrap">{{ reservation.total | number: '1.2-2' }} €</td>
                  @if (reservation.anonymized) {
                    <td class="erased" colspan="2">Dati dzēsti</td>
                  } @else {
                    <td>{{ reservation.customerName }}</td>
                    <td class="nowrap">{{ reservation.customerPhone }}</td>
                  }
                  <td class="nowrap">
                    <span [class]="'status status-' + reservation.status">{{ statusNames[reservation.status] }}</span>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <p class="actions">
          <button
            type="button"
            class="secondary"
            [disabled]="reservations.page === 0"
            (click)="load(reservations.page - 1)"
          >
            Iepriekšējā lapa
          </button>
          <button
            type="button"
            class="secondary"
            [disabled]="reservations.page + 1 >= reservations.totalPages"
            (click)="load(reservations.page + 1)"
          >
            Nākamā lapa
          </button>
        </p>
      } @else {
        <p>Nav atrasta neviena rezervācija.</p>
      }
    }
  `,
  styleUrls: ['../admin.css', '../products/product-list.css', './reservations.css'],
})
export class ReservationList implements OnInit {
  private readonly api = inject(ReservationApi);

  protected readonly statusNames = RESERVATION_STATUS_NAMES;
  protected readonly statusOptions = Object.keys(RESERVATION_STATUS_NAMES) as ReservationStatus[];
  protected readonly reservations = signal<ReservationPage | null>(null);
  protected readonly error = signal<string | null>(null);
  protected search = '';
  protected status: ReservationStatus | null = null;

  ngOnInit(): void {
    void this.load(0);
  }

  protected async load(page: number): Promise<void> {
    this.error.set(null);
    try {
      this.reservations.set(await firstValueFrom(this.api.list(this.status, this.search.trim(), page)));
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }
}
