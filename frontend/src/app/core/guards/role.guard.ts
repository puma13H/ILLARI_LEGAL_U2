import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth-service';

export const roleGuard = (...roles: string[]): CanActivateFn => (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.autenticado()) {
    return router.createUrlTree(['/login']);
  }

  if (auth.tieneRol(...roles.map(role => role.toUpperCase()))) {
    return true;
  }

  const rolesUsuario = auth.roles().map(role => role.toUpperCase());

  const rol = ['ADMIN', 'ABOGADO', 'CLIENTE']
    .find(role => rolesUsuario.includes(role));

  return rol
    ? router.createUrlTree(['/dashboard', rol.toLowerCase()])
    : router.createUrlTree(['/login']);
};
