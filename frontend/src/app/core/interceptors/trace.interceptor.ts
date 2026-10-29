import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { esApiPropia } from './api-destination';

/** Preserve purchase correlation set by the caller/auth interceptor; never forward it to third parties. */
export const traceInterceptor: HttpInterceptorFn = (req, next) => {
  const propia = esApiPropia(req.url, [environment.apiBaseUrl, environment.apiUrl], location.origin);
  return next(propia && !req.headers.has('X-Trace-ID')
    ? req.clone({ setHeaders: { 'X-Trace-ID': crypto.randomUUID() } }) : req);
};
