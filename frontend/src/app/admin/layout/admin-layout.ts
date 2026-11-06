import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AdminSession } from '../admin-session';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <!-- Built like the public site's header, with red instead of blue so the admin panel is never
         mistaken for the public site -->
    <header class="admin-header">
      <div class="container header-row">
        <a class="brand" routerLink="/admin" aria-label="XenonFix administrēšana">
          <img src="logo.png" alt="" width="644" height="192" />
        </a>
        @if (session.me(); as me) {
          <nav
            id="admin-nav"
            class="admin-nav"
            [class.open]="menuOpen()"
            aria-label="Administrēšana"
            (click)="menuOpen.set(false)"
          >
            @if (me.step === 'READY') {
              <a routerLink="/admin/reservations" routerLinkActive="active">Rezervācijas</a>
              <a routerLink="/admin/products" routerLinkActive="active">Produkti</a>
              <a routerLink="/admin/admins" routerLinkActive="active">Administratori</a>
              <a routerLink="/admin/audit" routerLinkActive="active">Notikumu žurnāls</a>
            }
            @if (me.step === 'READY' || me.step === 'PASSWORD_CHANGE_REQUIRED') {
              <a routerLink="/admin/password" routerLinkActive="active">Mainīt paroli</a>
            }
          </nav>
          <div class="account">
            <span class="name">{{ me.displayName }}</span>
            <button type="button" class="logout" (click)="logout()">Iziet</button>
            @if (me.step === 'READY' || me.step === 'PASSWORD_CHANGE_REQUIRED') {
              <button
                type="button"
                class="menu-toggle"
                [class.open]="menuOpen()"
                aria-label="Izvēlne"
                [attr.aria-expanded]="menuOpen()"
                aria-controls="admin-nav"
                (click)="menuOpen.set(!menuOpen())"
              >
                <span></span>
                <span></span>
              </button>
            }
          </div>
        }
      </div>
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

  protected readonly menuOpen = signal(false);

  protected async logout(): Promise<void> {
    try {
      await this.session.logout();
    } finally {
      await this.router.navigateByUrl('/admin/login');
    }
  }
}
