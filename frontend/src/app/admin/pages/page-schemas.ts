import type { PageName } from '../../pages/page-api';
import type { EditableText } from './page-admin-api';

/**
 * What the editor shows for each page, in the order the page shows it. The backend's records
 * in lv.xenonfix.page.content define the same fields and check them on save; the keys here
 * must match theirs.
 */
export type Field =
  /** A text in three languages. */
  | { kind: 'text'; key: string; label: string; multiline?: boolean; hint?: string }
  /** The same in every language, such as a phone number. */
  | { kind: 'plain'; key: string; label: string; hint?: string; type?: 'text' | 'email' | 'url' | 'tel' }
  | { kind: 'image'; key: string; label: string; hint?: string }
  /** Items that can be added, removed and reordered. Without fields, each item is one text. */
  | {
      kind: 'list';
      key: string;
      label: string;
      itemLabel: string;
      fields?: Field[];
      multiline?: boolean;
      min?: number;
      max: number;
      newItem: () => unknown;
    }
  | { kind: 'section'; label: string; fields: Field[] };

export interface PageSchema {
  name: string;
  /** Where the page is on the site, for "Skatīt lapā". */
  publicPath: string;
  description: string;
  fields: Field[];
}

export function emptyText(): EditableText {
  return { lv: '', ru: '', en: '' };
}

const SEO: Field = {
  kind: 'section',
  label: 'Meklētājiem',
  fields: [
    { kind: 'text', key: 'seo.title', label: 'Nosaukums pārlūka cilnē un meklēšanas rezultātos' },
    { kind: 'text', key: 'seo.description', label: 'Apraksts meklēšanas rezultātos', multiline: true, hint: 'Ap 150 rakstzīmēm.' },
  ],
};

const paragraphs = (key: string, label: string, max: number): Field => ({
  kind: 'list',
  key,
  label,
  itemLabel: 'Rindkopa',
  multiline: true,
  max,
  newItem: emptyText,
});

