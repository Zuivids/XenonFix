import { computed, Injectable, signal } from '@angular/core';
import { SiteInfoContent } from './page-api';

/** The shop's details with the links made from them, as the templates use them. */
export interface SiteDetails extends SiteInfoContent {
  phoneHref: string;
  whatsappHref: string;
  googleMapsHref: string;
  wazeHref: string;
}

/**
 * Used only while the edited details cannot be loaded, so the header and footer still show a
 * way to reach the shop. Times are in the language of the page that loads the real ones.
 */
const BUILT_IN: SiteInfoContent = {
  phone: '+371 288 03 119',
  whatsapp: '+371 288 03 119',
  email: 'info@xenonfix.lv',
  street: 'Bauskas iela 16c',
  city: 'Rīga, LV-1004',
  hours: [],
  instagramUrl: 'https://www.instagram.com/xenonfix/',
  facebookUrl: 'https://www.facebook.com/profile.php?id=100090952901221',
  companyName: 'SIA Elekanga',
  registrationNumber: '40203498222',
  vatNumber: 'LV40203498222',
  bankAccount: 'LV92HABA0551055135877',
};

/** The shop's details, as edited in the admin panel under Kontaktinformācija. */
@Injectable({ providedIn: 'root' })
export class SiteInfo {
  private readonly content = signal<SiteInfoContent>(BUILT_IN);

  readonly details = computed<SiteDetails>(() => withLinks(this.content()));

  set(content: SiteInfoContent): void {
    this.content.set(content);
  }
}

export function withLinks(info: SiteInfoContent): SiteDetails {
  const address = `${info.street}, ${info.city}`;
  return {
    ...info,
    phoneHref: `tel:${dialable(info.phone)}`,
    whatsappHref: `https://wa.me/${dialable(info.whatsapp).replace('+', '')}`,
    googleMapsHref: `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`,
    wazeHref: `https://waze.com/ul?q=${encodeURIComponent(address)}&navigate=yes`,
  };
}

/** Only the digits and the leading plus, as tel: and WhatsApp links need. */
function dialable(phone: string): string {
  return phone.replace(/[^+0-9]/g, '');
}
