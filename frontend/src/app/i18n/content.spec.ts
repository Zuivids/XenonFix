import type { Content } from './content';
import { enContent } from './content.en';
import { lvContent } from './content.lv';
import { ruContent } from './content.ru';

/** The text with its words taken out: what has to match between languages. */
function shape(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(shape);
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, inner]) => [key, shape(inner)]));
  }
  return 'text';
}

describe('Translated content', () => {
  it.each<[string, Content]>([
    ['ru', ruContent],
    ['en', enContent],
  ])('%s has the same structure as Latvian', (_language, content) => {
    expect(shape(content)).toEqual(shape(lvContent));
  });
});
