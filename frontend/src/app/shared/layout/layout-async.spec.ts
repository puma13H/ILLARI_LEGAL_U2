import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { AppComponent } from '../../app.component';
import { LayoutComponent } from './layout.component';
import { CasoCreateComponent } from '../../features/casos/caso-create.component';
import { AuthService } from '../../core/auth/auth-service';
import { CasoService } from '../../core/services/caso.service';
import { ClienteService } from '../../core/services/cliente.service';
import { AbogadoService } from '../../core/services/abogado.service';
describe('layout y pantallas juridicas heredadas con cargas asincronas',()=>{
 it('actualiza opciones recibidas despues de navegar sin escribir ni hacer otro click',async()=>{
  const clients=new Subject<{id:number;nombre:string}[]>();
  TestBed.configureTestingModule({providers:[provideRouter([{path:'',component:LayoutComponent,children:[{path:'casos/nuevo',component:CasoCreateComponent}]}]),
   {provide:AuthService,useValue:{autenticado:()=>true,currentUser:()=>({name:'QA',role:'admin'}),nombreUsuario:()=> 'QA',tieneRol:()=>true,hasRole:(r:string)=>r==='admin'}},
   {provide:ClienteService,useValue:{list:()=>clients}},{provide:AbogadoService,useValue:{list:()=>of([])}},{provide:CasoService,useValue:{}}]});
  const fixture=TestBed.createComponent(AppComponent);fixture.detectChanges();
  await TestBed.inject(Router).navigateByUrl('/casos/nuevo');await fixture.whenStable();fixture.detectChanges();
  expect(fixture.nativeElement.textContent).not.toContain('Cliente asincrono');
  clients.next([{id:71,nombre:'Cliente asincrono'}]);fixture.detectChanges();
  expect(fixture.nativeElement.textContent).toContain('Cliente asincrono');clients.complete();
 });
});
