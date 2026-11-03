import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { I18n } from '../i18n/i18n';
import { LanguageSwitcher } from '../i18n/language-switcher';
import { localizedPath } from '../i18n/languages';

/** Header and frame of the public pages. The admin panel has its own. */
@Component({
  selector: 'app-site-layout',
  imports: [LanguageSwitcher, RouterLink, RouterOutlet],
  template: `
    <header>
      <a class="brand" [routerLink]="home()">
        <img src="logo.png" alt="XenonFix" width="644" height="192" />
      </a>
      <app-language-switcher />
    </header>
    <main>
      <router-outlet />
    </main>
  `,
  styleUrl: './site-layout.css',
})
export class SiteLayout {
  private readonly i18n = inject(I18n);
  protected readonly home = computed(() => localizedPath(this.i18n.language(), '/'));
}
