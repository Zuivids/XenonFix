import { Component, inject, input } from '@angular/core';
import { I18n } from '../i18n/i18n';
import { Language } from '../i18n/languages';

const LOCALES: Record<Language, string> = { lv: 'lv-LV', ru: 'ru-RU', en: 'en-IE' };

export function formatPrice(amount: number, language: Language): string {
  return new Intl.NumberFormat(LOCALES[language], { style: 'currency', currency: 'EUR' }).format(amount);
}

/** The price, or the sale price with the usual price crossed out. */
@Component({
  selector: 'app-price',
  template: `
    @if (salePrice() !== null) {
      <span class="current sale">{{ format(salePrice()!) }}</span>
      <s class="old">{{ format(price()) }}</s>
    } @else {
      <span class="current">{{ format(price()) }}</span>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem;
    }

    .current {
      font-weight: 700;
    }

    .sale {
      color: var(--accent);
    }

    .old {
      color: var(--muted);
      font-size: 0.875em;
    }
  `,
})
export class Price {
  private readonly language = inject(I18n).language;

  readonly price = input.required<number>();
  readonly salePrice = input<number | null>(null);

  protected format(amount: number): string {
    return formatPrice(amount, this.language());
  }
}
