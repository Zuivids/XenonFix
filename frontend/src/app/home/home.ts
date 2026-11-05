import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18n } from '../i18n/i18n';
import { LocalizedPipe } from '../i18n/localized-link.pipe';
import { SERVICE_PHOTOS, SITE_INFO } from '../site-info';
import { ProductCard } from '../store/product-card';
import { StoreProductSummary } from '../store/store-api';

@Component({
  selector: 'app-home',
  imports: [LocalizedPipe, ProductCard, RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  private readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  protected readonly content = this.i18n.content;
  protected readonly info = SITE_INFO;
  protected readonly photos = SERVICE_PHOTOS;

  /** The newest products, from the route's resolver (see withComponentInputBinding). */
  readonly featured = input<StoreProductSummary[]>([]);

  /** Landscape photos take two columns, so their subject is not cropped away. */
  protected isWide(index: number): boolean {
    return this.photos[index].width > this.photos[index].height;
  }
}
