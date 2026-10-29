import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, timeout } from 'rxjs';
import { AuthService } from '../../core/auth/auth-service';
import { DashboardService } from '../../core/services/dashboard.service';
import { DashboardStats } from '../../core/models';
import { canEdit, csv, DemoRepository, Entry, Field, MODULES, Module, Role, validate } from './panel-data';
@Component({selector:'app-panel',standalone:true,imports:[RouterLink,FormsModule,CurrencyPipe,DatePipe],templateUrl:'./panel.component.html',styleUrl:'./panel.component.css'})
export class PanelComponent implements OnInit,OnDestroy {
 readonly auth=inject(AuthService);private route=inject(ActivatedRoute);private api=inject(DashboardService);
 private sub=new Subscription();private request?:Subscription;private repository?:DemoRepository;
 readonly role=(this.auth.currentUser()?.role.toUpperCase()??'') as Role;
 readonly modules=MODULES.filter(m=>m.roles.includes(this.role));
 readonly key=signal('');readonly data=signal<Record<string,Entry[]>>({});readonly demo=signal(false);
 readonly connection=signal<'loading'|'online'|'offline'|'denied'>('loading');readonly realStats=signal<DashboardStats|null>(null);
 readonly message=signal('');readonly error=signal('');readonly query=signal('');readonly stateFilter=signal('');readonly sort=signal('nombre');readonly page=signal(1);
 readonly module=computed(()=>this.modules.find(m=>m.key===this.key()));
 readonly rows=computed(()=>this.data()[this.key()]??[]);
 readonly filtered=computed(()=>{
  const q=this.query().trim().toLocaleLowerCase('es');const state=this.stateFilter();
  return this.rows().filter(r=>(!q||Object.values(r).some(v=>String(v).toLocaleLowerCase('es').includes(q)))&&(!state||r['estado']===state))
  .sort((a,b)=>this.sort()==='importe'?Number(a['importe'])-Number(b['importe']):String(a[this.sort()]??'').localeCompare(String(b[this.sort()]??''),'es'));
 });
 readonly pages=computed(()=>Math.max(1,Math.ceil(this.filtered().length/8)));
 readonly visible=computed(()=>this.filtered().slice((this.page()-1)*8,this.page()*8));
 readonly states=computed(()=>this.module()?.fields.find(f=>f.key==='estado')?.options??[]);
 readonly editable=computed(()=>this.demo()&&canEdit(this.role,this.key()));
 readonly totalOrders=computed(()=>(this.data()['pedidos']??[]).reduce((n,r)=>n+Number(r['importe']??0),0));
 readonly reportRows=computed(()=>this.modules.map(m=>({module:m,count:(this.data()[m.key]??[]).length})));
 form:Record<string,string|number>={};editing:string|null=null;showForm=false;detail:Entry|null=null;removeTarget:Entry|null=null;
 ngOnInit():void {
  if(!this.auth.autenticado()||!this.auth.idCliente()||!['ADMIN','ABOGADO','CLIENTE'].includes(this.role)){this.connection.set('denied');return;}
  this.repository=new DemoRepository(sessionStorage,this.auth.idCliente()!,this.role);
  this.sub.add(this.route.paramMap.subscribe(p=>{this.key.set(p.get('modulo')??'');this.resetView();}));
  this.checkBackend();
 }
 ngOnDestroy():void{this.sub.unsubscribe();this.request?.unsubscribe();}
 checkBackend():void {
  this.request?.unsubscribe();this.connection.set('loading');this.realStats.set(null);
  this.request=this.api.stats().pipe(timeout(6000)).subscribe({next:stats=>{this.realStats.set(stats);this.connection.set('online');this.restoreDemo();},error:(e:HttpErrorResponse)=>{ console.error('ILLARI DASHBOARD HTTP', e.status, e.message); this.error.set('Diagnóstico de conexión: HTTP ' + e.status + ' - ' + e.message);
   if(e.status && e.status<500){this.connection.set('denied');this.demo.set(false);this.data.set({});return;}
   this.connection.set('offline');this.restoreDemo();
  }});
 }
 activateDemo():void {
  if(!this.repository||!this.auth.autenticado()||this.connection()==='denied'||this.connection()==='loading')return;
  try{this.data.set(this.repository.read(this.role));sessionStorage.setItem(this.repository.key+':active','1');this.demo.set(true);this.error.set('');this.message.set('DemostraciÃ³n activada. Los cambios solo se guardan en esta pestaÃ±a para tu usuario.');}
  catch{this.error.set('No se pudieron leer los datos locales de demostraciÃ³n. No se sobrescribieron.');}
 }
 private restoreDemo():void{try{if(this.repository&&sessionStorage.getItem(this.repository.key+':active')==='1')this.activateDemo();}catch{this.error.set('No se pudo recuperar la preferencia local de demostraciÃ³n.');}}
 leaveDemo():void{try{if(this.repository)sessionStorage.removeItem(this.repository.key+':active');this.demo.set(false);this.data.set({});this.resetView();}catch{this.error.set('No se pudo cerrar la demostraciÃ³n local.');}}
 resetView():void{this.query.set('');this.stateFilter.set('');this.page.set(1);this.showForm=false;this.detail=null;this.removeTarget=null;this.error.set('');this.message.set('');}
 filterChanged():void{this.page.set(1);}
 newRecord():void {
  if(!this.editable())return;this.editing=null;this.form={};
  for(const f of this.module()!.fields)this.form[f.key]=f.options?.[0]??(f.type==='number'?0:'');
  if(this.key()==='citas'&&this.role==='CLIENTE')this.form['estado']='Solicitada';
  this.showForm=true;this.error.set('');
 }
 editRecord(row:Entry):void{if(!this.editable())return;this.editing=row.id;this.form={...row};this.showForm=true;this.error.set('');}
 fieldOptions(f:Field):string[]{return this.role==='CLIENTE'&&this.key()==='citas'&&f.key==='estado'?['Solicitada','Cancelada']:f.options??[];}
 save():void {
  if(!this.editable()||!this.module())return;
  const invalid=validate(this.module()!,this.form,this.role);if(invalid){this.error.set(invalid);return;}
  const record:Entry={id:this.editing??'demo-'+crypto.randomUUID()};
  for(const f of this.module()!.fields)record[f.key]=f.type==='number'?Math.round(Number(this.form[f.key])*100)/100:String(this.form[f.key]??'').trim();
  const rows=this.editing?this.rows().map(r=>r.id===this.editing?record:r):[record,...this.rows()];
  if(this.persist({...this.data(),[this.key()]:rows})){this.showForm=false;this.message.set('Registro demo guardado. No se enviÃ³ al servidor.');this.page.set(1);}
 }
 deleteRecord():void {
  if(!this.editable()||!this.removeTarget)return;
  if(this.persist({...this.data(),[this.key()]:this.rows().filter(r=>r.id!==this.removeTarget!.id)})){this.removeTarget=null;this.message.set('Registro demo eliminado de esta pestaÃ±a.');this.page.set(Math.min(this.page(),this.pages()));}
 }
 private persist(data:Record<string,Entry[]>):boolean {
  if(!this.auth.autenticado()||!this.repository)return false;
  try{this.repository.write(data);this.data.set(data);this.error.set('');return true;}catch{this.error.set('No se pudo guardar el cambio local. No se aplicÃ³.');return false;}
 }
 exportCsv():void {
  const module=this.module();if(!this.demo()||!module)return;
  this.download(csv(module.fields,this.filtered()),'illari-'+module.key+'-DEMO.csv','text/csv;charset=utf-8');
  this.message.set('ExportaciÃ³n de datos de demostraciÃ³n generada.');
 }
 exportReport():void {
  if(!this.demo())return;
  const fields:Field[]=[{key:'nombre',label:'MÃ³dulo'},{key:'cantidad',label:'Registros demo'}];
  this.download(csv(fields,this.reportRows().map(r=>({id:r.module.key,nombre:r.module.title,cantidad:r.count}))),'illari-reporte-DEMO.csv','text/csv;charset=utf-8');
 }
 private download(content:string,name:string,type:string):void {
  const url=URL.createObjectURL(new Blob([content],{type}));const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
 }
 downloadDocument():void{if(this.demo()&&this.detail)this.download('ILLARI LEGAL â€” DOCUMENTO DE DEMOSTRACIÃ“N\n\n'+this.detail['nombre']+'\n'+this.detail['descripcion']+'\n\nContenido ficticio, sin validez ni expediente real.','expediente-DEMO.txt','text/plain;charset=utf-8');}
 currencyField(f:Field):boolean{return f.key==='importe';}
}
