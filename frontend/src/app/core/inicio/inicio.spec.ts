import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { Inicio } from './inicio';
import { ServicioLegalService } from '../../features/catalogo/servicio.service';
import { LegalAvatarComponent } from '../../shared/legal-avatar/legal-avatar.component';
import { LayoutComponent } from '../../shared/layout/layout.component';
import { AuthService } from '../auth/auth-service';
import { Router } from '@angular/router';
function fixture(data?:unknown[]){TestBed.configureTestingModule({providers:[provideRouter([]),{provide:ServicioLegalService,useValue:{listar:()=>data?of(data):throwError(()=>new Error('offline'))}}]});const f=TestBed.createComponent(Inicio);f.detectChanges();return f;}
beforeEach(()=>{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({enabled:false}))));});
afterEach(()=>{TestBed.resetTestingModule();sessionStorage.clear();vi.restoreAllMocks();vi.unstubAllGlobals();});
describe('portada pública funcional',()=>{
 it('ofrece seis especialidades y no inventa estadísticas comerciales',()=>{const f=fixture();expect(f.nativeElement.querySelectorAll('.specialty-card')).toHaveLength(6);expect(f.nativeElement.textContent).toContain('No representan clientes');expect(f.nativeElement.textContent).not.toContain('98%');});
 it('no sustituye una caída por catálogo falso hasta activar demo',()=>{const f=fixture();expect(f.componentInstance.products()).toHaveLength(0);expect(f.componentInstance.demo()).toBe(false);f.componentInstance.showDemo();f.detectChanges();expect(f.nativeElement.textContent).toContain('CATÁLOGO DE DEMOSTRACIÓN');expect(f.componentInstance.products()).toHaveLength(10);});
 it('agrega varios ejemplos y conserva contador y total de portada',()=>{const f=fixture();const c=f.componentInstance;c.showDemo();c.add(c.products()[0]);c.add(c.products()[1]);f.detectChanges();expect(c.cart.cantidad()).toBe(2);expect(c.cart.subtotal()).toBe(175);expect(f.nativeElement.textContent).toContain('2 unidades');});
 it('filtra datos reales válidos sin etiquetarlos como demo',()=>{const f=fixture([{id:5,nombre:'Civil',precio:70,categoria:'Asesorías'},{id:6,nombre:'Inactivo',precio:10,estado:'Inactivo'},{id:7,nombre:'Inválido',precio:-1}]);expect(f.componentInstance.products()).toHaveLength(1);expect(f.componentInstance.demo()).toBe(false);f.componentInstance.search.set('penal');expect(f.componentInstance.filtered()).toHaveLength(0);});
 it('abre y cierra el asistente de navegación sin asesoría automatizada',()=>{const f=fixture();const launcher=f.nativeElement.querySelector('.assistant-launcher') as HTMLButtonElement;launcher.click();f.detectChanges();expect(f.nativeElement.querySelector('#assistant-panel')).toBeTruthy();expect(f.nativeElement.textContent).toContain('No ofrece asesoría legal automatizada');f.componentInstance.closeAssistant();f.detectChanges();expect(f.nativeElement.querySelector('#assistant-panel')).toBeNull();});
 it('el buscador de cabecera navega con la consulta a la tienda',()=>{TestBed.configureTestingModule({providers:[provideRouter([]),{provide:AuthService,useValue:{autenticado:()=>false}}]});const f=TestBed.createComponent(LayoutComponent);const router=TestBed.inject(Router);const spy=vi.spyOn(router,'navigate').mockResolvedValue(true);f.componentInstance.search='  Civil  ';f.componentInstance.buscar();expect(spy).toHaveBeenCalledWith(['/tienda-juridica'],{queryParams:{buscar:'Civil'}});});
 it('el avatar usa capas nativas sin descargar runtimes ni audio',()=>{const fetcher=vi.fn();vi.stubGlobal('fetch',fetcher);TestBed.configureTestingModule({});const f=TestBed.createComponent(LegalAvatarComponent);f.detectChanges();expect(f.nativeElement.querySelectorAll('[data-layer]')).toHaveLength(8);expect(f.nativeElement.querySelector('audio')).toBeNull();expect(fetcher).not.toHaveBeenCalled();});
});
