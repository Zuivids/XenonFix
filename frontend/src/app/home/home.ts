import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18n } from '../i18n/i18n';
import { LocalizedPipe } from '../i18n/localized-link.pipe';
import { HomePage, PageImage } from '../pages/page-api';
import { PageUnavailable } from '../pages/page-unavailable';
import { SiteInfo } from '../pages/site-info';
import { ProductCard } from '../store/product-card';
import { StoreProductSummary } from '../store/store-api';

@Component({
  selector: 'app-home',
  imports: [LocalizedPipe, PageUnavailable, ProductCard, RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  protected readonly t = inject(I18n).t;
  protected readonly info = inject(SiteInfo).details;

  /** From the route's resolvers (see withComponentInputBinding); null when it could not be loaded. */
  readonly page = input<HomePage | null>(null);

  /** The newest products. */
  readonly featured = input<StoreProductSummary[]>([]);

  /** Landscape photos take two columns, so their subject is not cropped away. */
  protected isWide(image: PageImage): boolean {
    return image.width > image.height;
  }
}
