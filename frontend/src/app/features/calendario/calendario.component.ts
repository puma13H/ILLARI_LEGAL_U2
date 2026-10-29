import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth-service';
import { CasoService } from '../../core/services/caso.service';
import { environment } from '../../../environments/environment';
interface Entrada {id:number;titulo:string;descripcion?:string;fechaInicio:string;fechaFin:string;casoId?:number;}
@Component({selector:'app-calendario',standalone:true,imports:[DatePipe,FormsModule,RouterLink],template:`
<section class="page-header"><div><h1 class="page-title">Calendario y agenda</h1><p class="page-subtitle">Audiencias de tus casos autorizados. La agenda general es exclusiva de ADMIN.</p></div></section>
@if(error()){<p class="alert alert-error" role="alert">{{error()}}</p>}
@if(auth.tieneRol('ADMIN')){<section class="card"><div class="card-body"><h2>Agregar evento a la agenda general</h2><form (ngSubmit)="guardar()">
<label for="titulo-agenda">Título</label><input id="titulo-agenda" name="titulo" class="input" [(ngModel)]="titulo" required maxlength="255"/>
<label for="inicio-agenda">Inicio</label><input id="inicio-agenda" name="inicio" class="input" type="datetime-local" [(ngModel)]="inicio" required/>
<label for="fin-agenda">Fin</label><input id="fin-agenda" name="fin" class="input" type="datetime-local" [(ngModel)]="fin" required/>
<label for="descripcion-agenda">Descripción</label><textarea id="descripcion-agenda" name="descripcion" class="input" [(ngModel)]="descripcion"></textarea>
<button class="btn btn-primary" [disabled]="guardando()" type="submit">{{guardando()?'Guardando...':'Guardar evento'}}</button></form></div></section>}
<section class="card"><div class="card-body"><h2>Eventos registrados</h2>@for(e of entradas();track e.id){<article class="agenda-item"><h3>{{e.titulo}}</h3><p>{{e.fechaInicio|date:'dd/MM/yyyy HH:mm'}} — {{e.fechaFin|date:'dd/MM/yyyy HH:mm'}}</p><p>{{e.descripcion}}</p>@if(e.casoId){<a [routerLink]="['/casos',e.casoId]">Ver caso</a>}</article>}@empty{<p>No hay eventos con fecha registrados para esta cuenta.</p>}</div></section>`})
export class CalendarioComponent implements OnInit {
 readonly auth=inject(AuthService);private readonly http=inject(HttpClient);private readonly casos=inject(CasoService);
 readonly entradas=signal<Entrada[]>([]);readonly error=signal('');readonly guardando=signal(false);titulo='';inicio='';fin='';descripcion='';
 ngOnInit(){this.casos.list().subscribe({next:casos=>this.agregar(casos.filter(c=>!!c.fechaAudiencia).map(c=>({id:-c.id,casoId:c.id,titulo:c.nombreCaso,descripcion:'Audiencia de caso',fechaInicio:c.fechaAudiencia!,fechaFin:c.fechaAudiencia!}))),error:()=>this.error.set('No se pudieron cargar las audiencias de tus casos.')});
 if(this.auth.tieneRol('ADMIN'))this.http.get<Entrada[]>(`${environment.apiUrl}/agenda`).subscribe({next:xs=>this.agregar(xs),error:()=>this.error.set('No se pudo cargar la agenda general.')});}
 private agregar(xs:Entrada[]){this.entradas.update(actual=>[...actual,...xs].sort((a,b)=>a.fechaInicio.localeCompare(b.fechaInicio)));}
 guardar(){if(!this.auth.tieneRol('ADMIN') || this.guardando())return;if(!this.titulo.trim() || !this.inicio || !this.fin || this.fin<this.inicio){this.error.set('Indica un título y un intervalo de fechas válido.');return;}
 this.error.set('');this.guardando.set(true);this.http.post<Entrada>(`${environment.apiUrl}/agenda`,{titulo:this.titulo.trim(),descripcion:this.descripcion,fechaInicio:this.inicio,fechaFin:this.fin}).subscribe({next:e=>{this.agregar([e]);this.titulo='';this.descripcion='';this.inicio='';this.fin='';this.guardando.set(false);},error:()=>{this.error.set('No se pudo guardar el evento.');this.guardando.set(false);}});}
}
