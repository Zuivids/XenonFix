import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { I18n } from './i18n/i18n';
import { LanguageSwitcher } from './i18n/language-switcher';
import { localizedPath } from './i18n/languages';

@Component({
  selector: 'app-root',
  imports: [LanguageSwitcher, RouterLink, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly i18n = inject(I18n);
  protected readonly home = computed(() => localizedPath(this.i18n.language(), '/'));
}
