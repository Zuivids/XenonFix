import { inject, Pipe, PipeTransform } from '@angular/core';
import { I18n } from './i18n';
import { localizedPath } from './languages';

/** A page in the current language: '/par-mums' | localized is '/ru/par-mums' on a Russian page. */
@Pipe({ name: 'localized', pure: false })
export class LocalizedPipe implements PipeTransform {
  private readonly i18n = inject(I18n);

  transform(path: string): string {
    return localizedPath(this.i18n.language(), path);
  }
}
