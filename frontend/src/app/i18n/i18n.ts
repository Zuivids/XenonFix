import { Injectable, signal } from '@angular/core';
import { DEFAULT_LANGUAGE, Language } from './languages';
import type { MessageKey, Messages } from './messages.lv';

// Separate chunks, so a visitor only downloads the language they read
const LOADERS: Record<Language, () => Promise<Messages>> = {
  lv: () => import('./messages.lv').then((m) => m.lv),
  ru: () => import('./messages.ru').then((m) => m.ru),
  en: () => import('./messages.en').then((m) => m.en),
};

@Injectable({ providedIn: 'root' })
export class I18n {
  readonly language = signal<Language>(DEFAULT_LANGUAGE);
  private readonly messages = signal<Messages | null>(null);

  /** Runs at startup and before each navigation, so templates never see a missing dictionary. */
  async use(language: Language): Promise<void> {
    const messages = await LOADERS[language]();
    this.messages.set(messages);
    this.language.set(language);
  }

  readonly t = (key: MessageKey): string => this.messages()?.[key] ?? key;
}
