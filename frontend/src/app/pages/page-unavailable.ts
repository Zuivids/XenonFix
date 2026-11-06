import { Component, inject } from '@angular/core';
import { I18n } from '../i18n/i18n';
import { SiteInfo } from './site-info';

/** In place of a page whose content could not be loaded, with a way to reach the shop anyway. */
@Component({
  selector: 'app-page-unavailable',
  template: `
    <div class="container page-body unavailable">
      <p class="lead">{{ t('page.unavailable') }}</p>
      <a class="button" [href]="info().phoneHref">{{ t('cta.call') }} {{ info().phone }}</a>
    </div>
  `,
  styles: `
    .unavailable {
      padding-block: clamp(4rem, 12vw, 8rem);
    }
  `,
})
export class PageUnavailable {
  protected readonly t = inject(I18n).t;
  protected readonly info = inject(SiteInfo).details;
}
