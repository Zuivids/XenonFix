import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../admin-errors';
import { PageAdminApi, PageSummary } from './page-admin-api';
import { PAGE_ORDER, PAGE_SCHEMAS } from './page-schemas';

@Component({
  selector: 'app-page-list',
  imports: [DatePipe, RouterLink],
  template: `
    <h1>Lapas</h1>
    <p class="hint">Teksti, virsraksti un foto lapās latviešu, krievu un angļu valodā.</p>
    @if (error(); as message) {
      <p class="error" role="alert">{{ message }}</p>
    }
    <ul class="pages">
      @for (name of order; track name) {
        <li>
          <a [routerLink]="['/admin/pages', name]">
            <span class="name">{{ schemas[name].name }}</span>
            <span class="description">{{ schemas[name].description }}</span>
            <span class="updated">
              @if (updated(name); as page) {
                @if (page.updatedBy) {
                  Mainīja {{ page.updatedBy }}, {{ page.updatedAt | date: 'dd.MM.yyyy HH:mm' }}
                } @else {
                  Vēl nav mainīta
                }
              }
            </span>
          </a>
        </li>
      }
    </ul>
  `,
  styleUrl: '../admin.css',
  styles: `
    .pages {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 18rem), 1fr));
      gap: 1rem;
      margin: 1.5rem 0 0;
      padding: 0;
      list-style: none;
    }

    a {
      display: grid;
      gap: 0.375rem;
      height: 100%;
      box-sizing: border-box;
      padding: 1.25rem;
      border: 1px solid #ddd;
      border-radius: 8px;
      color: inherit;
      text-decoration: none;
      transition:
        border-color 0.2s ease,
        box-shadow 0.2s ease,
        transform 0.2s ease;
    }

    a:hover {
      border-color: #1e42eb;
      box-shadow: 0 0.75rem 1.5rem rgb(20 32 70 / 10%);
      transform: translateY(-2px);
    }

    .name {
      font-size: 1.125rem;
      font-weight: 700;
    }

    .description,
    .updated {
      color: #555;
      font-size: 0.9375rem;
    }
  `,
})
export class PageList implements OnInit {
  private readonly api = inject(PageAdminApi);

  protected readonly order = PAGE_ORDER;
  protected readonly schemas = PAGE_SCHEMAS;
  protected readonly pages = signal<PageSummary[]>([]);
  protected readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.pages.set(await firstValueFrom(this.api.list()));
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }

  protected updated(name: string): PageSummary | undefined {
    return this.pages().find((page) => page.page === name);
  }
}
