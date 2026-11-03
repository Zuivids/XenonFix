import { HttpErrorResponse } from '@angular/common/http';

const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'Nepareizs e-pasts vai parole.',
  TOO_MANY_REQUESTS: 'Pārāk daudz mēģinājumu. Lūdzu, mēģiniet vēlreiz pēc brīža.',
  WRONG_CURRENT_PASSWORD: 'Pašreizējā parole nav pareiza.',
  SAME_PASSWORD: 'Jaunajai parolei jāatšķiras no pašreizējās.',
  EMAIL_TAKEN: 'Administrators ar šādu e-pastu jau ir.',
  CANNOT_CHANGE_SELF: 'Šo darbību nevar veikt ar savu kontu.',
  ADMIN_NOT_FOUND: 'Administrators nav atrasts.',
};

/** The backend sends a code, and the text the admin sees is decided here. */
export function errorMessage(error: unknown): string {
  const code = error instanceof HttpErrorResponse ? error.error?.code : undefined;
  return MESSAGES[code] ?? 'Neizdevās. Lūdzu, mēģiniet vēlreiz.';
}
