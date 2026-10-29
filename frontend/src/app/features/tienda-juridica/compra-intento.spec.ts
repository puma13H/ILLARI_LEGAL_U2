import { describe, expect, it } from 'vitest';
import { CompraIntentoStore } from './compra-intento';

function storage() {
  const data = new Map<string, string>();
  return { getItem: (k: string) => data.get(k) ?? null,
    setItem: (k: string, v: string) => { data.set(k, v); }, removeItem: (k: string) => { data.delete(k); } };
}
const solicitud = { metodoPago: 'Yape', detalles: [{ idServicio: 7, cantidad: 2 }, { idServicio: 8, cantidad: 1 }] };

describe('compras con respuestas inciertas', () => {
  it('reutiliza la clave tras un timeout y tras recargar la página', () => {
    const s = storage();
    const first = new CompraIntentoStore(s, () => 'key-original').preparar('client', solicitud);
    expect(new CompraIntentoStore(s, () => 'key-nueva').preparar('client', solicitud)).toEqual(first);
  });
  it('normaliza método y orden de detalles', () => {
    const store = new CompraIntentoStore(storage(), () => 'key-original');
    const first = store.preparar('client', solicitud);
    expect(store.preparar('client', { metodoPago: 'YAPE', detalles: [...solicitud.detalles].reverse() })).toEqual(first);
  });
  it('impide cambiar cantidades en un intento sin resolver', () => {
    const store = new CompraIntentoStore(storage()); store.preparar('client', solicitud);
    expect(() => store.preparar('client', { ...solicitud, detalles: [{ idServicio: 7, cantidad: 3 }] })).toThrow();
  });
  it('separa los intentos de dos usuarios', () => {
    let n = 0; const store = new CompraIntentoStore(storage(), () => 'key-' + ++n);
    expect(store.preparar('a', solicitud).clave).not.toBe(store.preparar('b', solicitud).clave);
  });
  it('solo limpia la clave confirmada y permite una compra nueva después', () => {
    let n = 0; const store = new CompraIntentoStore(storage(), () => 'key-' + ++n);
    const first = store.preparar('client', solicitud);
    store.completar('client', 'respuesta-antigua'); expect(store.actual('client')).toEqual(first);
    store.completar('client', first.clave); expect(store.preparar('client', solicitud).clave).not.toBe(first.clave);
  });
  it('detiene la compra si no se puede persistir la clave', () => {
    const s = storage(); s.setItem = () => { throw new Error('Storage lleno'); };
    expect(() => new CompraIntentoStore(s).preparar('client', solicitud)).toThrow('Storage lleno');
  });
});
