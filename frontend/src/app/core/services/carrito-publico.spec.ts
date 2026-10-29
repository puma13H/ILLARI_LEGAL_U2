import { afterEach, describe, expect, it, vi } from 'vitest';
import { CarritoPublicoService } from './carrito-publico.service';
const stores:CarritoPublicoService[]=[];
const service=()=>{const c=new CarritoPublicoService();stores.push(c);return c;};
const p={id:10,nombre:'Código',categoria:'Códigos',descripcion:'Libro',precio:0.1,icono:'§'};
afterEach(()=>{stores.forEach(s=>s.ngOnDestroy());stores.length=0;sessionStorage.clear();vi.restoreAllMocks();});
describe('carrito compartido de inicio y tienda',()=>{
 it('mantiene múltiples productos y calcula importes en céntimos',()=>{const c=service();expect(c.add(p)).toBe(true);c.add(p);c.add({...p,id:11,precio:0.2});expect(c.cantidad()).toBe(3);expect(c.subtotal()).toBe(0.4);const next=service();expect(next.cantidad()).toBe(3);});
 it('recibe cambios de cantidad y vaciado realizados en la tienda',()=>{const c=service();sessionStorage.setItem('illari_legal_carrito',JSON.stringify([{producto:p,cantidad:4}]));window.dispatchEvent(new Event('illari-cart-updated'));expect(c.cantidad()).toBe(4);sessionStorage.removeItem('illari_legal_carrito');window.dispatchEvent(new Event('illari-cart-updated'));expect(c.cantidad()).toBe(0);});
 it('rechaza mezcla real/demo sin alterar el carrito',()=>{const c=service();c.add(p);expect(c.add({...p,id:-1})).toBe(false);expect(c.error()).toContain('mezclar');expect(c.cantidad()).toBe(1);});
 it('bloquea cambios mientras existe una compra incierta',()=>{const c=service();sessionStorage.setItem('illari_compra_pendiente_test','{}');expect(c.add(p)).toBe(false);expect(c.error()).toContain('pendiente');expect(c.cantidad()).toBe(0);});
 it('no sobrescribe almacenamiento dañado ni admite precios inválidos',()=>{sessionStorage.setItem('illari_legal_carrito','bad json');const c=service();expect(c.add(p)).toBe(false);expect(sessionStorage.getItem('illari_legal_carrito')).toBe('bad json');sessionStorage.removeItem('illari_legal_carrito');expect(c.add({...p,precio:-1})).toBe(false);});
});
