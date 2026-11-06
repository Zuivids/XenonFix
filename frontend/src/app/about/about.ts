import { Component, input } from '@angular/core';
import { AboutPage } from '../pages/page-api';
import { PageUnavailable } from '../pages/page-unavailable';

@Component({
  selector: 'app-about',
  imports: [PageUnavailable],
  template: `
    @if (page(); as p) {
      <header class="page-head">
        <div class="container">
          <p class="eyebrow">XenonFix</p>
          <h1>{{ p.heading }}</h1>
          <p class="lead">{{ p.lead }}</p>
        </div>
      </header>

      <div class="container page-body">
        <ol class="promises">
          @for (paragraph of p.points; track $index) {
            <li class="lift reveal">
              <span class="number" aria-hidden="true">{{ number($index) }}</span>
              <p>{{ paragraph }}</p>
            </li>
          }
        </ol>

        <section class="goal reveal">
          <h2 class="section-title">{{ p.goalHeading }}</h2>
          <div>
            @for (paragraph of p.goal; track $index) {
              <p>{{ paragraph }}</p>
            }
          </div>
        </section>

        <section class="manager">
          <div class="portrait zoom reveal">
            <img
              [src]="p.managerImage.src"
              [alt]="p.managerName"
              [width]="p.managerImage.width"
              [height]="p.managerImage.height"
              loading="lazy"
            />
          </div>
          <div class="reveal">
            <p class="eyebrow">{{ p.managerRole }}</p>
            <h2 class="section-title">{{ p.managerName }}</h2>
            @for (paragraph of p.manager; track $index) {
              <p>{{ paragraph }}</p>
            }
          </div>
        </section>
      </div>
    } @else {
      <app-page-unavailable />
    }
  `,
  styles: `
    .promises {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
      gap: 1rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .promises li {
      padding: 1.5rem;
    }

    .number {
      display: block;
      margin-bottom: 1.5rem;
      color: var(--accent-bright);
      font-size: 0.875rem;
      font-weight: 700;
      letter-spacing: 0.1em;
    }

    .promises p {
      margin: 0;
    }

    .goal,
    .manager {
      display: grid;
      gap: 1.5rem 4rem;
      margin-top: clamp(3.5rem, 8vw, 6.5rem);
    }

    .goal p {
      color: var(--muted);
      font-size: 1.0625rem;
    }

    .goal p:first-child {
      margin-top: 0;
    }

    .manager {
      align-items: center;
    }

    .manager p:not(.eyebrow) {
      color: var(--muted);
    }

    .manager .section-title {
      margin-bottom: 1.25rem;
    }

    .portrait {
      max-width: 26rem;
      aspect-ratio: 4 / 5;
      border: 1px solid var(--line-strong);
      border-radius: 1.25rem;
    }

    @media (min-width: 52rem) {
      .goal {
        grid-template-columns: 1fr 2fr;
      }

      .manager {
        grid-template-columns: minmax(0, 26rem) minmax(0, 1fr);
      }
    }
  `,
})
export class About {
  /** From the route's resolver; null when it could not be loaded. */
  readonly page = input<AboutPage | null>(null);

  protected number(index: number): string {
    return String(index + 1).padStart(2, '0');
  }
}
