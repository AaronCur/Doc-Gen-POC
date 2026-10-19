import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { DevUser } from './dev-user';

/** POC ONLY: sends the selected mock user's Basic credentials on every API call. */
export const basicAuthInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) {
    return next(request);
  }
  const devUser = inject(DevUser);
  return next(request.clone({ setHeaders: { Authorization: devUser.authorizationHeader() } }));
};
