import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { traceInterceptor } from './core/interceptors/trace.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withInMemoryScrolling({anchorScrolling:'enabled',scrollPositionRestoration:'enabled'})),
    provideHttpClient(withXhr(), withInterceptors([authInterceptor, traceInterceptor]))
  ]
};

