import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../admin-errors';
import { AdminSession } from '../admin-session';

@Component({
  selector: 'app-admin-login',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <main>
      <a class="logo" routerLink="/" aria-label="XenonFix - uz sākumlapu">
        <img src="logo.png" alt="" width="644" height="192" />
      </a>
      @if (stage() === 'password') {
        <h1>Pieslēgšanās</h1>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <label>
            E-pasts
            <input type="email" formControlName="email" autocomplete="username" />
          </label>
          <label>
            Parole
            <input type="password" formControlName="password" autocomplete="current-password" />
          </label>
          @if (error(); as message) {
            <p class="error" role="alert">{{ message }}</p>
          }
          <button type="submit" [disabled]="busy()">Pieslēgties</button>
        </form>
      } @else {
        <h1>Apstipriniet pieslēgšanos</h1>
        <form [formGroup]="codeForm" (ngSubmit)="submitCode()">
          @if (useBackupCode()) {
            <label>
              Rezerves kods
              <input type="text" formControlName="code" autocomplete="off" autocapitalize="characters" spellcheck="false" />
              <span class="hint">Viens no kodiem, ko saņēmāt, iestatot lietotni. Katrs der vienu reizi.</span>
            </label>
          } @else {
            <label>
              Kods no lietotnes
              <input
                type="text"
                formControlName="code"
                inputmode="numeric"
                autocomplete="one-time-code"
              />
              <span class="hint">6 cipari no autentifikatora lietotnes tālrunī.</span>
            </label>
          }
          @if (error(); as message) {
            <p class="error" role="alert">{{ message }}</p>
          }
          <button type="submit" [disabled]="busy()">Apstiprināt</button>
        </form>
        <p class="links">
          <button type="button" class="link" (click)="toggleBackupCode()">
            {{ useBackupCode() ? 'Izmantot kodu no lietotnes' : 'Nav tālruņa? Izmantot rezerves kodu' }}
          </button>
          <button type="button" class="link" (click)="startOver()">Sākt no jauna</button>
        </p>
      }
    </main>
  `,
  styleUrls: ['../admin.css', './admin-login.css'],
})
export class AdminLogin implements OnInit {
  private readonly session = inject(AdminSession);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly form = this.formBuilder.group({
    email: ['', Validators.required],
    password: ['', Validators.required],
  });
  protected readonly codeForm = this.formBuilder.group({ code: ['', Validators.required] });
  protected readonly stage = signal<'password' | 'code'>('password');
  protected readonly useBackupCode = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Opened again while the code is awaited, for example after a reload: continue there. */
  async ngOnInit(): Promise<void> {
    try {
      const me = await this.session.load();
      if (me?.step === 'MFA_REQUIRED') {
        this.stage.set('code');
      }
    } catch {
      // The password form works regardless
    }
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.error.set('Ievadiet e-pastu un paroli.');
      return;
    }
    await this.run(async () => {
      const { email, password } = this.form.getRawValue();
      const me = await this.session.login(email, password);
      this.form.controls.password.reset();
      if (me.step === 'MFA_REQUIRED') {
        this.stage.set('code');
      } else {
        // The guard opens the setup page or the panel, as the step requires
        await this.router.navigateByUrl('/admin');
      }
    }, () => this.form.controls.password.reset());
  }

  protected async submitCode(): Promise<void> {
    if (this.codeForm.invalid) {
      this.error.set(this.useBackupCode() ? 'Ievadiet rezerves kodu.' : 'Ievadiet 6 ciparu kodu.');
      return;
    }
    await this.run(async () => {
      await this.session.verifyMfa(this.codeForm.getRawValue().code.trim());
      await this.router.navigateByUrl('/admin');
    }, () => this.codeForm.reset());
  }

  protected toggleBackupCode(): void {
    this.useBackupCode.set(!this.useBackupCode());
    this.codeForm.reset();
    this.error.set(null);
  }

  /** Back to the password, for another account or when the code step ran out of time. */
  protected async startOver(): Promise<void> {
    try {
      await this.session.logout();
    } catch {
      // Already logged out
    }
    this.backToPassword(null);
  }

  private backToPassword(message: string | null): void {
    this.stage.set('password');
    this.useBackupCode.set(false);
    this.codeForm.reset();
    this.error.set(message);
  }

  private async run(action: () => Promise<void>, onError: () => void): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (error) {
      onError();
      if (this.stage() === 'code' && error instanceof HttpErrorResponse && error.status === 401) {
        this.backToPassword('Pieslēgšanās laiks beidzās. Lūdzu, ievadiet paroli vēlreiz.');
      } else {
        this.error.set(errorMessage(error));
      }
    } finally {
      this.busy.set(false);
    }
  }
}
