import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth-service';
import { destinationForRoles } from '../auth/auth-destination';

export const dashboardRedirectGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.autenticado()) {
    return router.createUrlTree(['/login']);
  }

  const destination = destinationForRoles(auth.roles());
  if (!destination) {
    return router.createUrlTree(['/login']);
  }

  return router.parseUrl(destination);
};
