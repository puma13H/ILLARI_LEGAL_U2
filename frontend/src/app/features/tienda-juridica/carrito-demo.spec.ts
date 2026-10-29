import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { of } from 'rxjs';
import { TiendaJuridicaComponent } from './tienda-juridica.component';
import { ServicioLegalService } from '../catalogo/servicio.service';
import { AuthService } from '../../core/auth/auth-service';
describe('carrito público y separación de demostración', () => {
 let http: HttpTestingController;
 function tienda() {return new TiendaJuridicaComponent({listar:()=>of([])} as unknown as ServicioLegalService,TestBed.inject(HttpClient),{autenticado:()=>false,idCliente:()=>null} as unknown as AuthService);}
 beforeEach(()=>{sessionStorage.clear();TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});http=TestBed.inject(HttpTestingController);});
 afterEach(()=>{http.verify();sessionStorage.clear();});
 it('mantiene múltiples productos, cantidades y total al recrear la pantalla',()=>{const c=tienda();c.verDemo();const [a,b]=c.productos();c.agregar(a);c.agregar(b);c.cambiarCantidad(a.id,1);expect(c.subtotal()).toBe(265);expect(c.cantidad()).toBe(3);const restored=tienda();expect(restored.subtotal()).toBe(265);restored.quitar(a.id);expect(restored.subtotal()).toBe(85);});
 it('bloquea compras demo incluso después de recargar y sin autenticación',()=>{const c=tienda();c.verDemo();c.agregar(c.productos()[0]);const restored=tienda();restored.continuarCompra();expect(restored.errorCompra()).toContain('demostración');http.expectNone(()=>true);});
 it('impide mezclar IDs de demostración con productos reales',()=>{const c=tienda();c.verDemo();c.agregar(c.productos()[0]);c.agregar({id:25,nombre:'Real',categoria:'Asesorías',descripcion:'',precio:100,icono:'§'});expect(c.carrito()).toHaveLength(1);expect(c.errorCompra()).toContain('Vacía');});
 it('filtra por búsqueda, categoría y ordena importes',()=>{const c=tienda();c.verDemo();c.seleccionarCategoria('Códigos y leyes');c.orden.set('menor');expect(c.productosFiltrados().map(p=>p.precio)).toEqual([45,80,85,90]);c.busqueda.set('penal');expect(c.productosFiltrados()).toHaveLength(1);});
});
