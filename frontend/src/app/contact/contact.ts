import { Component, inject, input } from '@angular/core';
import { I18n } from '../i18n/i18n';
import { ContactPage } from '../pages/page-api';
import { PageUnavailable } from '../pages/page-unavailable';
import { SiteInfo } from '../pages/site-info';

/** Map links rather than an embedded map, which would set third-party cookies. */
@Component({
  selector: 'app-contact',
  imports: [PageUnavailable],
  template: `
    @if (page(); as p) {
      <header class="page-head">
        <div class="container">
          <p class="eyebrow">XenonFix</p>
          <h1>{{ p.heading }}</h1>
        </div>
      </header>

      <div class="container page-body">
        <div class="cards">
          <section class="lift reveal">
            <h2>{{ p.phoneHeading }}</h2>
            <p class="big"><a [href]="info().phoneHref">{{ info().phone }}</a></p>
            <a class="button" [href]="info().whatsappHref" rel="noopener">{{ t('cta.whatsapp') }}</a>
          </section>
          <section class="lift reveal">
            <h2>{{ p.addressHeading }}</h2>
            <p class="big">{{ info().street }}<br />{{ info().city }}</p>
            <div class="button-row">
              <a class="button secondary" [href]="info().googleMapsHref" rel="noopener">{{ t('contact.map') }}</a>
              <a class="button secondary" [href]="info().wazeHref" rel="noopener">{{ t('contact.navigate') }}</a>
            </div>
          </section>
          <section class="lift reveal">
            <h2>{{ p.emailHeading }}</h2>
            <p class="big"><a [href]="'mailto:' + info().email">{{ info().email }}</a></p>
          </section>
          <section class="lift reveal">
            <h2>{{ p.hoursHeading }}</h2>
            <dl>
              @for (row of info().hours; track $index) {
                <dt>{{ row.day }}</dt>
                <dd>{{ row.time }}</dd>
              }
            </dl>
          </section>
        </div>
      </div>
    } @else {
      <app-page-unavailable />
    }
  `,
  styles: `
    .cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 26rem), 1fr));
      gap: 1rem;
    }

    section {
      padding: clamp(1.5rem, 4vw, 2.25rem);
    }

    h2 {
      margin: 0 0 1rem;
      color: var(--accent-bright);
      font-size: 0.8125rem;
      letter-spacing: 0.14em;
      text-transform: uppercase;
    }

    .big {
      margin: 0 0 1.5rem;
      font-size: clamp(1.375rem, 3vw, 1.75rem);
      font-weight: 700;
      letter-spacing: -0.02em;
      line-height: 1.3;
    }

    section:nth-child(3) .big {
      margin-bottom: 0;
    }

    .big a {
      color: inherit;
      text-decoration: none;
      background: linear-gradient(var(--accent-bright), var(--accent-bright)) 0 100% / 0 2px no-repeat;
      transition: background-size 0.4s var(--ease);
    }

    .big a:hover {
      background-size: 100% 2px;
    }

    dl {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 0.5rem 1rem;
      margin: 0;
    }

    dt {
      color: var(--muted);
    }

    dd {
      margin: 0;
      font-weight: 600;
      white-space: nowrap;
    }
  `,
})
export class Contact {
  protected readonly t = inject(I18n).t;
  protected readonly info = inject(SiteInfo).details;

  /** From the route's resolver; null when it could not be loaded. */
  readonly page = input<ContactPage | null>(null);
}
