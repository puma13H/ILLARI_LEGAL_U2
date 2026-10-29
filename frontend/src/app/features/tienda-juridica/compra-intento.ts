export interface SolicitudCompra {
  metodoPago: string;
  detalles: { idServicio: number; cantidad: number }[];
}
export interface IntentoCompra { clave: string; traceId?: string; solicitud: SolicitudCompra; huella: string; }
type Almacen = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

/** Persist before sending; an uncertain response never creates a new purchase attempt. */
export class CompraIntentoStore {
  constructor(private readonly storage: Almacen, private readonly uuid: () => string = () => crypto.randomUUID()) {}
  private key(cliente: string) { return 'illari_compra_pendiente_' + encodeURIComponent(cliente); }
  actual(cliente: string): IntentoCompra | null {
    const value = this.storage.getItem(this.key(cliente));
    if (!value) return null;
    const intento = JSON.parse(value) as IntentoCompra;
    if (!intento.clave || !intento.solicitud || !Array.isArray(intento.solicitud.detalles)) {
      throw new Error('El intento pendiente está dañado; verifica tus solicitudes antes de iniciar otra compra.');
    }
    if (!intento.traceId) {
      intento.traceId = crypto.randomUUID();
      this.storage.setItem(this.key(cliente), JSON.stringify(intento));
    }
    return intento;
  }
  preparar(cliente: string, solicitud: SolicitudCompra): IntentoCompra {
    const normal = { metodoPago: solicitud.metodoPago.trim().toUpperCase(),
      detalles: [...solicitud.detalles].sort((a, b) => a.idServicio - b.idServicio) };
    const huella = JSON.stringify(normal);
    const previo = this.actual(cliente);
    if (previo) {
      if (previo.huella !== huella) throw new Error('Hay una compra sin confirmar. Reintenta la compra original antes de cambiar el carrito.');
      return previo;
    }
    const intento = { clave: this.uuid(), traceId: crypto.randomUUID(), solicitud: normal, huella };
    this.storage.setItem(this.key(cliente), JSON.stringify(intento));
    return intento;
  }
  completar(cliente: string, clave: string): void {
    if (this.actual(cliente)?.clave === clave) this.storage.removeItem(this.key(cliente));
  }
}
