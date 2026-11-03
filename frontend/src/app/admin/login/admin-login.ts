import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { errorMessage } from '../admin-errors';
import { AdminSession } from '../admin-session';

@Component({
  selector: 'app-admin-login',
  imports: [ReactiveFormsModule],
  template: `
    <main>
      <img src="logo.png" alt="XenonFix" width="644" height="192" />
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
    </main>
  `,
  styleUrls: ['../admin.css', './admin-login.css'],
})
export class AdminLogin {
  private readonly session = inject(AdminSession);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', Validators.required],
    password: ['', Validators.required],
  });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.error.set('Ievadiet e-pastu un paroli.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const { email, password } = this.form.getRawValue();
      await this.session.login(email, password);
      await this.router.navigateByUrl('/admin');
    } catch (error) {
      this.form.controls.password.reset();
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
