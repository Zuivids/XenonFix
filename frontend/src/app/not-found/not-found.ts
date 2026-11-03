import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18n } from '../i18n/i18n';
import { localizedPath } from '../i18n/languages';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  template: `
    <h1>{{ i18n.t('notFound.heading') }}</h1>
    <p><a [routerLink]="home()">{{ i18n.t('notFound.backHome') }}</a></p>
  `,
})
export class NotFound {
  protected readonly i18n = inject(I18n);
  protected readonly home = computed(() => localizedPath(this.i18n.language(), '/'));
}
