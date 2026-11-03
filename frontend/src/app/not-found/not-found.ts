import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18n } from '../i18n/i18n';
import { LocalizedPipe } from '../i18n/localized-link.pipe';

@Component({
  selector: 'app-not-found',
  imports: [LocalizedPipe, RouterLink],
  template: `
    <header class="page-head">
      <div class="container">
        <p class="eyebrow">404</p>
        <h1>{{ t('notFound.heading') }}</h1>
      </div>
    </header>
    <div class="container page-body">
      <a class="button" [routerLink]="'/' | localized">{{ t('notFound.backHome') }}</a>
    </div>
  `,
})
export class NotFound {
  protected readonly t = inject(I18n).t;
}
