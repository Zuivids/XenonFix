import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18n } from '../i18n/i18n';
import { LocalizedPipe } from '../i18n/localized-link.pipe';
import { SiteInfo } from '../pages/site-info';
import { ProductCard } from './product-card';
import { StoreProductPage } from './store-api';

@Component({
  selector: 'app-store-list',
  imports: [LocalizedPipe, ProductCard, RouterLink],
  template: `
    <header class="page-head">
      <div class="container">
        <h1>{{ t('store.heading') }}</h1>
        <p class="lead">{{ t('store.lead') }}</p>
      </div>
    </header>

    <div class="container page-body">
      @if (result(); as page) {
        @if (page.items.length) {
          <div class="grid">
            @for (product of page.items; track product.slug) {
              <app-product-card [product]="product" />
            }
          </div>
          @if (page.totalPages > 1) {
            <nav class="pages" [attr.aria-label]="t('store.pages')">
              @if (page.page > 0) {
                <a class="button secondary" [routerLink]="'/store' | localized" [queryParams]="pageLink(page.page - 1)">
                  {{ t('store.previous') }}
                </a>
              }
              <span>{{ page.page + 1 }} / {{ page.totalPages }}</span>
              @if (page.page + 1 < page.totalPages) {
                <a class="button secondary" [routerLink]="'/store' | localized" [queryParams]="pageLink(page.page + 1)">
                  {{ t('store.next') }}
                </a>
              }
            </nav>
          }
        } @else {
          <p class="lead">{{ t('store.empty') }}</p>
          <a class="button" [href]="info().phoneHref">{{ t('cta.call') }} {{ info().phone }}</a>
        }
      } @else {
        <p class="lead" role="alert">{{ t('store.unavailable') }}</p>
        <a class="button" [href]="info().phoneHref">{{ t('cta.call') }} {{ info().phone }}</a>
      }
    </div>
  `,
  styles: `
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 15.5rem), 1fr));
      gap: 1.25rem;
    }

    .pages {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      margin-top: 2.5rem;
    }
  `,
})
export class StoreList {
  protected readonly t = inject(I18n).t;
  protected readonly info = inject(SiteInfo).details;

  /** From the route's resolver (see withComponentInputBinding); null if the backend did not answer. */
  readonly result = input<StoreProductPage | null>(null);

  /** The first page is the address without a number. */
  protected pageLink(page: number): { page: number | null } {
    return { page: page > 0 ? page + 1 : null };
  }
}