export const PAGE_SCHEMAS: Record<PageName, PageSchema> = {
  home: {
    name: 'Sākumlapa',
    publicPath: '/',
    description: 'Galvenais foto, pakalpojumu flīzes un teksti.',
    fields: [
      SEO,
      {
        kind: 'section',
        label: 'Augšdaļa',
        fields: [
          { kind: 'text', key: 'eyebrow', label: 'Rinda virs virsraksta' },
          { kind: 'text', key: 'heading', label: 'Virsraksts' },
          { kind: 'text', key: 'intro', label: 'Ievads', multiline: true },
          { kind: 'image', key: 'heroImage', label: 'Galvenais foto' },
          { kind: 'text', key: 'heroImageAlt', label: 'Foto apraksts', hint: 'Ko foto rāda, tiem, kas to neredz.' },
        ],
      },
      {
        kind: 'list',
        key: 'stats',
        label: 'Skaitļi',
        itemLabel: 'Skaitlis',
        max: 4,
        fields: [
          { kind: 'plain', key: 'value', label: 'Skaitlis' },
          { kind: 'text', key: 'label', label: 'Teksts zem tā' },
        ],
        newItem: () => ({ value: '', label: emptyText() }),
      },
      {
        kind: 'section',
        label: 'Pakalpojumi',
        fields: [
          { kind: 'text', key: 'servicesEyebrow', label: 'Rinda virs virsraksta' },
          { kind: 'text', key: 'servicesHeading', label: 'Virsraksts', hint: 'Arī pogas teksts augšdaļā.' },
          { kind: 'text', key: 'allServices', label: 'Pēdējās flīzes teksts', hint: 'Skaitlis blakus ir pakalpojumu skaits lapā "Mūsu pakalpojumi".' },
        ],
      },
      {
        kind: 'list',
        key: 'tiles',
        label: 'Pakalpojumu flīzes',
        itemLabel: 'Flīze',
        min: 1,
        max: 12,
        fields: [
          { kind: 'image', key: 'image', label: 'Foto', hint: 'Platiem foto flīze ir divreiz platāka.' },
          { kind: 'text', key: 'title', label: 'Nosaukums' },
          { kind: 'text', key: 'text', label: 'Teksts', multiline: true },
        ],
        newItem: () => ({ image: null, title: emptyText(), text: emptyText() }),
      },
      {
        kind: 'section',
        label: 'Veikals',
        fields: [
          { kind: 'text', key: 'storeEyebrow', label: 'Rinda virs virsraksta' },
          { kind: 'text', key: 'storeHeading', label: 'Virsraksts', hint: 'Sadaļa redzama, ja veikalā ir produkti.' },
        ],
      },
      {
        kind: 'section',
        label: 'Apakšdaļa',
        fields: [
          { kind: 'text', key: 'ctaHeading', label: 'Virsraksts' },
          { kind: 'text', key: 'ctaText', label: 'Teksts', multiline: true },
          paragraphs('reasons', 'Rindkopas blakus', 10),
        ],
      },
    ],
  },
  about: {
    name: 'Par mums',
    publicPath: '/par-mums',
    description: 'Ievads, punkti, mērķis un vadītājs.',
    fields: [
      SEO,
      {
        kind: 'section',
        label: 'Augšdaļa',
        fields: [
          { kind: 'text', key: 'heading', label: 'Virsraksts' },
          { kind: 'text', key: 'lead', label: 'Ievads', multiline: true },
        ],
      },
      paragraphs('points', 'Numurētie punkti', 12),
      {
        kind: 'section',
        label: 'Mērķis',
        fields: [{ kind: 'text', key: 'goalHeading', label: 'Virsraksts' }, paragraphs('goal', 'Rindkopas', 10)],
      },
      {
        kind: 'section',
        label: 'Vadītājs',
        fields: [
          { kind: 'image', key: 'managerImage', label: 'Foto', hint: 'Vislabāk stāvs foto.' },
          { kind: 'text', key: 'managerName', label: 'Vārds' },
          { kind: 'text', key: 'managerRole', label: 'Amats' },
          paragraphs('manager', 'Rindkopas', 10),
        ],
      },
    ],
  },
  services: {
    name: 'Mūsu pakalpojumi',
    publicPath: '/musu-pakalpojumi',
    description: 'Pakalpojumu saraksts.',
    fields: [
      SEO,
      {
        kind: 'section',
        label: 'Augšdaļa',
        fields: [
          { kind: 'text', key: 'eyebrow', label: 'Rinda virs virsraksta' },
          { kind: 'text', key: 'heading', label: 'Virsraksts' },
          { kind: 'text', key: 'lead', label: 'Ievads', multiline: true },
        ],
      },
      {
        kind: 'list',
        key: 'services',
        label: 'Pakalpojumi',
        itemLabel: 'Pakalpojums',
        min: 1,
        max: 40,
        fields: [
          { kind: 'text', key: 'title', label: 'Nosaukums' },
          { kind: 'text', key: 'text', label: 'Apraksts', multiline: true },
        ],
        newItem: () => ({ title: emptyText(), text: emptyText() }),
      },
      { kind: 'text', key: 'warrantyLink', label: 'Pogas uz garantijas nosacījumiem teksts' },
    ],
  },
  works: {
    name: 'Mūsu darbi',
    publicPath: '/musu-darbi',
    description: 'Darbu foto galerija.',
    fields: [
      SEO,
      {
        kind: 'section',
        label: 'Augšdaļa',
        fields: [
          { kind: 'text', key: 'heading', label: 'Virsraksts' },
          { kind: 'text', key: 'intro', label: 'Ievads', multiline: true },
        ],
      },
      {
        kind: 'list',
        key: 'photos',
        label: 'Galerija',
        itemLabel: 'Foto',
        max: 60,
        fields: [
          { kind: 'image', key: 'image', label: 'Foto' },
          { kind: 'text', key: 'caption', label: 'Paraksts', hint: 'Redzams, uzbraucot ar peli, un apraksta foto tiem, kas to neredz.' },
        ],
        newItem: () => ({ image: null, caption: emptyText() }),
      },
    ],
  },
  contact: {
    name: 'Kontakti',
    publicPath: '/kontakti',
    description: 'Lapas virsraksti. Pašus kontaktus maina sadaļā "Kontaktinformācija".',
    fields: [
      SEO,
      {
        kind: 'section',
        label: 'Virsraksti',
        fields: [
          { kind: 'text', key: 'heading', label: 'Lapas virsraksts' },
          { kind: 'text', key: 'phoneHeading', label: 'Tālruņa kartīte' },
          { kind: 'text', key: 'addressHeading', label: 'Adreses kartīte' },
          { kind: 'text', key: 'emailHeading', label: 'E-pasta kartīte' },
          { kind: 'text', key: 'hoursHeading', label: 'Darba laika kartīte' },
        ],
      },
    ],
  },
  'site-info': {
    name: 'Kontaktinformācija',
    publicPath: '/kontakti',
    description: 'Tālrunis, adrese, darba laiks un rekvizīti: lapā Kontakti, galvenē, kājenē un e-pastos.',
    fields: [
      {
        kind: 'section',
        label: 'Saziņa',
        fields: [
          { kind: 'plain', key: 'phone', label: 'Tālrunis', type: 'tel', hint: 'Kā to rādīt, piemēram +371 288 03 119.' },
          { kind: 'plain', key: 'whatsapp', label: 'WhatsApp numurs', type: 'tel' },
          { kind: 'plain', key: 'email', label: 'E-pasts', type: 'email' },
        ],
      },
      {
        kind: 'section',
        label: 'Adrese',
        fields: [
          { kind: 'plain', key: 'street', label: 'Iela un numurs' },
          { kind: 'plain', key: 'city', label: 'Pilsēta un pasta indekss', hint: 'Kartes saites veido no adreses.' },
        ],
      },
      {
        kind: 'list',
        key: 'hours',
        label: 'Darba laiks',
        itemLabel: 'Rinda',
        max: 7,
        fields: [
          { kind: 'text', key: 'day', label: 'Dienas' },
          { kind: 'text', key: 'time', label: 'Laiks', hint: 'Piemēram 9:00–17:00 vai Brīvdiena.' },
        ],
        newItem: () => ({ day: emptyText(), time: emptyText() }),
      },
      {
        kind: 'section',
        label: 'Sociālie tīkli',
        fields: [
          { kind: 'plain', key: 'instagramUrl', label: 'Instagram adrese', type: 'url' },
          { kind: 'plain', key: 'facebookUrl', label: 'Facebook adrese', type: 'url' },
        ],
      },
      {
        kind: 'section',
        label: 'Rekvizīti',
        fields: [
          { kind: 'plain', key: 'companyName', label: 'Uzņēmums' },
          { kind: 'plain', key: 'registrationNumber', label: 'Reģistrācijas numurs' },
          { kind: 'plain', key: 'vatNumber', label: 'PVN numurs' },
          { kind: 'plain', key: 'bankAccount', label: 'Bankas konts' },
        ],
      },
    ],
  },
};

export const PAGE_ORDER: PageName[] = ['home', 'about', 'services', 'works', 'contact', 'site-info'];
