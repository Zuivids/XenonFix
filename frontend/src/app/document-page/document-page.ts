import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { I18n } from '../i18n/i18n';
import { MessageKey } from '../i18n/messages.lv';

type DocumentName = 'payment' | 'delivery' | 'warranty';

/** A longer text page. The route data says which text and heading. */
@Component({
  selector: 'app-document-page',
  template: `
    <header class="page-head">
      <div class="container">
        <p class="eyebrow">{{ i18n.t('footer.terms') }}</p>
        <h1>{{ i18n.t(heading) }}</h1>
      </div>
    </header>

    <div class="container page-body">
      <div class="reading">
        @for (section of sections(); track $index) {
          <section>
            @if (section.heading) {
              <h2>{{ section.heading }}</h2>
            }
            @for (paragraph of section.paragraphs; track $index) {
              <p>{{ paragraph }}</p>
            }
            @if (section.items) {
              <ul>
                @for (item of section.items; track $index) {
                  <li>{{ item }}</li>
                }
              </ul>
            }
            @if (section.closing) {
              <p>{{ section.closing }}</p>
            }
          </section>
        }
      </div>
    </div>
  `,
  styles: `
    section {
      padding-block: 1.75rem;
      border-bottom: 1px solid var(--line);
    }

    section:first-child {
      padding-top: 0;
    }

    section:last-child {
      border-bottom: 0;
    }

    h2 {
      margin: 0 0 0.875rem;
      font-size: 1.375rem;
    }

    p,
    li {
      color: #3a4252;
    }

    p {
      margin: 0 0 0.875rem;
    }

    p:last-child,
    ul:last-child {
      margin-bottom: 0;
    }

    ul {
      display: grid;
      gap: 0.5rem;
      margin: 0 0 0.875rem;
      padding: 0;
      list-style: none;
    }

    li {
      position: relative;
      padding-left: 1.25rem;
    }

    li::before {
      content: '';
      position: absolute;
      top: 0.7em;
      left: 0;
      width: 0.4375rem;
      height: 0.4375rem;
      border-radius: 50%;
      background: var(--accent-bright);
      box-shadow: 0 0 0.5rem var(--accent-bright);
    }
  `,
})
export class DocumentPage {
  protected readonly i18n = inject(I18n);
  private readonly data = inject(ActivatedRoute).snapshot.data;

  protected readonly heading = this.data['heading'] as MessageKey;
  private readonly documentName = this.data['document'] as DocumentName;
  protected readonly sections = computed(() => this.i18n.content()[this.documentName]);
}
