import { Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { errorMessage } from '../admin-errors';
import { AdminSession } from '../admin-session';

const MIN_PASSWORD_LENGTH = 12;

@Component({
  selector: 'app-change-password',
  imports: [ReactiveFormsModule],
  template: `
    <h1>Mainīt paroli</h1>
    @if (required()) {
      <p role="status">Šī ir pagaidu parole. Lai turpinātu, nomainiet to pret savu.</p>
    }
    <form [formGroup]="form" (ngSubmit)="submit()">
      <label>
        Pašreizējā parole
        <input type="password" formControlName="currentPassword" autocomplete="current-password" />
      </label>
      <label>
        Jaunā parole
        <input type="password" formControlName="newPassword" autocomplete="new-password" />
        <span class="hint">Vismaz {{ minLength }} rakstzīmes.</span>
      </label>
      <label>
        Jaunā parole vēlreiz
        <input type="password" formControlName="repeatPassword" autocomplete="new-password" />
      </label>
      @if (error(); as message) {
        <p class="error" role="alert">{{ message }}</p>
      }
      @if (saved()) {
        <p role="status">Parole nomainīta.</p>
      }
      <button type="submit" [disabled]="busy()">Saglabāt</button>
    </form>
  `,
  styleUrl: '../admin.css',
})
export class ChangePassword {
  private readonly session = inject(AdminSession);
  private readonly router = inject(Router);

  protected readonly minLength = MIN_PASSWORD_LENGTH;
  protected readonly required = computed(() => this.session.me()?.passwordChangeRequired ?? false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH)]],
    repeatPassword: ['', Validators.required],
  });
  protected readonly busy = signal(false);
  protected readonly saved = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    const { currentPassword, newPassword, repeatPassword } = this.form.getRawValue();
    this.saved.set(false);
    if (this.form.invalid) {
      this.error.set(`Aizpildiet visus laukus. Jaunajai parolei jābūt vismaz ${MIN_PASSWORD_LENGTH} rakstzīmēm.`);
      return;
    }
    if (newPassword !== repeatPassword) {
      this.error.set('Jaunās paroles nesakrīt.');
      return;
    }
    const wasRequired = this.required();
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.session.changePassword(currentPassword, newPassword);
      this.form.reset();
      if (wasRequired) {
        await this.router.navigateByUrl('/admin');
      } else {
        this.saved.set(true);
      }
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
