import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from '../auth/auth-service';
import { authInterceptor } from './auth.interceptor';
import { environment } from '../../../environments/environment';

describe('correlacion del interceptor HTTP', () => {
  let controller: HttpTestingController;
  const auth = {accessToken: () => 'unit-test-token', iniciarLogin: vi.fn()};
  beforeEach(() => {
    TestBed.configureTestingModule({providers:[provideRouter([]),provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting(),{provide:AuthService,useValue:auth}]});
    controller=TestBed.inject(HttpTestingController);
  });
  afterEach(() => controller.verify());
  it('genera una correlacion tecnica para el API propio', () => {
    const url=environment.apiBaseUrl+'/api/v1/ordenes';TestBed.inject(HttpClient).get(url).subscribe();
    const request=controller.expectOne(url);
    expect(request.request.headers.get('X-Trace-ID')).toMatch(/^[0-9a-f-]{36}$/);
    expect(request.request.headers.get('Authorization')).toBe('Bearer unit-test-token');request.flush({});
  });
  it('conserva la correlacion persistida por el carrito durante un reintento', () => {
    const url=environment.apiBaseUrl+'/api/v1/ordenes';
    TestBed.inject(HttpClient).post(url,{}, {headers:{'X-Trace-ID':'purchase-technical-trace','Idempotency-Key':'purchase-business-key'}}).subscribe();
    const request=controller.expectOne(url);expect(request.request.headers.get('X-Trace-ID')).toBe('purchase-technical-trace');
    expect(request.request.headers.get('Idempotency-Key')).toBe('purchase-business-key');request.flush({});
  });
  it('no envia JWT ni correlacion a destinos externos', () => {
    const url='https://external.example.test/api';TestBed.inject(HttpClient).get(url).subscribe();
    const request=controller.expectOne(url);expect(request.request.headers.has('Authorization')).toBe(false);
    expect(request.request.headers.has('X-Trace-ID')).toBe(false);request.flush({});
  });
});
