import { Component, computed, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { I18n } from '../i18n/i18n';
import { WorksPage } from '../pages/page-api';
import { PageUnavailable } from '../pages/page-unavailable';
import { SiteInfo } from '../pages/site-info';

@Component({
  selector: 'app-works',
  imports: [PageUnavailable],
  template: `
    @if (page(); as p) {
      <header class="page-head">
        <div class="container">
          <p class="eyebrow">XenonFix</p>
          <h1>{{ p.heading }}</h1>
          <p class="lead">{{ p.intro }}</p>
          <div class="button-row social">
            <a class="button" [href]="info().instagramUrl" rel="noopener">Instagram</a>
            <a class="button secondary" [href]="info().facebookUrl" rel="noopener">Facebook</a>
          </div>
        </div>
      </header>

      <div class="container page-body">
        <ul class="gallery">
          @for (photo of p.photos; track $index) {
            <li class="reveal">
              <button type="button" class="photo" (click)="open($index)">
                <span class="frame zoom">
                  <img
                    [src]="photo.image.src"
                    [alt]="photo.caption"
                    [width]="photo.image.width"
                    [height]="photo.image.height"
                    loading="lazy"
                  />
                </span>
                <span class="caption">{{ photo.caption }}</span>
              </button>
            </li>
          }
        </ul>
      </div>
    } @else {
      <app-page-unavailable />
    }

    <dialog #viewer class="viewer" (click)="close()" (close)="selected.set(null)">
      @if (shown(); as photo) {
        <img [src]="photo.image.src" [alt]="photo.caption" [width]="photo.image.width" [height]="photo.image.height" />
        <p>{{ photo.caption }}</p>
      }
      <button type="button" class="close" [attr.aria-label]="t('works.close')" (click)="close()">×</button>
    </dialog>
  `,
  styles: `
    .social {
      margin-top: 1.75rem;
    }

    .gallery {
      columns: 1;
      column-gap: 1rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .gallery li {
      break-inside: avoid;
      margin-bottom: 1rem;
    }

    .photo {
      position: relative;
      display: block;
      width: 100%;
      padding: 0;
      border: 1px solid var(--line);
      border-radius: 1.25rem;
      overflow: hidden;
      background: var(--bg);
      color: var(--text);
      font: inherit;
      text-align: left;
      cursor: zoom-in;
      transition:
        border-color 0.45s var(--ease),
        box-shadow 0.45s var(--ease);
    }

    .photo:hover {
      border-color: var(--line-strong);
      box-shadow: 0 1.5rem 3.5rem var(--shadow);
    }

    .frame {
      display: block;
    }

    .caption {
      position: absolute;
      right: 0;
      bottom: 0;
      left: 0;
      padding: 0.75rem 1.25rem;
      background: rgb(255 255 255 / 92%);
      font-weight: 700;
      transition:
        opacity 0.45s var(--ease),
        transform 0.45s var(--ease);
    }

    @media (hover: hover) {
      .caption {
        opacity: 0;
        transform: translateY(0.75rem);
      }

      .photo:hover .caption,
      .photo:focus-visible .caption {
        opacity: 1;
        transform: none;
      }
    }

    .viewer {
      max-width: min(92vw, 75rem);
      max-height: 92vh;
      padding: 0;
      border: 0;
      background: none;
      color: var(--text);
      overflow: visible;
      cursor: zoom-out;
    }

    .viewer[open] {
      animation: zoom-in 0.4s var(--ease);
    }

    .viewer::backdrop {
      background: rgb(244 246 252 / 95%);
      backdrop-filter: blur(8px);
    }

    @keyframes zoom-in {
      from {
        opacity: 0;
        transform: scale(0.92);
      }
    }

    .viewer img {
      display: block;
      width: auto;
      max-width: 100%;
      height: auto;
      max-height: 84vh;
      margin-inline: auto;
      border-radius: 0.75rem;
      box-shadow: 0 1.5rem 4rem var(--shadow);
    }

    .viewer p {
      margin: 0.75rem 0 0;
      font-weight: 700;
      text-align: center;
    }

    .close {
      position: fixed;
      top: 1rem;
      right: 1rem;
      width: 3rem;
      height: 3rem;
      border: 1px solid var(--line-strong);
      border-radius: 50%;
      background: var(--bg);
      color: var(--text);
      font-size: 1.5rem;
      line-height: 1;
      cursor: pointer;
      transition:
        background-color 0.3s var(--ease),
        color 0.3s var(--ease),
        transform 0.3s var(--ease);
    }

    .close:hover {
      background: var(--accent);
      color: #fff;
      transform: rotate(90deg);
    }

    @media (min-width: 38rem) {
      .gallery {
        columns: 2;
      }
    }

    @media (min-width: 62rem) {
      .gallery {
        columns: 3;
      }
    }
  `,
})
export class Works {
  protected readonly t = inject(I18n).t;
  protected readonly info = inject(SiteInfo).details;

  /** From the route's resolver; null when it could not be loaded. */
  readonly page = input<WorksPage | null>(null);

  private readonly viewer = viewChild.required<ElementRef<HTMLDialogElement>>('viewer');
  /** The open photo's position, counted from 1 so that @if treats the first one as present. */
  protected readonly selected = signal<number | null>(null);
  protected readonly shown = computed(() => {
    const index = this.selected();
    return index ? (this.page()?.photos[index - 1] ?? null) : null;
  });

  protected open(index: number): void {
    this.selected.set(index + 1);
    this.viewer().nativeElement.showModal();
  }

  protected close(): void {
    this.viewer().nativeElement.close();
  }
}
