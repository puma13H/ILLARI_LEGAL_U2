import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CasoService } from '../../core/services/caso.service';
import { MensajeService } from '../../core/services/mensaje.service';
import { Caso, Mensaje } from '../../core/models';
@Component({selector:'app-mensajes', standalone:true, imports:[FormsModule],template:`
<section class="page-header"><div><h1 class="page-title">Mensajes de mis casos</h1><p class="page-subtitle">Conversaciones persistidas de los casos a los que tienes acceso.</p></div></section>
<section class="card"><div class="card-body"><label for="caso-mensajes">Caso</label>
<select id="caso-mensajes" class="input" [ngModel]="casoId()" (ngModelChange)="seleccionar(+$event)" [disabled]="enviando()">
<option [ngValue]="0">Selecciona un caso</option>@for(caso of casos(); track caso.id){<option [ngValue]="caso.id">#{{caso.id}} — {{caso.nombreCaso}}</option>}</select>
@if(error()){<p class="alert alert-error" role="alert">{{error()}}</p>}@if(cargando()){<p role="status">Cargando conversación...</p>}
@if(!cargando() && casoId()){
<div class="messages-list" aria-live="polite">@for(m of mensajes(); track m.id){<article class="message-item"><strong>{{m.userName}}</strong><p>{{m.contenido}}</p></article>}@empty{<p>No hay mensajes en este caso.</p>}</div>
<form (ngSubmit)="enviar()"><label for="nuevo-mensaje">Mensaje</label><textarea id="nuevo-mensaje" name="mensaje" class="input" rows="3" [(ngModel)]="texto" [disabled]="enviando()" maxlength="5000"></textarea>
<button class="btn btn-primary" type="submit" [disabled]="enviando() || !texto.trim()">{{enviando()?'Enviando...':'Enviar mensaje'}}</button></form>}
</div></section>`})
export class MensajesComponent implements OnInit {
 private readonly casosApi=inject(CasoService);private readonly mensajesApi=inject(MensajeService);
 readonly casos=signal<Caso[]>([]);readonly mensajes=signal<Mensaje[]>([]);readonly casoId=signal(0);
 readonly cargando=signal(false);readonly enviando=signal(false);readonly error=signal('');texto='';private revision=0;
 ngOnInit(){this.casosApi.list().subscribe({next:casos=>{this.casos.set(casos);if(casos.length)this.seleccionar(casos[0].id);},error:()=>this.error.set('No se pudieron cargar los casos autorizados.')});}
 seleccionar(id:number){const revision=++this.revision;this.casoId.set(id);this.mensajes.set([]);this.texto='';this.error.set('');this.cargando.set(false);if(!id)return;this.cargando.set(true);
 this.mensajesApi.list(id).subscribe({next:mensajes=>{if(revision===this.revision){this.mensajes.set(mensajes);this.cargando.set(false);}},error:()=>{if(revision===this.revision){this.cargando.set(false);this.error.set('No se pudo cargar la conversación.');}}});}
 enviar(){const contenido=this.texto.trim();if(!contenido || !this.casoId() || this.enviando())return;this.enviando.set(true);this.error.set('');const revision=this.revision;
 this.mensajesApi.send(this.casoId(),contenido).subscribe({next:mensaje=>{if(revision===this.revision){this.mensajes.update(xs=>[...xs,mensaje]);this.texto='';}this.enviando.set(false);},error:()=>{this.enviando.set(false);this.error.set('No se pudo enviar el mensaje. El texto se conserva para reintentar.');}});}
}
