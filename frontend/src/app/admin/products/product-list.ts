import { DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../admin-errors';
import { ProductApi, ProductPage, ProductStatus, STATUS_NAMES } from './product-api';

@Component({
  selector: 'app-product-list',
  imports: [DecimalPipe, FormsModule, RouterLink],
  template: `
    <div class="heading">
      <h1>Produkti</h1>
      <a class="button-link" routerLink="/admin/products/new">Pievienot produktu</a>
    </div>

    <form class="filters" (ngSubmit)="load(0)">
      <label>
        Meklēt
        <input type="search" name="search" [(ngModel)]="search" placeholder="Nosaukums vai detaļas numurs" />
      </label>
      <label>
        Statuss
        <select name="status" [(ngModel)]="status" (ngModelChange)="load(0)">
          <option [ngValue]="null">Melnraksti un publicētie</option>
          <option [ngValue]="'DRAFT'">Melnraksti</option>
          <option [ngValue]="'PUBLISHED'">Publicētie</option>
          <option [ngValue]="'ARCHIVED'">Arhivētie</option>
        </select>
      </label>
      <button type="submit" class="secondary">Meklēt</button>
    </form>

    @if (error(); as message) {
      <p class="error" role="alert">{{ message }}</p>
    }

    @if (products(); as products) {
      @if (products.items.length) {
        <div class="table-scroll">
          <table class="cards">
            <thead>
              <tr>
                <th></th>
                <th>Nosaukums</th>
                <th>Detaļas numurs</th>
                <th>Cena</th>
                <th>Atlikums</th>
                <th>Statuss</th>
              </tr>
            </thead>
            <tbody>
              @for (product of products.items; track product.id) {
                <tr>
                  <td class="thumbnail-cell">
                    @if (product.thumbnailUrl) {
                      <img [src]="product.thumbnailUrl" alt="" width="64" height="48" loading="lazy" />
                    } @else {
                      <span class="no-photo">Nav foto</span>
                    }
                  </td>
                  <td data-label="Nosaukums"><a [routerLink]="['/admin/products', product.id]">{{ product.name }}</a></td>
                  <td data-label="Detaļas numurs">{{ product.partNumber ?? '—' }}</td>
                  <td data-label="Cena" class="nowrap">
                    @if (product.salePrice !== null) {
                      <s>{{ product.price | number: '1.2-2' }} €</s>
                      {{ product.salePrice | number: '1.2-2' }} €
                    } @else {
                      {{ product.price | number: '1.2-2' }} €
                    }
                  </td>
                  <td data-label="Atlikums" [class.out-of-stock]="product.stockQuantity === 0">{{ product.stockQuantity }}</td>
                  <td data-label="Statuss">{{ statusNames[product.status] }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <p class="actions">
          <button type="button" class="secondary" [disabled]="products.page === 0" (click)="load(products.page - 1)">
            Iepriekšējā lapa
          </button>
          <button
            type="button"
            class="secondary"
            [disabled]="products.page + 1 >= products.totalPages"
            (click)="load(products.page + 1)"
          >
            Nākamā lapa
          </button>
        </p>
      } @else {
        <p>Nav atrasts neviens produkts.</p>
      }
    }
  `,
  styleUrls: ['../admin.css', './product-list.css'],
})
export class ProductList implements OnInit {
  private readonly api = inject(ProductApi);

  protected readonly statusNames = STATUS_NAMES;
  protected readonly products = signal<ProductPage | null>(null);
  protected readonly error = signal<string | null>(null);
  protected search = '';
  protected status: ProductStatus | null = null;

  ngOnInit(): void {
    void this.load(0);
  }

  protected async load(page: number): Promise<void> {
    this.error.set(null);
    try {
      this.products.set(await firstValueFrom(this.api.list(this.status, this.search.trim(), page)));
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }
}
