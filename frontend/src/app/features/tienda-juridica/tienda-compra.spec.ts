import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth-service';
import { ServicioLegalService } from '../catalogo/servicio.service';
import { TiendaJuridicaComponent } from './tienda-juridica.component';

describe('reintentos HTTP del carrito', () => {
  let http: HttpTestingController;
  const producto = { id: 7, nombre: 'Consulta', categoria: 'Legal', descripcion: '', precio: 50, icono: '⚖' };
  const auth = { autenticado: () => true, idCliente: () => 'retry-client' } as unknown as AuthService;
  const catalogo = { listar: () => of([]) } as unknown as ServicioLegalService;
  function tienda() {
    return new TiendaJuridicaComponent(catalogo, TestBed.inject(HttpClient), auth);
  }
  function compra() { return http.expectOne(r => r.method === 'POST' && r.url.endsWith('/api/v1/ordenes')); }
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    vi.useFakeTimers();
  });
  afterEach(() => { http.verify(); vi.clearAllTimers(); vi.useRealTimers(); sessionStorage.clear(); });

  it('recupera la clave tras perder la respuesta, recargar y completar el pago', async () => {
    const first = tienda(); first.agregar(producto); first.continuarCompra();
    const original = compra(); const key = original.request.headers.get('Idempotency-Key');
    const body = original.request.body;
    expect(key).toBeTruthy(); original.error(new ProgressEvent('error'));
    expect(first.compraPendiente()).toBe(true);
    const reload = tienda(); reload.ngOnInit(); reload.continuarCompra();
    const retry = compra(); expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    expect(retry.request.body).toEqual(body);
    retry.flush({ id: 42, estado: 'PENDIENTE_PAGO', total: 50 });
    await vi.advanceTimersByTimeAsync(800);
    http.expectOne(r => r.url.endsWith('/api/v1/ordenes/42')).flush({ id: 42, estado: 'PAGADA', total: 50 });
    expect(reload.mensajeCompra()).toContain('pagada y confirmada');
    expect(reload.compraPendiente()).toBe(false); expect(reload.carrito()).toHaveLength(0);
  });
  it('conserva una compra incierta aunque el siguiente reintento sea denegado', () => {
    const component = tienda(); component.agregar(producto); component.continuarCompra();
    const original = compra(); const key = original.request.headers.get('Idempotency-Key');
    original.error(new ProgressEvent('error'));
    component.continuarCompra(); compra().flush({}, { status: 403, statusText: 'Forbidden' });
    expect(component.compraPendiente()).toBe(true);
    component.continuarCompra();
    const retry = compra(); expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.error(new ProgressEvent('error'));
  });
  it('permite corregir una compra rechazada en su primera petición sin reutilizar otra carga', () => {
    const component = tienda(); component.agregar(producto); component.continuarCompra();
    const original = compra(); const key = original.request.headers.get('Idempotency-Key');
    original.flush({}, { status: 400, statusText: 'Bad Request' });
    expect(component.compraPendiente()).toBe(false);
    component.metodoPago.set('Plin'); component.continuarCompra();
    const corrected = compra(); expect(corrected.request.headers.get('Idempotency-Key')).not.toBe(key);
    expect(corrected.request.body.metodoPago).toBe('PLIN'); corrected.error(new ProgressEvent('error'));
  });
});
