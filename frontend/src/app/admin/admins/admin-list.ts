import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { Admin, AdminApi } from '../admin-api';
import { errorMessage } from '../admin-errors';
import { AdminSession } from '../admin-session';

const MIN_PASSWORD_LENGTH = 12;

@Component({
  selector: 'app-admin-list',
  imports: [DatePipe, ReactiveFormsModule],
  template: `
    <h1>Administratori</h1>
    @if (error(); as message) {
      <p class="error" role="alert">{{ message }}</p>
    }
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Vārds</th>
            <th>E-pasts</th>
            <th>Statuss</th>
            <th>Lietotne</th>
            <th>Pēdējā pieslēgšanās</th>
            <th>Darbības</th>
          </tr>
        </thead>
        <tbody>
          @for (admin of admins(); track admin.id) {
            <tr>
              <td>{{ admin.displayName }}</td>
              <td>{{ admin.email }}</td>
              <td>{{ statusText(admin) }}</td>
              <td>{{ admin.mfaEnabled ? 'Iestatīta' : 'Vēl nav' }}</td>
              <td>{{ admin.lastLoginAt ? (admin.lastLoginAt | date: 'dd.MM.yyyy HH:mm') : '—' }}</td>
              <td>
                @if (admin.id === session.me()?.id) {
                  Jūs
                } @else {
                  <div class="actions">
                    <button type="button" class="secondary" (click)="startReset(admin)">Jauna parole</button>
                    @if (admin.mfaEnabled) {
                      <button type="button" class="secondary" (click)="resetMfa(admin)">
                        Atiestatīt divpakāpju pieslēgšanos
                      </button>
                    }
                    <button type="button" class="secondary" (click)="setEnabled(admin, admin.status !== 'ACTIVE')">
                      {{ admin.status === 'ACTIVE' ? 'Atslēgt' : 'Ieslēgt' }}
                    </button>
                  </div>
                }
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>

    @if (resetting(); as admin) {
      <h2>Jauna parole: {{ admin.email }}</h2>
      <form [formGroup]="resetForm" (ngSubmit)="submitReset(admin)">
        <p class="hint">Pagaidu parole. Nododiet to administratoram; pieslēdzoties to vajadzēs nomainīt.</p>
        <label>
          Pagaidu parole
          <input type="text" formControlName="password" autocomplete="off" />
          <span class="hint">Vismaz {{ minLength }} rakstzīmes.</span>
        </label>
        <div class="actions">
          <button type="submit" [disabled]="resetForm.invalid">Saglabāt</button>
          <button type="button" class="secondary" (click)="resetting.set(null)">Atcelt</button>
        </div>
      </form>
    }

    <h2>Pievienot administratoru</h2>
    <form [formGroup]="createForm" (ngSubmit)="create()">
      <label>
        Vārds
        <input type="text" formControlName="displayName" autocomplete="off" />
      </label>
      <label>
        E-pasts
        <input type="email" formControlName="email" autocomplete="off" />
      </label>
      <label>
        Pagaidu parole
        <input type="text" formControlName="password" autocomplete="off" />
        <span class="hint">Vismaz {{ minLength }} rakstzīmes. Pieslēdzoties to vajadzēs nomainīt.</span>
      </label>
      <button type="submit" [disabled]="createForm.invalid">Pievienot</button>
    </form>
  `,
  styleUrl: '../admin.css',
})
export class AdminList implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  protected readonly session = inject(AdminSession);

  protected readonly minLength = MIN_PASSWORD_LENGTH;
  protected readonly admins = signal<Admin[]>([]);
  protected readonly resetting = signal<Admin | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly createForm = this.formBuilder.group({
    displayName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH)]],
  });
  protected readonly resetForm = this.formBuilder.group({
    password: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH)]],
  });

  ngOnInit(): void {
    void this.run(() => this.reload());
  }

  protected statusText(admin: Admin): string {
    if (admin.status === 'DISABLED') {
      return 'Atslēgts';
    }
    if (admin.locked) {
      return 'Īslaicīgi bloķēts';
    }
    return admin.passwordChangeRequired ? 'Jānomaina parole' : 'Aktīvs';
  }

  protected setEnabled(admin: Admin, enabled: boolean): Promise<void> {
    return this.run(async () => {
      await firstValueFrom(this.api.setEnabled(admin.id, enabled));
      await this.reload();
    });
  }

  /** For a lost phone. The admin sets up the app again at their next login. */
  protected resetMfa(admin: Admin): Promise<void> {
    const question =
      `Atiestatīt ${admin.email} divpakāpju pieslēgšanos? ` +
      'Lietotnes iestatījums un rezerves kodi tiks dzēsti, administrators tiks izrakstīts un, ' +
      'nākamreiz pieslēdzoties, iestatīs lietotni no jauna.';
    if (!window.confirm(question)) {
      return Promise.resolve();
    }
    return this.run(async () => {
      await firstValueFrom(this.api.resetMfa(admin.id));
      await this.reload();
    });
  }

  protected startReset(admin: Admin): void {
    this.resetForm.reset();
    this.resetting.set(admin);
  }

  protected submitReset(admin: Admin): Promise<void> {
    return this.run(async () => {
      await firstValueFrom(this.api.resetPassword(admin.id, this.resetForm.getRawValue().password));
      this.resetForm.reset();
      this.resetting.set(null);
      await this.reload();
    });
  }

  protected create(): Promise<void> {
    return this.run(async () => {
      const { email, displayName, password } = this.createForm.getRawValue();
      await firstValueFrom(this.api.createAdmin(email, displayName, password));
      this.createForm.reset();
      await this.reload();
    });
  }

  private async reload(): Promise<void> {
    this.admins.set(await firstValueFrom(this.api.admins()));
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.error.set(null);
    try {
      await action();
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }
}
