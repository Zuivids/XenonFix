import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import { Language } from '../i18n/languages';

/**
 * The editable pages as the site shows them: one language, with Latvian where a translation is
 * missing. The backend's records in lv.xenonfix.page.content define the same shapes, with each
 * text in three languages.
 */
export type PageName = 'home' | 'about' | 'services' | 'works' | 'contact' | 'site-info';

export interface PageImage {
  src: string;
  width: number;
  height: number;
}

export interface Seo {
  title: string;
  description: string;
}

export interface TitledText {
  title: string;
  text: string;
}

export interface HomePage {
  seo: Seo;
  eyebrow: string;
  heading: string;
  intro: string;
  heroImage: PageImage;
  heroImageAlt: string;
  stats: { value: string; label: string }[];
  servicesEyebrow: string;
  servicesHeading: string;
  allServices: string;
  tiles: (TitledText & { image: PageImage })[];
  storeEyebrow: string;
  storeHeading: string;
  ctaHeading: string;
  ctaText: string;
  reasons: string[];
  /** How many services the services page lists. */
  serviceCount: number;
}

export interface AboutPage {
  seo: Seo;
  heading: string;
  lead: string;
  points: string[];
  goalHeading: string;
  goal: string[];
  managerImage: PageImage;
  managerName: string;
  managerRole: string;
  manager: string[];
}

export interface ServicesPage {
  seo: Seo;
  eyebrow: string;
  heading: string;
  lead: string;
  services: TitledText[];
  warrantyLink: string;
}

export interface WorksPage {
  seo: Seo;
  heading: string;
  intro: string;
  photos: { image: PageImage; caption: string }[];
}

export interface ContactPage {
  seo: Seo;
  heading: string;
  phoneHeading: string;
  addressHeading: string;
  emailHeading: string;
  hoursHeading: string;
}

export interface SiteInfoContent {
  phone: string;
  whatsapp: string;
  email: string;
  street: string;
  city: string;
  hours: { day: string; time: string }[];
  instagramUrl: string;
  facebookUrl: string;
  companyName: string;
  registrationNumber: string;
  vatNumber: string;
  bankAccount: string;
}

/** Pages wait for these before showing, so they must not hang when the backend does not answer. */
const READ_TIMEOUT_MS = 4000;

@Injectable({ providedIn: 'root' })
export class PageApi {
  private readonly http = inject(HttpClient);

  get<T>(page: PageName, language: Language): Observable<T> {
    return this.http.get<T>(`/api/pages/${page}`, { params: { lang: language } }).pipe(timeout(READ_TIMEOUT_MS));
  }
}
