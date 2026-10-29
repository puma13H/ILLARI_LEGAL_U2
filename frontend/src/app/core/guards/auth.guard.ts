import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth-service';
export const authGuard: CanActivateFn = () => {
 const auth=inject(AuthService);
 return auth.autenticado() && !!auth.currentUser() ? true : inject(Router).createUrlTree(['/login']);
};
