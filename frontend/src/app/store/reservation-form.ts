import { Component, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { I18n } from '../i18n/i18n';
import { LocalizedPipe } from '../i18n/localized-link.pipe';
import { MessageKey } from '../i18n/messages.lv';
import { errorCode, StoreApi } from './store-api';

/** The same rules as the backend's, so most mistakes are shown before anything is sent. */
const PHONE = /^\s*\+?[0-9][0-9 ()-]{5,28}\s*$/;
const MAX_QUANTITY = 10;

const ERROR_MESSAGES: Record<string, MessageKey> = {
  OUT_OF_STOCK: 'reservation.errorOutOfStock',
  PRODUCT_NOT_FOUND: 'reservation.errorGone',
  TOO_MANY_REQUESTS: 'reservation.errorTooMany',
};

@Component({
  selector: 'app-reservation-form',
  imports: [LocalizedPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './reservation-form.html',
  styleUrl: './reservation-form.css',
})
export class ReservationForm {
  private readonly api = inject(StoreApi);
  private readonly i18n = inject(I18n);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly t = this.i18n.t;
  protected readonly quantities = Array.from({ length: MAX_QUANTITY }, (_unused, index) => index + 1);

  readonly slug = input.required<string>();
  /** Someone else got the last one first, so the page must look at the stock again. */
  readonly outOfStock = output<void>();

  protected readonly busy = signal(false);
  protected readonly reference = signal<string | null>(null);
  protected readonly error = signal<MessageKey | null>(null);

  protected readonly form = this.formBuilder.group({
    quantity: [1, [Validators.required, Validators.min(1), Validators.max(MAX_QUANTITY)]],
    name: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)]],
    phone: ['', [Validators.required, Validators.maxLength(30), Validators.pattern(PHONE)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    comment: ['', Validators.maxLength(1000)],
    privacyAccepted: [false, Validators.requiredTrue],
    website: [''],
  });

  protected shouldShowError(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  protected async submit(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    try {
      const result = await firstValueFrom(
        this.api.reserve({ ...this.form.getRawValue(), slug: this.slug(), language: this.i18n.language() }),
      );
      this.reference.set(result.reference);
    } catch (error) {
      const code = errorCode(error);
      this.error.set((code && ERROR_MESSAGES[code]) || 'reservation.errorGeneric');
      if (code === 'OUT_OF_STOCK') {
        this.outOfStock.emit();
      }
    } finally {
      this.busy.set(false);
    }
  }
}
