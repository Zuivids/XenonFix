import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { PageName } from '../../pages/page-api';
import { errorMessage } from '../admin-errors';
import { EditableText, PageAdminApi, PageDocument } from './page-admin-api';
import { getAt, labelFor, move, Problem, problems, setAt, withoutLanguage } from './page-editing';
import { Field, PAGE_SCHEMAS } from './page-schemas';

type Language = 'lv' | 'ru' | 'en';

const LANGUAGES: { code: Language; name: string }[] = [
  { code: 'lv', name: 'Latviešu' },
  { code: 'ru', name: 'Krievu' },
  { code: 'en', name: 'Angļu' },
];

/**
 * Edits one page, field by field, in the order the page shows them (page-schemas.ts). Saving
 * sends the whole page; the site shows it at once.
 */
@Component({
  selector: 'app-page-editor',
  imports: [DatePipe, NgTemplateOutlet, RouterLink],
  templateUrl: './page-editor.html',
  styleUrls: ['../admin.css', './page-editor.css'],
})
export class PageEditor implements OnInit {
  private readonly api = inject(PageAdminApi);

  /** From the address (see withComponentInputBinding). */
  readonly page = input.required<string>();

  protected readonly languages = LANGUAGES;
  protected readonly schema = computed(() => PAGE_SCHEMAS[this.page() as PageName]);
  protected readonly language = signal<Language>('lv');
  protected readonly content = signal<PageDocument | null>(null);
  protected readonly version = signal(0);
  protected readonly updated = signal<{ at: string; by: string | null } | null>(null);
  protected readonly busy = signal(false);
  protected readonly uploading = signal<string | null>(null);
  protected readonly saved = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly changedByAnother = signal(false);
  protected readonly problemList = signal<Problem[]>([]);
  /** Set by every change, so the page asks before leaving with unsaved changes. */
  readonly dirty = signal(false);
  /** Changes to the content object do not change the signal, so this makes the view look again. */
  private readonly revision = signal(0);

  protected readonly invalidPaths = computed(() => new Set(this.problemList().map((problem) => problem.path)));

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  protected async load(): Promise<void> {
    this.error.set(null);
    this.changedByAnother.set(false);
    try {
      const page = await firstValueFrom(this.api.get(this.page() as PageName));
      this.content.set(page.content);
      this.version.set(page.version);
      this.updated.set({ at: page.updatedAt, by: page.updatedBy });
      this.dirty.set(false);
      this.problemList.set([]);
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }

  protected async save(): Promise<void> {
    const content = this.content()!;
    const found = problems(this.schema().fields, content);
    this.problemList.set(found);
    this.saved.set(false);
    if (found.length) {
      this.error.set('Lapa nav saglabāta. Aizpildiet iezīmētos laukus.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const page = await firstValueFrom(this.api.save(this.page() as PageName, content, this.version()));
      this.content.set(page.content);
      this.version.set(page.version);
      this.updated.set({ at: page.updatedAt, by: page.updatedBy });
      this.dirty.set(false);
      this.saved.set(true);
    } catch (error) {
      const code = error instanceof HttpErrorResponse ? error.error?.code : undefined;
      if (code === 'CHANGED_BY_ANOTHER') {
        // What was typed stays, until the admin chooses to load the other version
        this.changedByAnother.set(true);
        this.error.set('Kāds cits tikko saglabāja šo lapu. Jūsu izmaiņas nav saglabātas.');
      } else if (code === 'INVALID_PAGE_CONTENT' && error instanceof HttpErrorResponse && error.error?.field) {
        const path = withoutLanguage(error.error.field);
        const label = labelFor(this.schema().fields, path) ?? path;
        this.problemList.set([{ path, message: `${label}: pārbaudiet pierakstu vai garumu.` }]);
        this.error.set('Lapa nav saglabāta.');
      } else {
        this.error.set(errorMessage(error));
      }
    } finally {
      this.busy.set(false);
    }
  }

  // Reading values; each reads the revision, so the view follows changes made in place

  protected text(target: object, key: string | null): string {
    this.revision();
    const value = (key ? getAt(target as PageDocument, key) : target) as EditableText | undefined;
    return value?.[this.language()] ?? '';
  }

  /** In Russian and English, the Latvian text shows where there is no translation. */
  protected latvian(target: object, key: string | null): string {
    const value = (key ? getAt(target as PageDocument, key) : target) as EditableText | undefined;
    return this.language() === 'lv' ? '' : (value?.lv ?? '');
  }

  protected plain(target: object, key: string): string {
    this.revision();
    return String(getAt(target as PageDocument, key) ?? '');
  }

  protected image(target: object, key: string): { src: string; width: number; height: number } | null {
    this.revision();
    return (getAt(target as PageDocument, key) as { src: string; width: number; height: number } | null) ?? null;
  }

  /** A list's items: each an object, or a text in three languages. */
  protected items(target: object, key: string): object[] {
    this.revision();
    return (getAt(target as PageDocument, key) as object[] | undefined) ?? [];
  }

  protected invalid(path: string): boolean {
    return this.invalidPaths().has(path);
  }

  // Changing values

  protected setText(target: object, key: string | null, event: Event): void {
    const value = (event.target as HTMLInputElement | HTMLTextAreaElement).value;
    const text = ((key ? getAt(target as PageDocument, key) : target) ?? { lv: '', ru: '', en: '' }) as EditableText;
    text[this.language()] = value;
    if (key) {
      setAt(target as PageDocument, key, text);
    }
    this.changed();
  }

  protected setPlain(target: object, key: string, event: Event): void {
    setAt(target as PageDocument, key, (event.target as HTMLInputElement).value);
    this.changed();
  }

  protected async upload(target: object, key: string, path: string, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.uploading.set(path);
    this.error.set(null);
    try {
      setAt(target as PageDocument, key, await firstValueFrom(this.api.upload(file)));
      this.changed();
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.uploading.set(null);
    }
  }

  protected add(target: object, field: Field & { kind: 'list' }): void {
    const items = getAt(target as PageDocument, field.key) as unknown[] | undefined;
    if (items) {
      items.push(field.newItem());
    } else {
      setAt(target as PageDocument, field.key, [field.newItem()]);
    }
    this.changed();
  }

  protected remove(target: object, key: string, index: number): void {
    (getAt(target as PageDocument, key) as unknown[]).splice(index, 1);
    this.changed();
  }

  protected move(target: object, key: string, index: number, step: -1 | 1): void {
    move(getAt(target as PageDocument, key) as unknown[], index, step);
    this.changed();
  }

  private changed(): void {
    this.dirty.set(true);
    this.saved.set(false);
    this.revision.update((value) => value + 1);
    if (this.problemList().length) {
      this.problemList.set(problems(this.schema().fields, this.content()!));
    }
  }
}
