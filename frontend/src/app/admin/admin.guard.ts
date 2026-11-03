import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AdminSession } from './admin-session';

const PASSWORD_PAGE = '/admin/password';

/** Sends visitors to the login page, and admins with a temporary password to change it. */
export const adminGuard: CanActivateFn = async (_route, state) => {
  const session = inject(AdminSession);
  const router = inject(Router);

  const me = await session.load();
  if (!me) {
    return router.parseUrl('/admin/login');
  }
  if (me.passwordChangeRequired && state.url !== PASSWORD_PAGE) {
    return router.parseUrl(PASSWORD_PAGE);
  }
  return true;
};
