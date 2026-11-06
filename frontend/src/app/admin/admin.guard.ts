import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { LoginStep } from './admin-api';
import { AdminSession } from './admin-session';

/** The page each unfinished login step must stay on. */
const STEP_PAGES: Partial<Record<LoginStep, string>> = {
  MFA_REQUIRED: '/admin/login',
  MFA_SETUP_REQUIRED: '/admin/mfa',
  PASSWORD_CHANGE_REQUIRED: '/admin/password',
};

/**
 * Sends visitors to the login page, and admins who have not finished logging in to the step
 * they are at: the code, setting up the app, or changing a temporary password.
 */
export const adminGuard: CanActivateFn = async (_route, state) => {
  const session = inject(AdminSession);
  const router = inject(Router);

  const me = await session.load();
  if (!me) {
    return router.parseUrl('/admin/login');
  }
  const page = STEP_PAGES[me.step];
  if (page && state.url !== page) {
    return router.parseUrl(page);
  }
  return true;
};
