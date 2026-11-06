import { Routes } from '@angular/router';
import { adminGuard } from './admin.guard';
import { AdminLayout } from './layout/admin-layout';
import { AdminLogin } from './login/admin-login';

// The admin panel is in Latvian only, so its texts are in the templates and not in the
// translation files of the public site.
export const adminRoutes: Routes = [
  { path: 'login', component: AdminLogin, title: 'Pieslēgšanās — XenonFix' },
  {
    path: '',
    component: AdminLayout,
    canActivate: [adminGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'products' },
      {
        path: 'products',
        title: 'Produkti — XenonFix',
        loadComponent: () => import('./products/product-list').then((m) => m.ProductList),
      },
      {
        path: 'products/new',
        title: 'Jauns produkts — XenonFix',
        loadComponent: () => import('./products/product-form').then((m) => m.ProductForm),
      },
      {
        path: 'products/:id',
        title: 'Produkts — XenonFix',
        loadComponent: () => import('./products/product-form').then((m) => m.ProductForm),
      },
      {
        path: 'reservations',
        title: 'Rezervācijas — XenonFix',
        loadComponent: () => import('./reservations/reservation-list').then((m) => m.ReservationList),
      },
      {
        path: 'reservations/:id',
        title: 'Rezervācija — XenonFix',
        loadComponent: () => import('./reservations/reservation-detail').then((m) => m.ReservationDetail),
      },
      {
        path: 'admins',
        title: 'Administratori — XenonFix',
        loadComponent: () => import('./admins/admin-list').then((m) => m.AdminList),
      },
      {
        path: 'audit',
        title: 'Notikumu žurnāls — XenonFix',
        loadComponent: () => import('./audit/audit-log').then((m) => m.AuditLog),
      },
      {
        path: 'password',
        title: 'Mainīt paroli — XenonFix',
        loadComponent: () => import('./password/change-password').then((m) => m.ChangePassword),
      },
      { path: '**', redirectTo: 'products' },
    ],
  },
];
