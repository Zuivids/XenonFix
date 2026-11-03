import { Location } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';
import { I18n } from './i18n';
import { LANGUAGE_NAMES, LANGUAGES, localizedPath, pagePath } from './languages';

/** Plain links, not a dropdown: crawlers follow them to find each language version. */
@Component({
  selector: 'app-language-switcher',
  imports: [RouterLink],
  template: `
    <nav class="language-switcher" [attr.aria-label]="i18n.t('site.languageSwitcher')">
      @for (link of links(); track link.language) {
        <a
          [routerLink]="link.path"
          [attr.hreflang]="link.language"
          [attr.lang]="link.language"
          [attr.aria-label]="link.name"
          [attr.title]="link.name"
          [attr.aria-current]="link.language === i18n.language() ? 'true' : null"
        >{{ link.language }}</a>
      }
    </nav>
  `,
  styleUrl: './language-switcher.css',
})
export class LanguageSwitcher {
  protected readonly i18n = inject(I18n);
  private readonly router = inject(Router);

  // Location, because this component is created before the first navigation ends
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: inject(Location).path() || '/' },
  );

  protected readonly links = computed(() =>
    LANGUAGES.map((language) => ({
      language,
      name: LANGUAGE_NAMES[language],
      path: localizedPath(language, pagePath(this.url())),
    })),
  );
}
