import { describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CasosListComponent } from './casos-list.component';
import { CasoService } from '../../core/services/caso.service';
import { AuthService } from '../../core/auth/auth-service';
function listado(fail=false){const list=vi.fn().mockReturnValue(fail?throwError(()=>new Error('down')):of([]));
 TestBed.configureTestingModule({providers:[provideRouter([]),{provide:CasoService,useValue:{list}},{provide:AuthService,useValue:{hasRole:(role:string)=>role==='admin'}}]});
 const fixture=TestBed.createComponent(CasosListComponent);fixture.detectChanges();return {fixture,list};}
describe('carga y acceso de Casos',()=>{
 it('ADMIN puede abrir Nuevo caso desde el listado',()=>{const {fixture}=listado();expect(fixture.nativeElement.textContent).toContain('Nuevo caso');});
 it('finaliza el spinner y muestra el fallo del servicio',()=>{const {fixture}=listado(true);expect(fixture.componentInstance.loading).toBe(false);expect(fixture.nativeElement.textContent).toContain('No se pudieron cargar los casos');expect(fixture.nativeElement.textContent).not.toContain('No hay casos registrados');});
 it('reintenta y elimina el error cuando el API se recupera',()=>{const {fixture,list}=listado(true);list.mockReturnValue(of([{id:1,nombreCaso:'Recuperado'}]));fixture.componentInstance.cargar();fixture.detectChanges();expect(fixture.componentInstance.error).toBeNull();expect(fixture.nativeElement.textContent).toContain('Recuperado');});
});
