import type { Field } from './page-schemas';
import type { EditableText } from './page-admin-api';

type Container = Record<string, unknown>;

/** A value by a key such as "seo.title". */
export function getAt(object: Container, key: string): unknown {
  return key.split('.').reduce<unknown>((value, part) => (value as Container | undefined)?.[part], object);
}

export function setAt(object: Container, key: string, value: unknown): void {
  const parts = key.split('.');
  const last = parts.pop()!;
  const parent = parts.reduce<Container>((current, part) => (current[part] ??= {}) as Container, object);
  parent[last] = value;
}

/** Moves an item one place up (-1) or down (+1). */
export function move<T>(items: T[], index: number, step: -1 | 1): void {
  const target = index + step;
  if (target < 0 || target >= items.length) {
    return;
  }
  [items[index], items[target]] = [items[target], items[index]];
}

export interface Problem {
  /** As the backend names it without the language, such as tiles[2].title */
  path: string;
  message: string;
}

/**
 * What must be filled in before saving: every Latvian text, every photo and every plain value,
 * and enough list items. The backend checks the same, and more, on save.
 */
export function problems(fields: Field[], content: Container, prefix = ''): Problem[] {
  const found: Problem[] = [];
  for (const field of fields) {
    if (field.kind === 'section') {
      found.push(...problems(field.fields, content, prefix));
      continue;
    }
    const path = prefix + field.key;
    const value = getAt(content, field.key);
    switch (field.kind) {
      case 'text':
        if (!(value as EditableText | undefined)?.lv?.trim()) {
          found.push({ path, message: `${field.label}: vajag latvisko tekstu.` });
        }
        break;
      case 'plain':
        if (!String(value ?? '').trim()) {
          found.push({ path, message: `${field.label}: nav aizpildīts.` });
        }
        break;
      case 'image':
        if (!value) {
          found.push({ path, message: `${field.label}: nav izvēlēts foto.` });
        }
        break;
      case 'list': {
        const items = (value as unknown[] | undefined) ?? [];
        if (items.length < (field.min ?? 0)) {
          found.push({ path, message: `${field.label}: vajag vismaz ${field.min}.` });
        }
        items.forEach((item, index) => {
          const itemPath = `${path}[${index}]`;
          if (field.fields) {
            found.push(
              ...problems(field.fields, item as Container, `${itemPath}.`).map((problem) => ({
                ...problem,
                message: `${field.itemLabel} ${index + 1}. ${problem.message}`,
              })),
            );
          } else if (!(item as EditableText).lv?.trim()) {
            found.push({ path: itemPath, message: `${field.label}, ${field.itemLabel.toLowerCase()} ${index + 1}: vajag latvisko tekstu.` });
          }
        });
        break;
      }
    }
  }
  return found;
}

/** How the editor calls the field at a path such as "tiles[2].title": "Pakalpojumu flīzes, flīze 3: Nosaukums". */
export function labelFor(fields: Field[], path: string): string | null {
  for (const field of fields) {
    if (field.kind === 'section') {
      const inside = labelFor(field.fields, path);
      if (inside) {
        return inside;
      }
      continue;
    }
    if (path === field.key) {
      return field.label;
    }
    if (field.kind === 'list' && path.startsWith(`${field.key}[`)) {
      const match = /^\[(\d+)\]\.?(.*)$/.exec(path.slice(field.key.length));
      if (match) {
        const item = `${field.label}, ${field.itemLabel.toLowerCase()} ${Number(match[1]) + 1}`;
        const inner = match[2] && field.fields ? labelFor(field.fields, match[2]) : null;
        return inner ? `${item}: ${inner}` : item;
      }
    }
  }
  return null;
}

/** The field a backend error names, such as "tiles[2].title.lv", without its language. */
export function withoutLanguage(path: string): string {
  return path.replace(/\.(lv|ru|en)$/, '');
}
