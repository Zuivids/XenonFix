import { Component, inject } from '@angular/core';
import { I18n } from '../i18n/i18n';

@Component({
  selector: 'app-home',
  template: `
    <h1>{{ t('home.heading') }}</h1>
    <p>{{ t('home.intro') }}</p>
  `,
})
export class Home {
  protected readonly t = inject(I18n).t;
}
