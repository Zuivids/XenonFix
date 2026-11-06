import { HttpErrorResponse } from '@angular/common/http';

const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'Nepareizs e-pasts vai parole.',
  TOO_MANY_REQUESTS: 'Pārāk daudz mēģinājumu. Lūdzu, mēģiniet vēlreiz pēc brīža.',
  WRONG_CURRENT_PASSWORD: 'Pašreizējā parole nav pareiza.',
  SAME_PASSWORD: 'Jaunajai parolei jāatšķiras no pašreizējās.',
  EMAIL_TAKEN: 'Administrators ar šādu e-pastu jau ir.',
  CANNOT_CHANGE_SELF: 'Šo darbību nevar veikt ar savu kontu.',
  ADMIN_NOT_FOUND: 'Administrators nav atrasts.',
  CHANGED_BY_ANOTHER: 'Kāds cits tikko mainīja šo ierakstu. Pārlādējiet lapu un pārbaudiet izmaiņas.',
  OUT_OF_STOCK: 'Atlikumā nepietiek preču, lai atjaunotu šo rezervāciju.',
  RESERVATION_NOT_FOUND: 'Rezervācija nav atrasta.',
  SLUG_TAKEN: 'Šāda lapas adrese jau ir citam produktam.',
  SALE_PRICE_NOT_LOWER: 'Akcijas cenai jābūt zemākai par cenu.',
  LATVIAN_NAME_REQUIRED: 'Latviskais nosaukums ir obligāts.',
  TRANSLATION_NEEDS_NAME: 'Tulkojumam ar aprakstu vajag arī nosaukumu.',
  PUBLISH_NEEDS_IMAGE: 'Lai publicētu produktu, pievienojiet vismaz vienu foto.',
  LAST_IMAGE_OF_PUBLISHED: 'Publicētam produktam jāpaliek vismaz vienam foto.',
  TOO_MANY_IMAGES: 'Produktam var būt ne vairāk kā 20 foto.',
  INVALID_IMAGE: 'Šis fails nav derīgs JPEG vai PNG attēls.',
  IMAGE_TOO_LARGE: 'Attēls ir pārāk liels. Maksimums ir 60 megapikseļi.',
  PRODUCT_NOT_FOUND: 'Produkts nav atrasts.',
  IMAGE_NOT_FOUND: 'Foto nav atrasts.',
};

/** The backend sends a code, and the text the admin sees is decided here. */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 413) {
    return 'Fails ir pārāk liels. Maksimums ir 20 MB.';
  }
  const code = error instanceof HttpErrorResponse ? error.error?.code : undefined;
  return MESSAGES[code] ?? 'Neizdevās. Lūdzu, mēģiniet vēlreiz.';
}
