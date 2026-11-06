import { Component, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AdminApi, Me, MfaSetup } from '../admin-api';
import { errorMessage } from '../admin-errors';
import { AdminSession } from '../admin-session';
import { RecoveryCodesView } from './recovery-codes';

/** Setting up the authenticator app, which every admin must do before using the panel. */
@Component({
  selector: 'app-mfa-setup',
  imports: [ReactiveFormsModule, RecoveryCodesView],
  template: `
    <h1>Divpakāpju pieslēgšanās</h1>
    @if (finished(); as result) {
      <p role="status">Lietotne ir iestatīta. Turpmāk pēc paroles prasīsim kodu no tās.</p>
      <app-recovery-codes [codes]="result.codes" (done)="continue(result.admin)" />
    } @else {
      <p>Pēc paroles būs jāievada arī kods no tālruņa, tāpēc ar paroli vien pieslēgties nevarēs.</p>
      <ol class="steps">
        <li>Instalējiet tālrunī autentifikatora lietotni, piemēram, Google Authenticator vai Microsoft Authenticator.</li>
        <li>Lietotnē pievienojiet kontu un noskenējiet šo QR kodu.</li>
        <li>Ievadiet 6 ciparu kodu, ko rāda lietotne.</li>
      </ol>
      @if (setup(); as details) {
        <img class="qr" [src]="details.qrCode" alt="QR kods autentifikatora lietotnei" width="240" height="240" />
        <details>
          <summary>Nevarat noskenēt? Ievadiet atslēgu ar roku</summary>
          <p><code class="secret">{{ details.secret }}</code></p>
          <p class="hint">Konta veids: pēc laika (TOTP).</p>
        </details>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <label>
            Kods no lietotnes
            <input
              type="text"
              formControlName="code"
              inputmode="numeric"
              autocomplete="one-time-code"
            />
          </label>
          @if (error(); as message) {
            <p class="error" role="alert">{{ message }}</p>
          }
          <button type="submit" [disabled]="busy()">Apstiprināt</button>
        </form>
      } @else if (error(); as message) {
        <p class="error" role="alert">{{ message }}</p>
      }
    }
  `,
  styleUrl: '../admin.css',
  styles: `
    :host {
      display: block;
      max-width: 32rem;
      margin: 0 auto;
      padding-top: 1rem;
    }

    .steps {
      padding-left: 1.25rem;
    }

    .steps li {
      margin-bottom: 0.375rem;
    }

    .qr {
      display: block;
      margin: 1.25rem 0;
      border: 1px solid #ddd;
      border-radius: 8px;
    }

    details {
      margin-bottom: 1.25rem;
    }

    summary {
      cursor: pointer;
      color: #1e42eb;
    }

    .secret {
      font-size: 1.0625rem;
      letter-spacing: 0.08em;
      overflow-wrap: anywhere;
    }
  `,
})
export class MfaSetupPage implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly session = inject(AdminSession);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({ code: ['', Validators.required] });
  protected readonly setup = signal<MfaSetup | null>(null);
  protected readonly finished = signal<{ codes: string[]; admin: Me } | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.setup.set(await firstValueFrom(this.api.mfaSetup()));
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.error.set('Ievadiet 6 ciparu kodu.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      this.finished.set(await firstValueFrom(this.api.completeMfaSetup(this.form.getRawValue().code.trim())));
    } catch (error) {
      this.form.reset();
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }

  /**
   * On to the password change if the password is temporary, or the panel. Chosen here, as the
   * guard does not run again for another page inside the panel.
   */
  protected async continue(admin: Me): Promise<void> {
    this.session.setMe(admin);
    await this.router.navigateByUrl(admin.step === 'PASSWORD_CHANGE_REQUIRED' ? '/admin/password' : '/admin');
  }
}
