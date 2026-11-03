import { Injectable, signal } from '@angular/core';
import type { Content } from './content';
import { DEFAULT_LANGUAGE, Language } from './languages';
import type { MessageKey, Messages } from './messages.lv';

interface Dictionary {
  messages: Messages;
  content: Content;
}

async function load(messages: Promise<Messages>, content: Promise<Content>): Promise<Dictionary> {
  return { messages: await messages, content: await content };
}

// Separate chunks, so a visitor only downloads the language they read
const LOADERS: Record<Language, () => Promise<Dictionary>> = {
  lv: () => load(import('./messages.lv').then((m) => m.lv), import('./content.lv').then((m) => m.lvContent)),
  ru: () => load(import('./messages.ru').then((m) => m.ru), import('./content.ru').then((m) => m.ruContent)),
  en: () => load(import('./messages.en').then((m) => m.en), import('./content.en').then((m) => m.enContent)),
};

@Injectable({ providedIn: 'root' })
export class I18n {
  readonly language = signal<Language>(DEFAULT_LANGUAGE);
  private readonly dictionary = signal<Dictionary | null>(null);

  /** Runs at startup and before each navigation, so templates never see a missing dictionary. */
  async use(language: Language): Promise<void> {
    const dictionary = await LOADERS[language]();
    this.dictionary.set(dictionary);
    this.language.set(language);
  }

  readonly t = (key: MessageKey): string => this.dictionary()?.messages[key] ?? key;

  readonly content = (): Content => this.dictionary()!.content;
}
