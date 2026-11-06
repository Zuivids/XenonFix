import { Component, input, output, signal } from '@angular/core';

/**
 * New backup codes, shown once: the backend keeps only their hashes. Continuing needs a tick
 * that they are saved, as they cannot be shown again.
 */
@Component({
  selector: 'app-recovery-codes',
  template: `
    <h2>Rezerves kodi</h2>
    <p>
      Ja tālrunis pazūd, ar katru no šiem kodiem var pieslēgties vienu reizi. Izdrukājiet vai pierakstiet tos un
      glabājiet drošā vietā. Tie vairs netiks parādīti.
    </p>
    <ol class="codes" aria-label="Rezerves kodi">
      @for (code of codes(); track code) {
        <li><code>{{ code }}</code></li>
      }
    </ol>
    <p class="actions">
      <button type="button" class="secondary" (click)="copy()">Kopēt</button>
      <button type="button" class="secondary" (click)="print()">Drukāt</button>
      @if (copied()) {
        <span role="status">Nokopēts.</span>
      }
    </p>
    <label class="check">
      <input type="checkbox" [checked]="saved()" (change)="saved.set($any($event.target).checked)" />
      Esmu kodus saglabājis
    </label>
    <p>
      <button type="button" [disabled]="!saved()" (click)="done.emit()">{{ doneLabel() }}</button>
    </p>
  `,
  styleUrl: '../admin.css',
  styles: `
    .codes {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(9rem, 1fr));
      gap: 0.5rem 1.5rem;
      max-width: 24rem;
      padding-left: 1.5rem;
    }

    code {
      font-size: 1.0625rem;
      letter-spacing: 0.05em;
    }

    .check {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
  `,
})
export class RecoveryCodesView {
  readonly codes = input.required<string[]>();
  readonly doneLabel = input('Turpināt');
  readonly done = output<void>();

  protected readonly saved = signal(false);
  protected readonly copied = signal(false);

  protected async copy(): Promise<void> {
    await navigator.clipboard.writeText(this.codes().join('\n'));
    this.copied.set(true);
  }

  /** Only the codes, on a page of their own, so the rest of the panel is not printed. */
  protected print(): void {
    const page = window.open('', '_blank', 'width=480,height=640');
    if (!page) {
      return;
    }
    const items = this.codes()
      .map((code) => `<li>${code}</li>`)
      .join('');
    page.document.write(
      `<!doctype html><title>XenonFix rezerves kodi</title>` +
        `<body style="font: 16px/1.8 monospace"><h1 style="font: bold 18px sans-serif">XenonFix rezerves kodi</h1>` +
        `<ol>${items}</ol><p style="font: 13px sans-serif">Katrs kods der vienu reizi.</p></body>`,
    );
    page.document.close();
    page.focus();
    page.print();
  }
}
