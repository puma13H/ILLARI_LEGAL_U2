import { Router } from '@angular/router';
import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth-service';
import { environment } from '../../../environments/environment';
import { esApiPropia } from './api-destination';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const haciaApi = esApiPropia(req.url, [environment.apiBaseUrl, environment.apiUrl], location.origin);
  if (haciaApi && !req.headers.has('X-Trace-ID')) req = req.clone({ setHeaders: { 'X-Trace-ID': crypto.randomUUID() } });
  const token = auth.accessToken();
  const conToken = haciaApi && token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(conToken).pipe(
    catchError((error: HttpErrorResponse) => {
      if (haciaApi && error.status === 401) {
        void router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
