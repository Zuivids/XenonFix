import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AdminSession } from '../admin-session';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <header>
      <img src="logo.png" alt="XenonFix" width="644" height="192" />
      @if (session.me(); as me) {
        <nav aria-label="Administrēšana">
          @if (!passwordChangeRequired()) {
            <a routerLink="/admin/reservations" routerLinkActive="active">Rezervācijas</a>
            <a routerLink="/admin/products" routerLinkActive="active">Produkti</a>
            <a routerLink="/admin/admins" routerLinkActive="active">Administratori</a>
            <a routerLink="/admin/audit" routerLinkActive="active">Notikumu žurnāls</a>
          }
          <a routerLink="/admin/password" routerLinkActive="active">Mainīt paroli</a>
        </nav>
        <div class="account">
          <span>{{ me.displayName }}</span>
          <button type="button" (click)="logout()">Iziet</button>
        </div>
      }
    </header>
    <main>
      <router-outlet />
    </main>
  `,
  styleUrl: './admin-layout.css',
})
export class AdminLayout {
  protected readonly session = inject(AdminSession);
  private readonly router = inject(Router);

  protected readonly passwordChangeRequired = computed(
    () => this.session.me()?.passwordChangeRequired ?? false,
  );

  protected async logout(): Promise<void> {
    try {
      await this.session.logout();
    } finally {
      await this.router.navigateByUrl('/admin/login');
    }
  }
}
