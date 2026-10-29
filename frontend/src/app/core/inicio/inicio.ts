import { Component, ElementRef, inject, OnDestroy, OnInit, AfterViewInit, signal, computed, ViewChild } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subscription, timeout } from 'rxjs';
import { ServicioLegalService } from '../../features/catalogo/servicio.service';
import { CarritoPublicoService, PublicProduct } from '../services/carrito-publico.service';
import { CATALOGO_DEMO } from '../../features/tienda-juridica/catalogo-demo';
import { LegalAvatarComponent } from '../../shared/legal-avatar/legal-avatar.component';
@Component({selector:'app-inicio',standalone:true,imports:[RouterLink,CurrencyPipe,FormsModule,LegalAvatarComponent],templateUrl:'./inicio.html',styleUrl:'./inicio.css'})
export class Inicio implements OnInit,OnDestroy,AfterViewInit {
 readonly cart=inject(CarritoPublicoService);private readonly api=inject(ServicioLegalService);
 @ViewChild('specialties')specialties?:ElementRef<HTMLDivElement>;
 @ViewChild('lawyer')lawyer?:LegalAvatarComponent;
 readonly assistantOpen=signal(false);readonly loading=signal(true);readonly catalogError=signal('');readonly demo=signal(false);readonly notice=signal('');
 readonly products=signal<PublicProduct[]>([]);readonly search=signal('');readonly category=signal('Todas');
 readonly categories=computed(()=>['Todas',...new Set(this.products().map(p=>p.categoria))]);
 readonly filtered=computed(()=>this.products().filter(p=>(this.category()==='Todas'||p.categoria===this.category())&&`${p.nombre} ${p.descripcion} ${p.categoria}`.toLocaleLowerCase('es').includes(this.search().trim().toLocaleLowerCase('es'))));
 readonly canPrevious=signal(false);readonly canNext=signal(false);private resize?:ResizeObserver;
 private request?:Subscription;
 readonly areas=[
 {name:'Derecho Civil',query:'Civil',icon:'scales',description:'Contratos, bienes, sucesiones y más.',position:'0% 0%'},
 {name:'Derecho Penal',query:'Penal',icon:'scales',description:'Defensa y asesoría en procesos penales.',position:'50% 0%'},
 {name:'Derecho Laboral',query:'Laboral',icon:'building',description:'Relaciones laborales, despidos y beneficios.',position:'100% 0%'},
 {name:'Derecho Familiar',query:'Familiar',icon:'people',description:'Divorcios, alimentos, tenencia y más.',position:'0% 100%'},
 {name:'Derecho Empresarial',query:'Empresarial',icon:'building',description:'Constitución, contratos y asesoría corporativa.',position:'50% 100%'},
 {name:'Tienda Jurídica',query:'',icon:'cart',description:'Códigos, libros y modelos de escritos.',position:'100% 100%'}
 ];
 ngOnInit():void{this.cart.refresh();this.loadCatalog();}
 ngAfterViewInit():void{this.updateScroll();if(typeof ResizeObserver!=='undefined'&&this.specialties){this.resize=new ResizeObserver(()=>this.updateScroll());this.resize.observe(this.specialties.nativeElement);}}
 updateScroll():void{const el=this.specialties?.nativeElement;if(el){this.canPrevious.set(el.scrollLeft>2);this.canNext.set(el.scrollLeft+el.clientWidth<el.scrollWidth-2);}}
 ngOnDestroy():void{this.request?.unsubscribe();this.resize?.disconnect();}
 loadCatalog():void {
  this.request?.unsubscribe();this.loading.set(true);this.catalogError.set('');this.notice.set('');
  this.request=this.api.listar().pipe(timeout(6000)).subscribe({next:data=>{
   this.demo.set(false);this.products.set(data.filter(p=>Number.isSafeInteger(p.id)&&p.id!>0&&typeof p.precio==='number'&&Number.isFinite(p.precio)&&p.precio>=0&&!/^(inactivo|inactiva)$/i.test(p.estado??''))
    .map(p=>({id:p.id!,nombre:p.nombre,descripcion:p.descripcion??'Recurso del catálogo de ILLARI LEGAL.',categoria:p.categoria??'Servicios legales',precio:p.precio!,icono:/asesor|consult/i.test(p.categoria??'')?'⚖':'§'})));
   this.category.set('Todas');this.loading.set(false);
  },error:()=>{this.loading.set(false);this.catalogError.set('El catálogo real no está disponible en este momento. Puedes explorar ejemplos claramente identificados.');}});
 }
 showDemo():void{this.request?.unsubscribe();this.demo.set(true);this.products.set(CATALOGO_DEMO);this.category.set('Todas');this.loading.set(false);this.catalogError.set('');this.notice.set('Datos de demostración: precios de ejemplo, sin compra ni reserva real.');}
 add(product:PublicProduct):void {
  this.cart.clearError();
  if(this.cart.add(product))this.notice.set(product.nombre+' agregado al carrito'+(product.id<0?' de demostración.':'.'));
  else this.notice.set(this.cart.error());
 }
 scrollAreas(direction:number):void{const reduced=window.matchMedia('(prefers-reduced-motion: reduce)').matches;this.specialties?.nativeElement.scrollBy({left:direction*300,behavior:reduced?'instant':'smooth'});}
 point():void{this.lawyer?.gesture('senalar');}
 rest():void{this.lawyer?.gesture('reposo');}
 closeAssistant():void{this.assistantOpen.set(false);}
}
