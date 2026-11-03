export const LANGUAGES = ['lv', 'ru', 'en'] as const;

export type Language = (typeof LANGUAGES)[number];

export const DEFAULT_LANGUAGE: Language = 'lv';

export const LANGUAGE_NAMES: Record<Language, string> = {
  lv: 'Latviešu',
  ru: 'Русский',
  en: 'English',
};

/**
 * The default language lives at the site root, the same as on the old site, so existing
 * Latvian URLs keep their address. The others are under a prefix: /ru, /en.
 */
export function languagePrefix(language: Language): string {
  return language === DEFAULT_LANGUAGE ? '' : language;
}

/** The same page in another language: localizedPath('ru', '/par-mums') is '/ru/par-mums'. */
export function localizedPath(language: Language, path: string): string {
  const prefix = languagePrefix(language);
  if (!prefix) {
    return path;
  }
  return path === '/' ? `/${prefix}` : `/${prefix}${path}`;
}

/** The language of a URL: '/ru/par-mums' is 'ru', '/par-mums' is the default language. */
export function urlLanguage(url: string): Language {
  const firstSegment = url.split(/[?#]/)[0].split('/')[1];
  return LANGUAGES.find((language) => languagePrefix(language) === firstSegment) ?? DEFAULT_LANGUAGE;
}

/** The page path without language prefix, query or fragment: '/ru/par-mums?a=1' is '/par-mums'. */
export function pagePath(url: string): string {
  const path = url.split(/[?#]/)[0];
  const prefix = languagePrefix(urlLanguage(path));
  return (prefix ? path.slice(prefix.length + 1) : path) || '/';
}
