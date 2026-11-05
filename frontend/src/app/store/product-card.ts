import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18n } from '../i18n/i18n';
import { LocalizedPipe } from '../i18n/localized-link.pipe';
import { Price } from './price';
import { StoreProductSummary } from './store-api';

@Component({
  selector: 'app-product-card',
  imports: [LocalizedPipe, Price, RouterLink],
  template: `
    <a class="card lift" [routerLink]="'/product/' + product().slug | localized">
      <span class="photo zoom">
        @if (product().thumbnailUrl; as url) {
          <img [src]="url" alt="" width="600" height="450" loading="lazy" />
        }
      </span>
      <span class="text">
        <span class="name">{{ product().name }}</span>
        <app-price [price]="product().price" [salePrice]="product().salePrice" />
        <span class="availability" [class.out]="!product().available">
          {{ t(product().available ? 'store.inStock' : 'store.outOfStock') }}
        </span>
      </span>
    </a>
  `,
  styles: `
    :host {
      display: block;
    }

    .card {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow: hidden;
      color: inherit;
      text-decoration: none;
    }

    .photo {
      display: block;
      aspect-ratio: 4 / 3;
      background: var(--line);
    }

    .text {
      display: flex;
      flex: 1;
      flex-direction: column;
      gap: 0.5rem;
      padding: 1rem 1.125rem 1.25rem;
    }

    .name {
      font-weight: 700;
      line-height: 1.3;
    }

    .availability {
      margin-top: auto;
      color: var(--accent);
      font-size: 0.875rem;
      font-weight: 600;
    }

    .availability.out {
      color: var(--muted);
    }
  `,
})
export class ProductCard {
  protected readonly t = inject(I18n).t;

  readonly product = input.required<StoreProductSummary>();
}
