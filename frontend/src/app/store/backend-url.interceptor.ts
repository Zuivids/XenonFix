import { HttpInterceptorFn } from '@angular/common/http';
import { inject, InjectionToken } from '@angular/core';

/** Where the server finds the backend while rendering. In the browser it stays empty. */
export const BACKEND_URL = new InjectionToken<string>('BACKEND_URL', { factory: () => '' });

/**
 * While rendering on the server, sends API requests straight to the backend, rather than out
 * through the public address and back. Angular may already have made the address absolute,
 * from the visitor's request, so only the path is used.
 */
export const backendUrlInterceptor: HttpInterceptorFn = (request, next) => {
  const backendUrl = inject(BACKEND_URL);
  if (!backendUrl) {
    return next(request);
  }
  const url = new URL(request.url, backendUrl);
  if (!url.pathname.startsWith('/api/')) {
    return next(request);
  }
  return next(request.clone({ url: `${backendUrl}${url.pathname}${url.search}` }));
};
