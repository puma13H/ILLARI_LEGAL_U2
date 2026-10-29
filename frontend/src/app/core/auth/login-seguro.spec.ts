import { afterEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { AuthService } from './auth-service';
import { LoginComponent } from '../../features/auth/login.component';
import { environment } from '../../../environments/environment';
import { authGuard } from '../guards/auth.guard';
import { roleGuard } from '../guards/role.guard';
import { AuthCallback } from './auth-callback';
afterEach(()=>{sessionStorage.clear();vi.unstubAllGlobals();vi.restoreAllMocks();});
describe('login único y seguro',()=>{
 it('Keycloak apagado no redirige, no crea sesión y conserva el carrito',async()=>{
  const a=new AuthService();sessionStorage.setItem('illari_legal_carrito','carrito-visitante');
  const assign=vi.spyOn(location,'assign').mockImplementation(()=>{});
  vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new TypeError('offline')));
  await expect(a.iniciarLogin('/dashboard')).rejects.toThrow('autenticación no disponible');
  expect(assign).not.toHaveBeenCalled();expect(a.autenticado()).toBe(false);expect(a.roles()).toEqual([]);
  expect(sessionStorage.getItem('illari_legal_carrito')).toBe('carrito-visitante');
 });
 it('genera Authorization Code con PKCE S256 sin seleccionar un rol',async()=>{
  const a=new AuthService();const assign=vi.spyOn(location,'assign').mockImplementation(()=>{});
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({issuer:environment.keycloak.issuer}))));
  await a.iniciarLogin('/dashboard');const u=new URL(assign.mock.calls[0][0] as string);
  expect(u.searchParams.get('response_type')).toBe('code');expect(u.searchParams.get('code_challenge_method')).toBe('S256');
  expect(u.searchParams.get('code_challenge')).toBeTruthy();expect(u.searchParams.get('state')).toBe(sessionStorage.getItem('illari_s12_state'));
  expect(sessionStorage.getItem('illari_s12_rol-solicitado')).toBeNull();expect(a.autenticado()).toBe(false);
 });
 it('rechaza discovery que no pertenece al realm configurado',async()=>{
  const a=new AuthService();vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({issuer:'https://otro.test'}))));
  await expect(a.iniciarLogin()).rejects.toThrow('no disponible');expect(a.autenticado()).toBe(false);
 });
 it('muestra el error de disponibilidad con un único botón de acceso',async()=>{
  TestBed.configureTestingModule({providers:[provideRouter([]),{provide:AuthService,useValue:{autenticado:()=>false,iniciarLogin:vi.fn().mockRejectedValue(new Error('offline'))}}]});
  const f=TestBed.createComponent(LoginComponent);f.detectChanges();await f.componentInstance.submit();f.detectChanges();
  expect(f.nativeElement.textContent).toContain('Servicio de autenticación no disponible');
  expect(f.nativeElement.querySelectorAll('button').length).toBe(1);
  expect(f.nativeElement.textContent).not.toContain('CASOS LEGALES');expect(f.componentInstance.loading).toBe(false);
 });
 it('bloquea rutas privadas y roles solicitados sin sesión',()=>{
  TestBed.configureTestingModule({providers:[provideRouter([]),{provide:AuthService,useValue:{autenticado:()=>false}}]});
  const router=TestBed.inject(Router);
  for(const guard of [authGuard,roleGuard('ADMIN')]){
   const result=TestBed.runInInjectionContext(()=>guard({} as never,{url:'/dashboard/admin'} as never));
   expect(router.serializeUrl(result as never)).toBe('/login');
  }
 });
 for(const [role,destination] of [['ADMIN','/dashboard/admin'],['ABOGADO','/dashboard/abogado'],['CLIENTE','/dashboard/cliente']]){
  it('callback usa el rol real '+role+' aunque el destino almacenado sea ADMIN',async()=>{
   const navigateByUrl=vi.fn().mockResolvedValue(true);const completarLogin=vi.fn().mockResolvedValue(undefined);
   TestBed.configureTestingModule({providers:[{provide:Router,useValue:{navigateByUrl}},{provide:ActivatedRoute,useValue:{snapshot:{queryParamMap:new Map([['code','test-code'],['state','test-state']])}}},{provide:AuthService,useValue:{completarLogin,roles:()=>[role],destinoPendiente:()=>'/dashboard/admin'}}]});
   TestBed.runInInjectionContext(()=>new AuthCallback());await Promise.resolve();await Promise.resolve();
   expect(completarLogin).toHaveBeenCalledWith('test-code','test-state');expect(navigateByUrl).toHaveBeenCalledWith(destination,{replaceUrl:true});
  });
 }
});
