import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18n } from '../i18n/i18n';
import { LocalizedPipe } from '../i18n/localized-link.pipe';
import { ServicesPage } from '../pages/page-api';
import { PageUnavailable } from '../pages/page-unavailable';
import { SiteInfo } from '../pages/site-info';

@Component({
  selector: 'app-services',
  imports: [LocalizedPipe, PageUnavailable, RouterLink],
  template: `
    @if (page(); as p) {
      <header class="page-head">
        <div class="container">
          <p class="eyebrow">{{ p.eyebrow }}</p>
          <h1>{{ p.heading }}</h1>
          <p class="lead">{{ p.lead }}</p>
        </div>
      </header>

      <div class="container page-body">
        <ul class="services">
          @for (service of p.services; track $index) {
            <li class="lift reveal">
              <span class="number" aria-hidden="true">{{ number($index) }}</span>
              <h2>{{ service.title }}</h2>
              <p>{{ service.text }}</p>
            </li>
          }
        </ul>
        <div class="button-row reveal">
          <a class="button" [href]="info().phoneHref">{{ t('cta.call') }} {{ info().phone }}</a>
          <a class="button secondary" [routerLink]="'/garantijas-nosacijumi' | localized">{{ p.warrantyLink }}</a>
        </div>
      </div>
    } @else {
      <app-page-unavailable />
    }
  `,
  styles: `
    .services {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr));
      gap: 1rem;
      margin: 0 0 2.5rem;
      padding: 0;
      list-style: none;
    }

    li {
      padding: 1.5rem;
    }

    .number {
      display: block;
      margin-bottom: 1.25rem;
      color: var(--accent-bright);
      font-size: 0.875rem;
      font-weight: 700;
      letter-spacing: 0.1em;
    }

    h2 {
      margin: 0 0 0.625rem;
      font-size: 1.1875rem;
      letter-spacing: -0.01em;
    }

    p {
      margin: 0;
      color: var(--muted);
      font-size: 0.9688rem;
    }
  `,
})
export class Services {
  protected readonly t = inject(I18n).t;
  protected readonly info = inject(SiteInfo).details;

  /** From the route's resolver; null when it could not be loaded. */
  readonly page = input<ServicesPage | null>(null);

  protected number(index: number): string {
    return String(index + 1).padStart(2, '0');
  }
}
