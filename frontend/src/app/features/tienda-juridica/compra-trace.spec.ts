import { describe, expect, it } from 'vitest';
import { CompraIntentoStore } from './compra-intento';
function storage() { const data = new Map<string,string>(); return {getItem:(k:string)=>data.get(k)??null,setItem:(k:string,v:string)=>{data.set(k,v);},removeItem:(k:string)=>{data.delete(k);}}; }
const solicitud = {metodoPago:'YAPE',detalles:[{idServicio:1,cantidad:2}]};
describe('correlacion tecnica de compra',()=>{
  it('mantiene el trace al recargar y lo separa de la clave de compra',()=>{
    const s=storage();const first=new CompraIntentoStore(s).preparar('cliente',solicitud);
    expect(first.traceId).toBeTruthy();expect(first.traceId).not.toBe(first.clave);
    expect(new CompraIntentoStore(s).actual('cliente')?.traceId).toBe(first.traceId);
  });
  it('agrega un trace a intentos historicos sin cambiar la clave ni el cuerpo',()=>{
    const s=storage();const original=new CompraIntentoStore(s).preparar('cliente',solicitud);
    const antiguo={...original};delete antiguo.traceId;s.setItem('illari_compra_pendiente_cliente',JSON.stringify(antiguo));
    const upgraded=new CompraIntentoStore(s).actual('cliente');expect(upgraded?.clave).toBe(original.clave);
    expect(upgraded?.solicitud).toEqual(original.solicitud);expect(upgraded?.traceId).toBeTruthy();
    expect(new CompraIntentoStore(s).actual('cliente')?.traceId).toBe(upgraded?.traceId);
  });
});
