import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { I18n } from '../i18n/i18n';
import { LanguageSwitcher } from '../i18n/language-switcher';
import { LocalizedPipe } from '../i18n/localized-link.pipe';
import { MessageKey } from '../i18n/messages.lv';
import { SITE_INFO } from '../site-info';

const NAV_LINKS: { path: string; label: MessageKey; exact: boolean }[] = [
  { path: '/', label: 'nav.home', exact: true },
  { path: '/par-mums', label: 'nav.about', exact: false },
  { path: '/musu-pakalpojumi', label: 'nav.services', exact: false },
  { path: '/store', label: 'nav.store', exact: false },
  { path: '/musu-darbi', label: 'nav.works', exact: false },
  { path: '/kontakti', label: 'nav.contact', exact: false },
];

const TERMS_LINKS: { path: string; label: MessageKey }[] = [
  { path: '/apmaksas-nosacijumi', label: 'payment.heading' },
  { path: '/piegades-nosacijumi', label: 'delivery.heading' },
  { path: '/garantijas-nosacijumi', label: 'warranty.heading' },
];

/** Header and footer of the public pages. The admin panel has its own. */
@Component({
  selector: 'app-site-layout',
  imports: [LanguageSwitcher, LocalizedPipe, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './site-layout.html',
  styleUrl: './site-layout.css',
})
export class SiteLayout {
  protected readonly t = inject(I18n).t;
  protected readonly info = SITE_INFO;
  protected readonly navLinks = NAV_LINKS;
  protected readonly termsLinks = TERMS_LINKS;
  protected readonly year = new Date().getFullYear();
  protected readonly menuOpen = signal(false);
}
