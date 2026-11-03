// Latvian is the source language: the other languages must have exactly these keys,
// otherwise the build fails.
export const lv = {
  'site.languageSwitcher': 'Valoda',
  'home.title': 'XenonFix — lukturu remonts un pārdošana',
  'home.description': 'XenonFix: automašīnu lukturu remonts un lukturu pārdošana. Jaunā mājaslapa tiek gatavota.',
  'home.heading': 'Lukturu remonts un pārdošana',
  'home.intro': 'Jaunā XenonFix mājaslapa tiek gatavota.',
  'notFound.title': 'Lapa nav atrasta — XenonFix',
  'notFound.heading': 'Lapa nav atrasta',
  'notFound.backHome': 'Uz sākumlapu',
};

export type MessageKey = keyof typeof lv;
export type Messages = Record<MessageKey, string>;
