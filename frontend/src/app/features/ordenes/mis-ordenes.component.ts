import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { forkJoin, of, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth-service';

interface OrdenLegal {
  id: number;
  fechaCreacion: string;
  estado: string;
  total: number;
  metodoPago: string;
  detalles: DetalleOrden[];
}

interface DetalleOrden {
  idServicio: number;
  nombreServicio: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

interface Pago {
  id: number;
  monto: number;
  estado: string;
  fechaCreacion: string;
  fechaAprobacion: string | null;
}

interface OrdenConPagos {
  orden: OrdenLegal;
  pagos: Pago[];
}

@Component({
  selector: 'app-mis-ordenes',
  standalone: true,
  imports: [CurrencyPipe, DatePipe],
  template: `
    <section class="page-header">
      <div>
        <h1 class="page-title">Mis solicitudes y pagos</h1>
        <p class="page-subtitle">Consulta tus órdenes de servicios legales y el resultado del pago.</p>
      </div>
    </section>

    @if (cargando()) {
      <p class="card">Cargando tus órdenes...</p>
    } @else if (error()) {
      <p class="alert alert-error">{{ error() }}</p>
    } @else if (ordenes().length === 0) {
      <p class="card">Todavía no tienes solicitudes. Explora la tienda jurídica para crear una orden.</p>
    } @else {
      @for (item of ordenes(); track item.orden.id) {
        <article class="card orden-card">
          <header class="orden-header">
            <div>
              <h2>Orden #{{ item.orden.id }}</h2>
              <p>Creada {{ item.orden.fechaCreacion | date:'medium' }}</p>
            </div>
            <span class="estado">{{ item.orden.estado }}</span>
          </header>
          <div class="tabla-wrap">
            <table class="data-table">
              <thead>
                <tr><th>Servicio</th><th>Cantidad</th><th>Precio</th><th>Subtotal</th></tr>
              </thead>
              <tbody>
                @for (detalle of item.orden.detalles; track detalle.idServicio) {
                  <tr>
                    <td>{{ detalle.nombreServicio }}</td>
                    <td>{{ detalle.cantidad }}</td>
                    <td>{{ detalle.precioUnitario | currency:'PEN':'S/ ' }}</td>
                    <td>{{ detalle.subtotal | currency:'PEN':'S/ ' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <footer class="orden-footer">
            <span>Método: {{ item.orden.metodoPago }}</span>
            <strong>Total: {{ item.orden.total | currency:'PEN':'S/ ' }}</strong>
          </footer>
          @if (item.pagos.length > 0) {
            <div class="pago-info">
              @for (pago of item.pagos; track pago.id) {
                <span>Pago #{{ pago.id }} — {{ pago.estado }}
                  @if (pago.fechaAprobacion) { · {{ pago.fechaAprobacion | date:'medium' }} }
                </span>
              }
            </div>
          }
        </article>
      }
    }
  `,
  styles: [`
    .orden-card{margin-bottom:18px;padding:20px}
    .orden-header,.orden-footer{display:flex;justify-content:space-between;align-items:center;gap:14px}
    .orden-header h2{margin:0;font-size:18px}.orden-header p{margin:5px 0;color:var(--muted)}
    .estado{padding:6px 10px;border-radius:20px;background:#edf4fb;color:#17375e;font-weight:700;font-size:12px}
    .tabla-wrap{overflow-x:auto;margin:15px 0}
    .orden-footer{border-top:1px solid #e7eaf0;padding-top:14px}
    .pago-info{margin-top:12px;color:var(--muted);font-size:13px}
  `]
})
export class MisOrdenesComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  readonly ordenes = signal<OrdenConPagos[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const subject = this.auth.idCliente();
    if (!subject) {
      this.error.set('No se pudo identificar la sesión. Inicia sesión nuevamente.');
      this.cargando.set(false);
      return;
    }

    this.http.get<OrdenLegal[]>(`${environment.apiBaseUrl}/v1/ordenes/cliente/${encodeURIComponent(subject)}`).pipe(
      switchMap(orders => orders.length === 0
        ? of([])
        : forkJoin(orders.map(orden =>
          this.http.get<Pago[]>(`${environment.apiBaseUrl}/v1/pagos/orden/${orden.id}`).pipe(
            switchMap(pagos => of({ orden, pagos }))
          )
        )))
    ).subscribe({
      next: ordenes => {
        this.ordenes.set(ordenes);
        this.cargando.set(false);
      },
      error: (error: HttpErrorResponse) => {
        const detail = error.error?.detail ?? error.error?.message;
        this.error.set(typeof detail === 'string' && detail
          ? `No se pudieron cargar tus órdenes: ${detail}`
          : error.status
          ? `No se pudieron cargar tus órdenes (HTTP ${error.status}).`
          : 'No se pudieron cargar tus órdenes. Verifica el Gateway.');
        this.cargando.set(false);
      }
    });
  }
}
