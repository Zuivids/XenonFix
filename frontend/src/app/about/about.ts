import { Component, inject } from '@angular/core';
import { I18n } from '../i18n/i18n';

@Component({
  selector: 'app-about',
  template: `
    <header class="page-head">
      <div class="container">
        <p class="eyebrow">XenonFix</p>
        <h1>{{ t('about.heading') }}</h1>
        <p class="lead">{{ t('about.lead') }}</p>
      </div>
    </header>

    <div class="container page-body">
      <ol class="promises">
        @for (paragraph of content().aboutIntro; track $index) {
          <li class="lift reveal">
            <span class="number" aria-hidden="true">0{{ $index + 1 }}</span>
            <p>{{ paragraph }}</p>
          </li>
        }
      </ol>

      <section class="goal reveal">
        <h2 class="section-title">{{ t('about.goalHeading') }}</h2>
        <div>
          @for (paragraph of content().aboutGoal; track $index) {
            <p>{{ paragraph }}</p>
          }
        </div>
      </section>

      <section class="manager">
        <div class="portrait zoom reveal">
          <img
            src="images/ugis-zvingulis.jpg"
            [alt]="t('about.managerName')"
            width="640"
            height="960"
            loading="lazy"
          />
        </div>
        <div class="reveal">
          <p class="eyebrow">{{ t('about.managerRole') }}</p>
          <h2 class="section-title">{{ t('about.managerName') }}</h2>
          @for (paragraph of content().aboutManager; track $index) {
            <p>{{ paragraph }}</p>
          }
        </div>
      </section>
    </div>
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
  private readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  protected readonly content = this.i18n.content;
}
