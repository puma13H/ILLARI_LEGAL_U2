export type Role = 'ADMIN'|'ABOGADO'|'CLIENTE';
export interface Field { key:string; label:string; type?:'text'|'number'|'email'|'date'|'datetime-local'|'textarea'; required?:boolean; options?:string[]; }
export interface Module {key:string;title:string;description:string;roles:Role[];fields:Field[];editable:Role[];real?:string;}
export interface Entry {id:string;[key:string]:string|number;}
const all:Role[]=['ADMIN','ABOGADO','CLIENTE'];const staff:Role[]=['ADMIN','ABOGADO'];
const name:Field={key:'nombre',label:'Nombre',required:true};
const description:Field={key:'descripcion',label:'Descripción',type:'textarea',required:true};
const money:Field={key:'importe',label:'Importe (S/)',type:'number',required:true};
const email:Field={key:'email',label:'Correo',type:'email',required:true};
const status=(options:string[]):Field=>({key:'estado',label:'Estado',options,required:true});
export const MODULES:Module[]=[
 {key:'asesorias',title:'Asesorías',description:'Servicios jurídicos y solicitudes de consulta.',roles:all,editable:staff,real:'/servicios',fields:[name,description,{key:'categoria',label:'Especialidad',options:['Civil','Penal','Laboral','Empresarial'],required:true},money,status(['Publicada','Borrador','Archivada'])]},
 {key:'productos',title:'Productos jurídicos',description:'Catálogo y disponibilidad de recursos jurídicos.',roles:staff,editable:staff,real:'/servicios',fields:[name,description,{key:'categoria',label:'Categoría',options:['Códigos','Libros','Escritos'],required:true},money,{key:'stock',label:'Existencias',type:'number',required:true},status(['Disponible','Agotado','Archivado'])]},
 {key:'pedidos',title:'Pedidos',description:'Detalle y seguimiento de pedidos. Sin cobros ni entregas reales en demostración.',roles:all,editable:['ADMIN'],real:'/mis-ordenes',fields:[name,{key:'cliente',label:'Cliente',required:true},description,money,status(['Pendiente','En preparación','Entregado','Cancelado'])]},
 {key:'citas',title:'Citas',description:'Solicitudes de atención jurídica. Las citas demo no se reservan en el servidor.',roles:all,editable:all,real:'/calendario',fields:[name,{key:'fecha',label:'Fecha y hora',type:'datetime-local',required:true},{key:'especialidad',label:'Especialidad',options:['Civil','Penal','Laboral','Empresarial'],required:true},{key:'modalidad',label:'Modalidad',options:['En línea','Presencial'],required:true},description,status(['Solicitada','Confirmada','Cancelada','Atendida'])]},
 {key:'expedientes',title:'Expedientes y casos',description:'Seguimiento de asuntos y expedientes asignados.',roles:all,editable:staff,real:'/casos',fields:[name,{key:'cliente',label:'Cliente vinculado',required:true},{key:'abogado',label:'Abogado asignado',required:true},description,status(['En trámite','Pendiente','Finalizado'])]},
 {key:'agenda',title:'Agenda',description:'Audiencias, reuniones y actividades profesionales.',roles:staff,editable:staff,real:'/calendario',fields:[name,{key:'fecha',label:'Fecha y hora',type:'datetime-local',required:true},{key:'lugar',label:'Lugar o modalidad',required:true},description,status(['Programado','Realizado','Cancelado'])]},
 {key:'clientes',title:'Clientes',description:'ADMIN: directorio general. ABOGADO: clientes vinculados a sus expedientes demo.',roles:staff,editable:staff,real:'/clientes',fields:[name,email,{key:'telefono',label:'Teléfono',required:true},status(['Activo','Inactivo'])]},
 {key:'abogados',title:'Abogados',description:'Directorio profesional de demostración.',roles:['ADMIN'],editable:['ADMIN'],real:'/abogados',fields:[name,email,{key:'especialidad',label:'Especialidad',options:['Civil','Penal','Laboral','Empresarial'],required:true},status(['Activo','Inactivo'])]},
 {key:'usuarios',title:'Usuarios',description:'Directorio demo. Ninguna acción crea cuentas o concede roles en Keycloak.',roles:['ADMIN'],editable:['ADMIN'],real:'/usuarios',fields:[name,email,{key:'perfil',label:'Perfil informativo (sin permisos)'},status(['Activo','Inactivo'])]},
 {key:'pagos',title:'Pagos',description:'Consulta de registros de ejemplo. No existe cobro, devolución ni integración bancaria en esta vista.',roles:['ADMIN'],editable:[],real:'/mis-ordenes',fields:[name,{key:'pedido',label:'Referencia de pedido',required:true},money,{key:'metodo',label:'Método de ejemplo',required:true},status(['Pendiente','Simulado','Rechazado'])]}
];
export function canOpen(role:string,key:string):boolean {return MODULES.some(m=>m.key===key&&m.roles.includes(role as Role));}
export function canEdit(role:string,key:string):boolean {return MODULES.some(m=>m.key===key&&m.editable.includes(role as Role));}
export function seed(role:Role):Record<string,Entry[]> {
 const own=role==='CLIENTE'?'Cliente de ejemplo':'Cliente vinculado de ejemplo';
 return {
 asesorias:[{id:'demo-asesoria',nombre:'Consulta civil (demo)',descripcion:'Orientación ficticia para presentar el módulo.',categoria:'Civil',importe:150,estado:'Publicada'}],
 productos:[{id:'demo-producto',nombre:'Código Civil (demo)',descripcion:'Recurso jurídico ficticio.',categoria:'Códigos',importe:90,stock:12,estado:'Disponible'}],
 pedidos:[{id:'demo-pedido',nombre:'Pedido de ejemplo #001',cliente:own,descripcion:'2 códigos civiles × S/ 90 + 1 asesoría × S/ 150. Total: S/ 330.',importe:330,estado:'Pendiente'}],
 citas:[{id:'demo-cita',nombre:'Consulta inicial (demo)',fecha:'2026-11-03T10:00',especialidad:'Civil',modalidad:'En línea',descripcion:'Solicitud ficticia; sin reserva real.',estado:'Solicitada'}],
 expedientes:[{id:'demo-caso',nombre:'Expediente DEMO-001',cliente:own,abogado:'Profesional de ejemplo',descripcion:'Revisión de contrato ficticio. Documentos de muestra disponibles desde el detalle.',estado:'En trámite'}],
 agenda:[{id:'demo-agenda',nombre:'Revisión de expediente (demo)',fecha:'2026-11-03T11:00',lugar:'En línea',descripcion:'Actividad ficticia de agenda.',estado:'Programado'}],
 clientes:[{id:'demo-cliente',nombre:own,email:'cliente@example.test',telefono:'000000000',estado:'Activo'}],
 abogados:[{id:'demo-abogado',nombre:'Profesional de ejemplo',email:'abogado@example.test',especialidad:'Civil',estado:'Activo'}],
 usuarios:[{id:'demo-usuario',nombre:'Usuario ficticio',email:'usuario@example.test',perfil:'Referencia de demostración',estado:'Activo'}],
 pagos:[{id:'demo-pago',nombre:'Registro de pago de ejemplo',pedido:'DEMO-001',importe:330,metodo:'Simulación sin cobro',estado:'Pendiente'}]
 };
}
export function validate(module:Module,record:Record<string,string|number>,role:Role):string|null {
 for(const field of module.fields){
  const value=String(record[field.key]??'').trim();
  if(field.required&&!value)return 'Completa el campo '+field.label+'.';
  if(value.length>2000)return field.label+': máximo 2000 caracteres.';
  if(field.options&& !field.options.includes(value))return 'Selecciona un valor válido para '+field.label+'.';
  if(field.type==='number'&&(!value||!Number.isFinite(Number(value))||Number(value)<0))return field.label+' debe ser un número mayor o igual a cero.';
  if(field.key==='stock'&&!Number.isSafeInteger(Number(value)))return 'Las existencias deben ser un número entero.';
  if(field.type==='email'&&!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value))return 'Introduce un correo válido.';
  if((field.type==='date'||field.type==='datetime-local')&&!Number.isFinite(Date.parse(value)))return 'Introduce una fecha válida.';
 }
 if(role==='CLIENTE'&&module.key==='citas'&&record['estado']!=='Solicitada'&&record['estado']!=='Cancelada')return 'El cliente solo puede solicitar o cancelar una cita.';
 return null;
}
export class DemoRepository {
 readonly key:string;
 constructor(private storage:Storage,subject:string,role:Role){this.key='illari_panel_demo_v1:'+encodeURIComponent(subject)+':'+role;}
 read(role:Role):Record<string,Entry[]> {
  const raw=this.storage.getItem(this.key);if(!raw)return seed(role);
  const parsed:unknown=JSON.parse(raw);
  if(!parsed||typeof parsed!=='object'||Array.isArray(parsed))throw new Error('Datos demo inválidos.');
  const data=parsed as Record<string,Entry[]>;
  for(const m of MODULES){if(!Array.isArray(data[m.key])||data[m.key].some(r=>!r||typeof r!=='object'||typeof r.id!=='string'||Object.values(r).some(v=>typeof v!=='string'&&typeof v!=='number')))throw new Error('Datos demo inválidos.');}
  return data;
 }
 write(data:Record<string,Entry[]>):void {this.storage.setItem(this.key,JSON.stringify(data));}
}
export function csv(columns:Field[],rows:Entry[]):string {
 const escape=(s:unknown)=>'"'+String(s??'').replace(/^[=+@-]/,"'$&").replaceAll('"','""')+'"';
 return '\uFEFF'+[columns.map(f=>escape(f.label)).join(';'),...rows.map(r=>columns.map(f=>escape(r[f.key])).join(';'))].join('\r\n');
}
