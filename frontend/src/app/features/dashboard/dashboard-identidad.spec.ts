import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { AuthService } from '../../core/auth/auth-service';
import { CasoService } from '../../core/services/caso.service';
import { DocumentoService } from '../../core/services/documento.service';
import { DashboardService } from '../../core/services/dashboard.service';
function panel(role:string,fail=false){
 const auth={hasRole:(r:string)=>r===role,currentUser:()=>({userId:0,name:'QA',role})};
 const cases=[{id:7,clienteId:35,abogadoId:48,estado:'En trámite',nombreCaso:'Caso autorizado'}];
 TestBed.configureTestingModule({providers:[provideRouter([]),{provide:AuthService,useValue:auth},
  {provide:CasoService,useValue:{list:()=>of(cases)}},{provide:DocumentoService,useValue:{list:()=>of([{id:1},{id:2}])}},
  {provide:DashboardService,useValue:{stats:()=>fail?throwError(()=>new Error('down')):of({totalCasos:1,totalClientes:1,totalAbogados:1,casosActivos:1})}}]});
 const fixture=TestBed.createComponent(DashboardComponent);fixture.detectChanges();return fixture;
}
describe('dashboard con identidad OIDC sin ID local inferido',()=>{
 it('cuenta casos y documentos autorizados del ABOGADO aunque userId sea cero',()=>{const c=panel('abogado').componentInstance;expect(c.myCasosCount).toBe(1);expect(c.documentosCount).toBe(2);});
 it('cuenta los casos autorizados del CLIENTE sin comparar IDs de tablas diferentes',()=>{const c=panel('cliente').componentInstance;expect(c.myCasosCount).toBe(1);expect(c.documentosCount).toBe(2);});
 it('muestra un error en lugar de tratar un fallo del API como un resumen valido',()=>{const f=panel('admin',true);expect(f.componentInstance.loadError).toBeTruthy();expect(f.nativeElement.textContent).toContain('No se pudo cargar el resumen');});
});
