import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { I18n } from '../i18n/i18n';
import { LocalizedPipe } from '../i18n/localized-link.pipe';
import { NotFound } from '../not-found/not-found';
import { SITE_INFO } from '../site-info';
import { Price } from './price';
import { ReservationForm } from './reservation-form';
import { StoreApi, StoreProduct } from './store-api';
import { ProductLookup } from './store.resolvers';

@Component({
  selector: 'app-product-page',
  imports: [LocalizedPipe, NotFound, Price, ReservationForm, RouterLink],
  templateUrl: './product-page.html',
  styleUrl: './product-page.css',
})
export class ProductPage {
  private readonly api = inject(StoreApi);
  private readonly i18n = inject(I18n);

  protected readonly t = this.i18n.t;
  protected readonly info = SITE_INFO;

  /** From the route's resolver (see withComponentInputBinding). */
  readonly lookup = input<ProductLookup>();

  /** The resolver's product, until the stock is looked at again. */
  protected readonly product = linkedSignal<StoreProduct | null>(() => {
    const lookup = this.lookup();
    return lookup?.state === 'found' ? lookup.product : null;
  });

  protected readonly selected = signal(0);
  protected readonly image = computed(() => {
    const images = this.product()?.images ?? [];
    return images[this.selected()] ?? images[0] ?? null;
  });

  /** Keeps the form's message on screen: it explains why the product is now out of stock. */
  protected readonly lostLastOne = signal(false);

  protected async refresh(): Promise<void> {
    const product = this.product();
    if (!product) {
      return;
    }
    try {
      const fresh = await firstValueFrom(this.api.get(product.slug, this.i18n.language()));
      this.lostLastOne.set(!fresh.available);
      this.product.set(fresh);
    } catch {
      // The form already says what happened, and the page is still usable as it is
    }
  }
}
