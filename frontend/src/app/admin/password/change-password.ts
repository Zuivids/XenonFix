import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AdminApi } from '../admin-api';
import { errorMessage } from '../admin-errors';
import { AdminSession } from '../admin-session';
import { RecoveryCodesView } from '../mfa/recovery-codes';

const MIN_PASSWORD_LENGTH = 12;

@Component({
  selector: 'app-change-password',
  imports: [ReactiveFormsModule, RecoveryCodesView],
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

    @if (!required()) {
      <section class="recovery">
        @if (newCodes(); as codes) {
          <app-recovery-codes [codes]="codes" doneLabel="Gatavs" (done)="newCodes.set(null)" />
        } @else {
          <h2>Rezerves kodi</h2>
          <p>
            @if (codesLeft() !== null) {
              Neizmantoti: {{ codesLeft() }} no 10.
            }
            Ja tie beidzas vai pazuduši, izveidojiet jaunus. Vecie tad vairs nederēs.
          </p>
          <form [formGroup]="codesForm" (ngSubmit)="renewCodes()">
            <label>
              Kods no lietotnes
              <input
                type="text"
                formControlName="code"
                inputmode="numeric"
                autocomplete="one-time-code"
              />
            </label>
            @if (codesError(); as message) {
              <p class="error" role="alert">{{ message }}</p>
            }
            <button type="submit" class="secondary" [disabled]="busy()">Izveidot jaunus rezerves kodus</button>
          </form>
        }
      </section>
    }
  `,
  styleUrl: '../admin.css',
  // A single short form, so it sits in the middle like the login page
  styles: `
    :host {
      display: block;
      max-width: 24rem;
      margin: 0 auto;
      padding-top: 1rem;
    }

    .recovery {
      margin-top: 2.5rem;
      padding-top: 1rem;
      border-top: 1px solid #ddd;
    }
  `,
})
export class ChangePassword implements OnInit {
  private readonly api = inject(AdminApi);
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

  protected readonly codesForm = inject(NonNullableFormBuilder).group({ code: ['', Validators.required] });
  protected readonly codesLeft = signal<number | null>(null);
  protected readonly newCodes = signal<string[] | null>(null);
  protected readonly codesError = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    if (!this.required()) {
      try {
        this.codesLeft.set((await firstValueFrom(this.api.recoveryCodesLeft())).left);
      } catch {
        // The count is only a hint; making new codes still works
      }
    }
  }

  protected async renewCodes(): Promise<void> {
    if (this.codesForm.invalid) {
      this.codesError.set('Ievadiet 6 ciparu kodu.');
      return;
    }
    this.busy.set(true);
    this.codesError.set(null);
    try {
      const result = await firstValueFrom(this.api.renewRecoveryCodes(this.codesForm.getRawValue().code.trim()));
      this.newCodes.set(result.codes);
      this.codesLeft.set(result.codes.length);
    } catch (error) {
      this.codesError.set(errorMessage(error));
    } finally {
      this.codesForm.reset();
      this.busy.set(false);
    }
  }

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
