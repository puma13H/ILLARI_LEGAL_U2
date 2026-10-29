import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController,provideHttpClientTesting } from '@angular/common/http/testing';
import { of,Subject,throwError } from 'rxjs';
import { describe,expect,it,vi } from 'vitest';
import { CasoService } from '../core/services/caso.service';
import { MensajeService } from '../core/services/mensaje.service';
import { AuthService } from '../core/auth/auth-service';
import { MensajesComponent } from './mensajes/mensajes.component';
import { CalendarioComponent } from './calendario/calendario.component';
import { environment } from '../../environments/environment';
function messages(list=vi.fn(()=>of([{id:1,contenido:'Mensaje persistido'}])),send=vi.fn(()=>of({id:2,contenido:'Respuesta real'}))){TestBed.configureTestingModule({providers:[{provide:CasoService,useValue:{list:()=>of([{id:11,nombreCaso:'Caso autorizado'}])}},{provide:MensajeService,useValue:{list,send}}]});const c=TestBed.createComponent(MensajesComponent);c.detectChanges();return {c,list,send};}
describe('módulos integrados de entrega',()=>{
 it('carga mensajes reales del caso autorizado sin datos de muestra',()=>{const {c,list}=messages();expect(list).toHaveBeenCalledWith(11);expect(c.nativeElement.textContent).toContain('Mensaje persistido');expect(c.nativeElement.textContent).not.toContain('09:10');});
 it('conserva texto cuando falla el envío y permite reintentar',()=>{const send=vi.fn().mockReturnValueOnce(throwError(()=>new Error('down'))).mockReturnValueOnce(of({id:2,contenido:'Respuesta real'}));const {c}=messages(undefined,send);const v=c.componentInstance;v.texto='Respuesta real';v.enviar();expect(v.texto).toBe('Respuesta real');expect(v.error()).toBeTruthy();v.enviar();expect(v.texto).toBe('');expect(v.mensajes()).toHaveLength(2);});
 it('ignora la respuesta tardía de un caso anterior',()=>{const first=new Subject<unknown[]>();const next=new Subject<unknown[]>();const list=vi.fn().mockReturnValueOnce(first).mockReturnValueOnce(next);const {c}=messages(list);c.componentInstance.seleccionar(12);next.next([{id:2,contenido:'Caso 12'}]);first.next([{id:1,contenido:'Caso 11'}]);expect(c.componentInstance.mensajes()[0].id).toBe(2);});
 it('impide doble envío mientras la respuesta está pendiente',()=>{const pending=new Subject();const send=vi.fn(()=>pending);const {c}=messages(undefined,send as never);c.componentInstance.texto='Mensaje';c.componentInstance.enviar();c.componentInstance.enviar();expect(send).toHaveBeenCalledTimes(1);});
 it('CLIENTE solo consulta fechas de sus casos y no solicita agenda ADMIN',()=>{TestBed.configureTestingModule({providers:[provideRouter([]),provideHttpClient(),provideHttpClientTesting(),{provide:AuthService,useValue:{tieneRol:()=>false}},{provide:CasoService,useValue:{list:()=>of([{id:11,nombreCaso:'Propio',fechaAudiencia:'2026-11-01T10:00:00'}])}}]});const c=TestBed.createComponent(CalendarioComponent);c.detectChanges();const http=TestBed.inject(HttpTestingController);http.expectNone(environment.apiUrl+'/agenda');expect(c.componentInstance.entradas()).toHaveLength(1);expect(c.nativeElement.textContent).not.toContain('Guardar evento');http.verify();});
 it('ADMIN carga agenda y guarda un evento válido por la API',()=>{TestBed.configureTestingModule({providers:[provideRouter([]),provideHttpClient(),provideHttpClientTesting(),{provide:AuthService,useValue:{tieneRol:()=>true}},{provide:CasoService,useValue:{list:()=>of([])}}]});const f=TestBed.createComponent(CalendarioComponent);f.detectChanges();const h=TestBed.inject(HttpTestingController);h.expectOne(environment.apiUrl+'/agenda').flush([]);const c=f.componentInstance;c.titulo='Ensayo';c.inicio='2026-11-01T10:00';c.fin='2026-11-01T09:00';c.guardar();h.expectNone(environment.apiUrl+'/agenda');expect(c.error()).toBeTruthy();c.fin='2026-11-01T11:00';c.guardar();const req=h.expectOne(environment.apiUrl+'/agenda');expect(req.request.method).toBe('POST');req.flush({id:1,titulo:'Ensayo',fechaInicio:c.inicio,fechaFin:c.fin});expect(c.entradas()).toHaveLength(1);expect(c.guardando()).toBe(false);h.verify();});
});
