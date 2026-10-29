import { Injectable, computed, signal, OnDestroy } from '@angular/core';
export interface PublicProduct { id:number;nombre:string;categoria:string;descripcion:string;precio:number;icono:string; }
export interface CartItem {producto:PublicProduct;cantidad:number;}
const KEY='illari_legal_carrito';
export function validItem(value:unknown):value is CartItem {
 const i=value as CartItem|undefined;const p=i?.producto;
 return !!p&&Number.isSafeInteger(i?.cantidad)&&i!.cantidad>0&&Number.isSafeInteger(p.id)&&p.id!==0&&typeof p.nombre==='string'&&typeof p.categoria==='string'&&typeof p.descripcion==='string'&&typeof p.icono==='string'&&typeof p.precio==='number'&&Number.isFinite(p.precio)&&p.precio>=0;
}
@Injectable({providedIn:'root'})
export class CarritoPublicoService implements OnDestroy {
 readonly items=signal<CartItem[]>([]);readonly error=signal('');
 readonly cantidad=computed(()=>this.items().reduce((n,i)=>n+i.cantidad,0));
 readonly subtotal=computed(()=>this.items().reduce((n,i)=>n+Math.round(i.producto.precio*100)*i.cantidad,0)/100);
 constructor(){this.refresh();window.addEventListener('illari-cart-updated',this.refresh);}
 refresh=():void=>{try{const data:unknown=JSON.parse(sessionStorage.getItem(KEY)??'[]');if(!Array.isArray(data)||!data.every(validItem))throw new Error('invalid');this.items.set(data);}catch{this.items.set([]);this.error.set('No se pudo leer el carrito guardado. Revisa la tienda antes de continuar.');}};
 add(product:PublicProduct):boolean {
  this.error.set('');this.refresh();if(this.error())return false;
  if(!validItem({producto:product,cantidad:1})){this.error.set('Producto no válido.');return false;}
  for(let i=0;i<sessionStorage.length;i++){if(sessionStorage.key(i)?.startsWith('illari_compra_pendiente_')){this.error.set('Hay una compra pendiente. Resuélvela en la tienda antes de modificar el carrito.');return false;}}
  if(this.items().some(i=>(i.producto.id<0)!==(product.id<0))){this.error.set('No puedes mezclar productos reales y de demostración. Revisa tu carrito en la tienda.');return false;}
  const existing=this.items().find(i=>i.producto.id===product.id);
  const next=existing?this.items().map(i=>i.producto.id===product.id?{producto:product,cantidad:i.cantidad+1}:i):[...this.items(),{producto:product,cantidad:1}];
  try{sessionStorage.setItem(KEY,JSON.stringify(next));this.items.set(next);window.dispatchEvent(new Event('illari-cart-updated'));return true;}catch{this.error.set('No se pudo guardar el carrito.');return false;}
 }
 ngOnDestroy():void{window.removeEventListener('illari-cart-updated',this.refresh);}
 clearError():void{this.error.set('');}
}
