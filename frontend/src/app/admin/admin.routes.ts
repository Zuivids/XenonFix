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
      { path: '', pathMatch: 'full', redirectTo: 'admins' },
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
      { path: '**', redirectTo: 'admins' },
    ],
  },
];
