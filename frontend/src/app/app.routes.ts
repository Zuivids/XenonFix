import { inject } from '@angular/core';
import { Route, Routes } from '@angular/router';
import { Home } from './home/home';
import { I18n } from './i18n/i18n';
import { Language, languagePrefix } from './i18n/languages';
import { NotFound } from './not-found/not-found';

// The same pages under each language's prefix. Add new pages here.
function languageRoutes(language: Language): Route {
  return {
    path: languagePrefix(language),
    resolve: { messages: () => inject(I18n).use(language) },
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: Home,
        title: 'home.title',
        data: { description: 'home.description' },
      },
      {
        path: '**',
        component: NotFound,
        title: 'notFound.title',
        data: { description: 'home.description' },
      },
    ],
  };
}

export const routes: Routes = [
  { path: 'lv', pathMatch: 'full', redirectTo: '/' },
  languageRoutes('ru'),
  languageRoutes('en'),
  // Last: no prefix, so it also takes every other URL
  languageRoutes('lv'),
];
