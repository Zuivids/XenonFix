import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  { path: 'lv', renderMode: RenderMode.Server, status: 301 },
  { path: '', renderMode: RenderMode.Server },
  { path: 'ru', renderMode: RenderMode.Server },
  { path: 'en', renderMode: RenderMode.Server },
  // Real 404 status for unknown pages, so search engines do not index them
  { path: 'ru/**', renderMode: RenderMode.Server, status: 404 },
  { path: 'en/**', renderMode: RenderMode.Server, status: 404 },
  { path: '**', renderMode: RenderMode.Server, status: 404 },
];
