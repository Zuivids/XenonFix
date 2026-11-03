import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

// These answer 401 as part of normal use, and their callers handle it
const HANDLED_BY_CALLER = ['/api/admin/auth/login', '/api/admin/me'];

/** Back to the login page when the session has ended while the panel is open. */
export const sessionExpiredInterceptor: HttpInterceptorFn = (request, next) => {
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        request.url.startsWith('/api/admin/') &&
        !HANDLED_BY_CALLER.includes(request.url)
      ) {
        void router.navigateByUrl('/admin/login');
      }
      return throwError(() => error);
    }),
  );
};
