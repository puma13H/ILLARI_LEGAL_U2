import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth-service';
import { canOpen } from './panel-data';
export const panelGuard:CanActivateFn=(route)=>{
 const auth=inject(AuthService);const router=inject(Router);
 if(!auth.autenticado()||!auth.currentUser())return router.parseUrl('/login');
 const key=route.paramMap.get('modulo')??'';
 const role=auth.currentUser()!.role.toUpperCase();
 return key==='reportes'?(role==='ADMIN'||role==='ABOGADO'?true:router.parseUrl('/dashboard')):canOpen(role,key)||router.parseUrl('/dashboard');
};
