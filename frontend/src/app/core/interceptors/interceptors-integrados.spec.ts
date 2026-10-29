import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from '../auth/auth-service';
import { authInterceptor } from './auth.interceptor';
import { traceInterceptor } from './trace.interceptor';
import { environment } from '../../../environments/environment';
describe('interceptores reales en el orden de appConfig',()=>{
 let http:HttpTestingController;
 beforeEach(()=>{TestBed.configureTestingModule({providers:[provideRouter([]),provideHttpClient(withInterceptors([authInterceptor,traceInterceptor])),provideHttpClientTesting(),{provide:AuthService,useValue:{accessToken:()=> 'unit-token'}}]});http=TestBed.inject(HttpTestingController);});
 afterEach(()=>http.verify());
 it('conserva el trace del carrito entre ambos interceptores',()=>{
  const url=environment.apiBaseUrl+'/api/v1/ordenes';TestBed.inject(HttpClient).post(url,{}, {headers:{'X-Trace-ID':'purchase-trace-original','Idempotency-Key':'purchase-key-original'}}).subscribe();
  const req=http.expectOne(url);expect(req.request.headers.get('X-Trace-ID')).toBe('purchase-trace-original');expect(req.request.headers.get('Idempotency-Key')).toBe('purchase-key-original');req.flush({});
 });
 it('genera correlacion y adjunta JWT solo al API propio',()=>{
  const url=environment.apiUrl+'/casos';TestBed.inject(HttpClient).get(url).subscribe();const req=http.expectOne(url);
  expect(req.request.headers.get('X-Trace-ID')).toMatch(/^[0-9a-f-]{36}$/);expect(req.request.headers.get('Authorization')).toBe('Bearer unit-token');req.flush([]);
 });
 it('no adjunta cabeceras a un sitio externo',()=>{
  const url='https://external.example.test/api/casos';TestBed.inject(HttpClient).get(url).subscribe();const req=http.expectOne(url);
  expect(req.request.headers.has('X-Trace-ID')).toBe(false);expect(req.request.headers.has('Authorization')).toBe(false);req.flush([]);
 });
});
