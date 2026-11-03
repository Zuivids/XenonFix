import { RenderMode, ServerRoute } from '@angular/ssr';
import { PAGE_PATHS } from './app.routes';

const LANGUAGE_PREFIXES = ['', 'ru/', 'en/'];

export const serverRoutes: ServerRoute[] = [
  { path: 'lv', renderMode: RenderMode.Server, status: 301 },
  // Rendered in the browser: the panel has nothing to index and needs the login cookie
  { path: 'admin/**', renderMode: RenderMode.Client },
  { path: '', renderMode: RenderMode.Server },
  { path: 'ru', renderMode: RenderMode.Server },
  { path: 'en', renderMode: RenderMode.Server },
  ...LANGUAGE_PREFIXES.flatMap((prefix) =>
    PAGE_PATHS.map((page) => ({ path: prefix + page, renderMode: RenderMode.Server as const })),
  ),
  // Real 404 status for unknown pages, so search engines do not index them
  { path: 'ru/**', renderMode: RenderMode.Server, status: 404 },
  { path: 'en/**', renderMode: RenderMode.Server, status: 404 },
  { path: '**', renderMode: RenderMode.Server, status: 404 },
];
