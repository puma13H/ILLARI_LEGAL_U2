import { CATALOGO_DEMO } from './catalogo-demo';
import { CommonModule } from '@angular/common';
import { Component, computed, OnInit, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { take, takeWhile, switchMap, timer } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth-service';
import { ServicioLegalService } from '../catalogo/servicio.service';
import { ServicioLegal } from '../catalogo/servicio.model';
import { CompraIntentoStore, IntentoCompra } from './compra-intento';

interface Producto {
  id: number;
  nombre: string;
  categoria: string;
  descripcion: string;
  precio: number;
  icono: string;
}

interface ArticuloCarrito {
  producto: Producto;
  cantidad: number;
}

const CARRITO_STORAGE_KEY = 'illari_legal_carrito';

function esArticuloCarrito(value: unknown): value is ArticuloCarrito {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const item = value as Partial<ArticuloCarrito>;
  const producto = item.producto;
  return Number.isSafeInteger(item.cantidad)
    && (item.cantidad ?? 0) > 0
    && typeof producto === 'object'
    && producto !== null
    && Number.isSafeInteger(producto.id)
    && typeof producto.nombre === 'string'
    && typeof producto.categoria === 'string'
    && typeof producto.descripcion === 'string'
    && typeof producto.precio === 'number'
    && Number.isFinite(producto.precio)
    && producto.precio >= 0
    && typeof producto.icono === 'string';
}

@Component({
  selector: 'app-tienda-juridica',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="tienda-shell">
      <header class="topbar">
        <a class="marca" routerLink="/">
          <span class="marca-icono" aria-hidden="true">⚖</span>
          <span class="marca-texto"><b>Illari Legal</b><small>Asesoría jurídica a tu alcance</small></span>
        </a>
        <label class="busqueda-global">
          <span aria-hidden="true">⌕</span>
          <input [ngModel]="busqueda()" (ngModelChange)="busqueda.set($event)" aria-label="Buscar en el catálogo"
            placeholder="Buscar servicios, libros, códigos, modelos de escritos..." />
        </label>
        <div class="acciones-top">
          
          <span class="avatar-usuario" aria-hidden="true">{{ inicialesUsuario() }}</span>
          <div class="usuario"><b>{{ nombreUsuario() }}</b><small>{{ auth.autenticado() ? 'Mi cuenta' : 'Visitante' }}</small></div>
          @if (auth.autenticado()) {<button class="cerrar-sesion" type="button" (click)="auth.cerrarSesion()">Cerrar sesión</button>} @else {<a class="cerrar-sesion" routerLink="/login">Iniciar sesión</a>}
        </div>
      </header>

      <div class="pagina">
        <aside class="sidebar">
          <nav class="menu" aria-label="Navegación del cliente">
            <a routerLink="/"><span>⌂</span>Inicio</a>
            <a class="activo" routerLink="/tienda-juridica"><span>🛒</span>Tienda Jurídica</a>
            <a routerLink="/servicios"><span>⚖</span>Servicios Legales</a>
            <a routerLink="/dashboard"><span>▦</span>Mi panel</a><a routerLink="/calendario"><span>▦</span>Mis citas</a><a routerLink="/casos"><span>▣</span>Mis casos</a><a routerLink="/mis-ordenes"><span>▤</span>Mis Solicitudes</a>
            <a routerLink="/mis-ordenes"><span>▣</span>Mis Pagos</a>
            <a routerLink="/documentos"><span>▧</span>Mis Documentos</a>
            <a routerLink="/configuracion"><span>♙</span>Mi Perfil</a>
          </nav>

          <div class="categorias-menu">
            <h2>CATEGORÍAS</h2>
            <button *ngFor="let cat of categorias()" type="button"
              [class.seleccionada]="categoria() === cat" (click)="seleccionarCategoria(cat)">
              <span>{{ iconoCategoria(cat) }}</span>{{ cat }}
            </button>
            <button type="button" [class.seleccionada]="categoria() === 'Todas'"
              (click)="seleccionarCategoria('Todas')"><span>▦</span>Todos los productos</button>
          </div>

          <div class="ayuda">
            <span class="ayuda-icono">?</span>
            <b>¿Necesitas ayuda?</b>
            <small>Encuentra una asesoría</small>
            <a routerLink="/servicios">Ver asesorías</a>
          </div>
        </aside>

        <main class="contenido">
          <section class="encabezado-tienda">
            <div>
              <h1>Tienda Jurídica</h1>
              <p>Encuentra asesorías, libros, códigos, modelos de escritos y más.</p>
            </div>
          </section>

          <section class="demo-notice" *ngIf="modoDemo()" role="status"><b>Catálogo de demostración</b><p>Productos y precios de ejemplo. Puedes probar el carrito; las compras están deshabilitadas.</p><button type="button" (click)="cargarCatalogo()" [disabled]="cargando()">Reintentar catálogo real</button></section>
          <nav class="pestanas" aria-label="Filtrar por tipo de producto">
            <button type="button" [class.activa]="categoria() === 'Todas'"
              (click)="seleccionarCategoria('Todas')"><span>▤</span>Todos</button>
            <button *ngFor="let cat of categorias()" type="button" [class.activa]="categoria() === cat"
              (click)="seleccionarCategoria(cat)"><span>{{ iconoCategoria(cat) }}</span>{{ cat }}</button>
          </nav>

          <section class="filtros">
            <label class="busqueda-local"><span>⌕</span><input [ngModel]="busqueda()" (ngModelChange)="busqueda.set($event)"
              placeholder="Buscar producto o servicio..." aria-label="Buscar producto o servicio" /></label>
            <label>Especialidad
              <select [ngModel]="categoria()" (ngModelChange)="seleccionarCategoria($event)">
                <option value="Todas">Todas</option>
                <option *ngFor="let cat of categorias()" [value]="cat">{{ cat }}</option>
              </select>
            </label>
            <label>Tipo
              <select [ngModel]="tipoSeleccionado()" (ngModelChange)="tipoSeleccionado.set($event)">
                <option value="Todos">Todos</option>
                <option value="Asesoría">Asesoría</option>
                <option value="Digital">Digital</option>
              </select>
            </label>
            <label>Ordenar por
              <select [ngModel]="orden()" (ngModelChange)="orden.set($event)">
                <option value="relevancia">Más relevantes</option>
                <option value="menor">Menor precio</option>
                <option value="mayor">Mayor precio</option>
                <option value="nombre">Nombre A-Z</option>
              </select>
            </label>
          </section>

          <p class="estado-tienda" *ngIf="cargando()">Cargando catálogo de servicios legales...</p>
          <p class="estado-tienda error" *ngIf="errorCatalogo()" role="alert">{{ errorCatalogo() }}</p><button type="button" *ngIf="!modoDemo() && !cargando() && (errorCatalogo() || !productos().length)" (click)="verDemo()">Explorar catálogo de demostración</button>
          <p class="estado-tienda" *ngIf="productos().length && !cargando()">
            {{ productosFiltrados().length }} productos disponibles para ti
          </p>

          <section class="catalogo" aria-label="Catálogo de servicios legales">
            <article class="producto" *ngFor="let p of productosFiltrados()">
              <div class="producto-imagen" [attr.data-cat]="p.categoria" aria-hidden="true">
                <span class="producto-etiqueta">{{ etiquetaProducto(p.categoria) }}</span>
                <span class="arte">{{ p.icono }}</span>
                <span class="texto-arte">{{ nombreCorto(p.nombre) }}</span>
              </div>
              <div class="producto-info">
                <h2>{{ p.nombre }}</h2>
                <p>{{ p.descripcion }}</p>
                <div class="detalle-producto">
                  <span>◷ {{ duracionProducto(p.categoria) }}</span>
                  <span>▣ {{ tipoProducto(p.categoria) }}</span>
                </div>
                <small class="disponibilidad">{{ modoDemo() ? 'Demostración · sin compra' : 'Disponible en catálogo' }}</small><strong class="precio">{{ p.precio | currency:'PEN':'S/ ':'1.2-2' }}</strong>
                <button class="agregar" type="button" [disabled]="procesando() || compraPendiente()" (click)="agregar(p)">
                  <span>🛒</span>Agregar al carrito
                </button>
              </div>
            </article>
            <div class="vacio" *ngIf="!cargando() && productosFiltrados().length === 0">
              No encontramos productos. Prueba otra búsqueda o categoría.
            </div>
          </section>
        </main>

        <aside class="carrito-panel" id="carrito">
          <div class="carrito-heading">
            <h2>Mi carrito de servicios</h2>
            <span class="contador">🛒 {{ cantidad() }}</span>
          </div>
          <div class="cabecera-carrito"><span>Producto / Servicio</span><span>Cantidad</span><span>Precio</span></div>
          <div class="carrito-vacio" *ngIf="carrito().length === 0">
            Tu carrito está vacío. Agrega productos del catálogo.
          </div>
          <div class="linea-carrito" *ngFor="let item of carrito()">
            <div class="mini-icono" [attr.data-cat]="item.producto.categoria">{{ item.producto.icono }}</div>
            <div class="linea-nombre">
              <b>{{ item.producto.nombre }}</b>
              <small>{{ item.producto.categoria }}</small>
            </div>
            <div class="cantidad">
              <button type="button" [disabled]="procesando() || compraPendiente()" (click)="cambiarCantidad(item.producto.id,-1)" aria-label="Reducir cantidad">−</button>
              <span>{{ item.cantidad }}</span>
              <button type="button" [disabled]="procesando() || compraPendiente()" (click)="cambiarCantidad(item.producto.id,1)" aria-label="Aumentar cantidad">+</button>
            </div>
            <b class="precio-linea">{{ item.producto.precio * item.cantidad | currency:'PEN':'S/ ':'1.2-2' }}</b>
            <button class="quitar" type="button" [disabled]="procesando() || compraPendiente()" (click)="quitar(item.producto.id)"
              [attr.aria-label]="'Quitar ' + item.producto.nombre">×</button>
          </div>

          <div class="resumen"><span>Subtotal:</span><b>{{ subtotal() | currency:'PEN':'S/ ':'1.2-2' }}</b></div>
          <div class="resumen"><span>Comisión:</span><b>S/ 0.00</b></div>
          <div class="total"><span>Total:</span><strong>{{ subtotal() | currency:'PEN':'S/ ':'1.2-2' }}</strong></div>

          <fieldset class="pago" [disabled]="procesando() || compraPendiente()">
            <legend>Método de pago (Aplicativo)</legend>
            <label *ngFor="let m of metodosPago" [class.metodo-activo]="metodoPago() === m">
              <input type="radio" name="metodoPago" [value]="m" [checked]="metodoPago() === m"
                (change)="metodoPago.set(m)" />
              <span class="logo-pago" [attr.data-metodo]="m">{{ nombreMetodoPago(m) }}</span>
            </label>
          </fieldset>

          <section class="informacion-pago">
            <b><span>i</span> Importante</b>
            <ul>
              <li>La orden y su pago son simulados; no se procesa dinero ni se entregan archivos.</li>
              <li>Yape, Plin y BiPay son opciones de demostración, no integraciones externas.</li>
              <li>El resultado de la solicitud se consulta en Mis Solicitudes.</li>
            </ul>
          </section>
          <button class="comprar" type="button" [disabled]="(carrito().length === 0 && !compraPendiente()) || procesando() || modoDemo() || carritoDemo()"
            (click)="continuarCompra()">
            {{ modoDemo() || carritoDemo() ? 'Compra deshabilitada en demostración' : procesando() ? 'Procesando solicitud...' : compraPendiente() ? 'Reintentar compra pendiente' : '♙ Pagar y confirmar solicitud' }}
          </button>
          <p class="estado-compra" *ngIf="mensajeCompra()">{{ mensajeCompra() }}</p>
          <p class="estado-tienda error" *ngIf="errorCompra()">{{ errorCompra() }}</p>
          <p class="seguro">Pago de demostración: no se procesa dinero real.</p>
        </aside>
      </div>
    </div>
  `,
  styles: [`
    .demo-notice{border:1px solid #dfbe79;background:#fff8e7;padding:16px;border-radius:8px;font-size:13px;margin-bottom:18px}.demo-notice p{margin:5px 0 10px}.demo-notice button{border:1px solid #b58a3e;background:white;border-radius:5px;padding:7px 12px;color:#745019;cursor:pointer}.disponibilidad{display:block;font-size:10px;color:#526d5b;margin:6px 0}

    :host{display:block;min-height:100vh;color:#142847;font-family:Inter,"Segoe UI",Arial,sans-serif}
    *{box-sizing:border-box}
    .tienda-shell{min-height:100vh;background:#f3f7fc}
    .topbar{height:58px;background:#062849;color:#fff;display:flex;align-items:center;gap:24px;padding:0 18px;box-shadow:0 2px 10px #112a4b24}
    .marca{width:228px;flex-shrink:0;display:flex;align-items:center;gap:12px;color:inherit;text-decoration:none}
    .marca-icono{font-size:39px;line-height:1;color:#e0b544}
    .marca-texto b,.marca-texto small{display:block}
    .marca-texto b{font-size:20px;line-height:1.1}
    .marca-texto small{font-size:10px;color:#d3dced;margin-top:3px}
    .busqueda-global{height:36px;max-width:460px;flex:1;display:flex;align-items:center;gap:10px;padding:0 14px;border-radius:22px;background:#fff;color:#283f60}
    .busqueda-global span,.busqueda-local span{font-size:24px;line-height:1}
    .busqueda-global input,.busqueda-local input{border:0;outline:0;width:100%;background:transparent;color:#283f60;font-size:11px}
    .acciones-top{margin-left:auto;display:flex;align-items:center;gap:12px;white-space:nowrap}
    .notificaciones{position:relative;border:0;background:none;color:#fff;font-size:22px;cursor:pointer}
    .notificaciones span{position:absolute;right:-4px;top:-4px;width:15px;height:15px;border-radius:50%;background:#e52f48;color:#fff;font-size:9px;display:grid;place-items:center}
    .avatar-usuario{width:34px;height:34px;border-radius:50%;background:#eef5ff;color:#1762bc;display:grid;place-items:center;font-weight:800}
    .usuario b,.usuario small{display:block}.usuario b{font-size:11px}.usuario small{font-size:9px;color:#d3dced;margin-top:2px}
    .cerrar-sesion{background:#fff;color:#ed352e;border:1px solid #e63b36;border-radius:5px;padding:8px 12px;font-size:11px;font-weight:700;cursor:pointer}
    .pagina{display:grid;grid-template-columns:158px minmax(0,1fr) 300px;gap:10px;max-width:1800px;margin:0 auto;align-items:start;padding-right:10px}
    .sidebar{width:auto;min-width:0;border-radius:0;box-shadow:none;position:sticky;top:0;min-height:calc(100vh - 58px);padding:10px 7px;background:#f8fbff;border-right:1px solid #e5edf6;display:flex;flex-direction:column}
    .menu{display:grid;gap:3px}
    .menu a,.categorias-menu button{min-height:32px;padding:7px 9px;display:flex;align-items:center;gap:11px;border:0;border-radius:5px;color:#183657;text-decoration:none;font-size:11px;text-align:left;background:transparent;cursor:pointer}
    .menu a span,.categorias-menu button span{width:17px;text-align:center;font-size:15px;color:#132f52}
    .menu a:hover,.categorias-menu button:hover,.categorias-menu button.seleccionada{background:#edf3fb}
    .menu a.activo{background:#ffdf9a;font-weight:750}
    .categorias-menu{border-top:1px solid #e6edf6;margin-top:14px;padding-top:12px}
    .categorias-menu h2{font-size:9px;margin:0 8px 8px;color:#263750}
    .categorias-menu button{width:100%;min-height:30px;padding:6px 8px;font-size:10px}
    .ayuda{margin-top:auto;display:grid;grid-template-columns:28px 1fr;align-items:center;column-gap:6px;padding:10px 7px;border:1px solid #e8edf5;border-radius:9px;background:#fff;font-size:9px}
    .ayuda-icono{grid-row:span 2;width:26px;height:26px;border-radius:50%;background:#e6ad2b;color:#fff;display:grid;place-items:center;font-size:16px;font-weight:bold}
    .ayuda small{color:#748198}
    .ayuda a{grid-column:1/-1;margin-top:8px;background:#06b95b;color:#fff;text-align:center;text-decoration:none;padding:8px;border-radius:6px;font-size:11px;font-weight:700}
    .contenido{min-width:0;padding:16px 3px 24px}
    .encabezado-tienda h1{font-size:18px;margin:0;color:#142a4a}
    .encabezado-tienda p{font-size:11px;color:#657592;margin:3px 0 10px}
    .pestanas{display:flex;gap:6px;overflow:auto;padding-bottom:8px}
    .pestanas button{flex:1;min-width:max-content;height:32px;padding:0 10px;border:1px solid #e0e8f2;border-radius:6px;background:#fff;color:#203a5a;font-size:10px;cursor:pointer}
    .pestanas button span{margin-right:6px;font-size:13px}
    .pestanas button.activa{background:#1263dd;color:white;border-color:#1263dd}
    .filtros{display:grid;grid-template-columns:minmax(130px,1.35fr) repeat(3,minmax(90px,1fr));gap:9px;margin:7px 0 12px}
    .busqueda-local{height:31px;align-self:end;border:1px solid #dce5f0;border-radius:6px;background:white;display:flex;align-items:center;gap:8px;padding:0 10px;color:#293f5e}
    .busqueda-local span{font-size:21px}
    .filtros>label:not(.busqueda-local){display:grid;gap:3px;color:#314563;font-size:9px}
    .filtros select{height:29px;min-width:0;padding:0 8px;border:1px solid #dce5f0;border-radius:6px;background:white;color:#263b59;font-size:10px}
    .estado-tienda,.estado-compra{margin:7px 0;padding:8px 10px;border-radius:6px;background:#edf4fb;color:#17375e;font-size:11px}
    .estado-tienda.error{background:#fff0f0;color:#9d2525}
    .catalogo{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}
    .producto{min-width:0;overflow:hidden;border:1px solid #e5eaf1;border-radius:7px;background:#fff;box-shadow:0 2px 7px #152d4c0b}
    .producto-imagen{height:74px;position:relative;display:grid;place-items:center;overflow:hidden;background:linear-gradient(135deg,#bfccd9,#657d96)}
    .producto-imagen:after{content:"";position:absolute;inset:8px 30% 6px 30%;border:1px solid #ffffff58;background:linear-gradient(110deg,#ffffff20,#0d294660);transform:rotate(-5deg);box-shadow:0 5px 12px #071b3530}
    .producto-imagen[data-cat*="Códigos"],.producto-imagen[data-cat*="ley"]{background:linear-gradient(135deg,#143e69,#061a37)}
    .producto-imagen[data-cat*="Libro"]{background:linear-gradient(135deg,#8b5836,#3f2417)}
    .producto-imagen[data-cat*="Contrato"],.producto-imagen[data-cat*="Modelo"]{background:linear-gradient(135deg,#c7baa1,#715f4c)}
    .producto-imagen[data-cat*="Capac"]{background:linear-gradient(135deg,#50678c,#172945)}
    .producto-etiqueta{position:absolute;z-index:1;top:5px;right:5px;padding:3px 6px;border-radius:3px;background:#1263d9;color:#fff;font-size:8px}
    .arte{position:relative;z-index:2;font-size:38px;filter:drop-shadow(0 3px 2px #0006)}
    .texto-arte{position:absolute;z-index:3;bottom:9px;width:40%;color:#fff;text-align:center;font-size:5px;font-weight:700;letter-spacing:.5px;text-shadow:0 1px 3px #000}
    .producto-info{padding:7px 8px}
    .producto-info h2{min-height:25px;margin:0 0 3px;color:#20334f;font-size:10px;line-height:1.25}
    .producto-info p{height:26px;overflow:hidden;margin:0 0 4px;color:#68768d;font-size:8px;line-height:1.35}
    .detalle-producto{display:grid;gap:2px;color:#68768d;font-size:8px}
    .precio{display:block;margin:3px 0;color:#0861db;font-size:12px}
    .agregar{width:100%;height:23px;border:0;border-radius:4px;background:#0964e8;color:white;font-size:9px;font-weight:700;cursor:pointer}
    .agregar:hover,.comprar:hover:not(:disabled){background:#064fb8}
    .agregar span{margin-right:4px}
    .vacio{grid-column:1/-1;padding:35px;text-align:center;border-radius:8px;background:#fff;color:#77849a;font-size:12px}
    .carrito-panel{position:sticky;top:8px;min-width:0;margin-top:10px;padding:12px 10px;border:1px solid #e6edf5;border-radius:8px;background:#fff;box-shadow:0 2px 8px #142f4d08}
    .carrito-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}
    .carrito-heading h2{margin:0;font-size:13px;color:#172a49}
    .contador{background:#d19012;color:#fff;border-radius:50%;min-width:20px;height:20px;display:grid;place-items:center;font-size:10px;font-weight:bold}
    .cabecera-carrito{display:grid;grid-template-columns:1fr 64px 55px;gap:3px;padding:7px 4px;background:#f3f6fb;color:#2d405d;font-size:8px;font-weight:700}
    .linea-carrito{position:relative;display:grid;grid-template-columns:35px minmax(0,1fr) 58px;gap:6px;align-items:center;padding:8px 3px;border-bottom:1px solid #edf1f6}
    .mini-icono{grid-row:span 2;width:33px;height:36px;border-radius:4px;display:grid;place-items:center;background:linear-gradient(145deg,#bdc8d6,#425d78);font-size:19px}
    .mini-icono[data-cat*="Libro"],.mini-icono[data-cat*="Códigos"]{background:linear-gradient(145deg,#214970,#071d38)}
    .linea-nombre{min-width:0;align-self:end}
    .linea-nombre b{display:block;overflow:hidden;color:#223753;font-size:8px;text-overflow:ellipsis;white-space:nowrap}
    .linea-nombre small{display:block;overflow:hidden;margin-top:2px;color:#718099;font-size:7px;text-overflow:ellipsis;white-space:nowrap}
    .cantidad{grid-column:3;grid-row:span 2;display:flex;align-items:center;justify-content:center;gap:4px}
    .cantidad button{width:17px;height:18px;padding:0;border:1px solid #dce5f0;border-radius:3px;background:#f7faff;color:#1360c6;cursor:pointer}
    .cantidad span{font-size:8px}
    .precio-linea{grid-column:2;align-self:start;color:#273b59;font-size:8px}
    .quitar{position:absolute;right:0;bottom:7px;border:0;background:transparent;color:#ed3737;font-size:16px;cursor:pointer}
    .carrito-vacio{padding:12px 4px;color:#77849a;font-size:9px}
    .resumen,.total{display:flex;justify-content:space-between;padding:5px 3px;color:#314563;font-size:10px}
    .resumen b{font-weight:500}
    .total{margin-top:2px;padding:8px 3px;border-radius:4px;background:#eef5ff;color:#0861db;font-size:13px;font-weight:700}
    .total strong{font-size:17px}
    .pago{display:grid;grid-template-columns:repeat(3,1fr);gap:5px;margin:9px 0 7px;padding:7px;border:1px solid #f15252;border-radius:5px}
    .pago legend{padding:0 3px;color:#182d4c;font-size:9px;font-weight:700}
    .pago label{display:flex;align-items:center;gap:3px;justify-content:center;min-height:35px;border:1px solid #e4eaf2;border-radius:5px;cursor:pointer}
    .pago label.metodo-activo{background:#f0f6ff;border-color:#80aef0}
    .pago input{width:10px;height:10px;margin:0;accent-color:#1263dd}
    .logo-pago{font-size:9px;font-weight:800;color:#18375e}
    .logo-pago[data-metodo="Yape"]{color:#802ab4}
    .logo-pago[data-metodo="Plin"]{color:#06aaba}
    .logo-pago[data-metodo="BiPay"]{color:#a46c00}
    .informacion-pago{padding:8px;border-radius:5px;background:#edf5ff;color:#344965}
    .informacion-pago>b{display:flex;align-items:center;gap:6px;color:#1263d9;font-size:10px}
    .informacion-pago>b span{width:16px;height:16px;border-radius:50%;background:#1263d9;color:#fff;text-align:center;line-height:16px}
    .informacion-pago ul{margin:5px 0 0;padding-left:16px;font-size:8px;line-height:1.45}
    .informacion-pago li{margin:2px 0}
    .comprar{width:100%;margin-top:7px;padding:9px 5px;border:0;border-radius:5px;background:#0864ed;color:#fff;font-size:10px;font-weight:750;cursor:pointer}
    .comprar:disabled{opacity:.5;cursor:not-allowed}
    .seguro{margin:7px 0 0;text-align:center;color:#37516f;font-size:8px}
    .topbar{background:#0b2035;height:auto;min-height:74px}.sidebar{background:#0b2035;color:#e0e9f3;border:0}.menu a,.menu a span,.categorias-menu button,.categorias-menu button span,.categorias-menu h2{color:#d9e4ee}.menu a.activo{background:linear-gradient(110deg,#b98733,#dcbf78);color:#102238}.menu a:hover,.categorias-menu button:hover,.categorias-menu button.seleccionada{background:#ffffff15}.contenido{padding:28px 15px}.encabezado-tienda h1{font-size:27px}.encabezado-tienda p{font-size:13px}.catalogo{grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.producto{border-radius:9px}.producto-imagen{height:150px}.producto-info{padding:14px}.producto-info h2{font-size:13px;min-height:34px}.producto-info p{font-size:11px;height:44px}.precio{font-size:17px;color:#14283f;margin:10px 0}.agregar{height:auto;min-height:36px;font-size:11px;background:linear-gradient(110deg,#c18b2d,#d7ae59);color:#13283d}.agregar:hover{background:#e1ba72}.comprar{background:#bd8829;font-size:12px;min-height:40px}.pestanas button.activa{background:#10283f;border-color:#10283f}.producto-etiqueta{background:#10283f}.carrito-panel{padding:18px 14px}.carrito-heading h2{font-size:15px}.linea-nombre b,.precio-linea{font-size:11px}.linea-nombre small{font-size:10px}.linea-carrito{padding:14px 3px}.resumen,.total{font-size:13px}.informacion-pago ul{font-size:10px}.seguro{font-size:10px}.pago legend{font-size:11px}.filtros>label:not(.busqueda-local){font-size:11px}.filtros select,.busqueda-local input{font-size:11px}.detalle-producto{font-size:10px}.cantidad button{width:23px;height:25px}.cantidad span{font-size:11px}.estado-tienda{font-size:12px}
    @media(min-width:1500px){.pagina{grid-template-columns:190px minmax(0,1fr) 350px}.producto-imagen{height:110px}.producto-info{padding:10px}.producto-info h2{font-size:12px}.producto-info p{font-size:9px}}
    @media(max-width:1050px){.pagina{grid-template-columns:145px minmax(0,1fr);padding:0 10px 0 0}.carrito-panel{position:static;grid-column:2;margin:0 0 18px}.catalogo{grid-template-columns:repeat(3,minmax(0,1fr))}.sidebar{grid-row:1/3}}
    @media(max-width:700px){.topbar{height:auto;min-height:58px;flex-wrap:wrap;padding:9px 12px;gap:8px}.marca{width:auto;flex:1}.marca-icono{font-size:31px}.marca-texto b{font-size:16px}.busqueda-global{order:3;flex-basis:100%;max-width:none}.acciones-top{gap:7px}.usuario,.notificaciones{display:none}.cerrar-sesion{padding:7px;font-size:9px}.pagina{display:block;padding:0}.sidebar{position:static;min-height:0;padding:7px;border-right:0;border-bottom:1px solid #e5edf6}.menu{display:flex;overflow:auto}.menu a{flex:0 0 auto;padding:7px;font-size:9px}.menu a span{width:auto}.categorias-menu,.ayuda{display:none}.contenido{padding:12px}.pestanas button{font-size:9px}.filtros{grid-template-columns:1fr 1fr}.busqueda-local{grid-column:1/-1}.catalogo{grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.producto-imagen{height:90px}.carrito-panel{margin:0 10px 16px}.cabecera-carrito{grid-template-columns:1fr 70px 55px}.linea-carrito{grid-template-columns:38px minmax(0,1fr) 75px}.cantidad button{width:21px;height:22px}}
    @media(max-width:380px){.acciones-top{gap:5px}.avatar-usuario{width:29px;height:29px}.catalogo{grid-template-columns:1fr}.producto-imagen{height:130px}}
  `]
})
export class TiendaJuridicaComponent implements OnInit {
  private readonly servicioApi: ServicioLegalService;
  private readonly http: HttpClient;
  protected readonly auth: AuthService;

  constructor(servicioApi: ServicioLegalService, http: HttpClient, auth: AuthService, private readonly route?: ActivatedRoute) {
    this.servicioApi = servicioApi;
    this.http = http;
    this.auth = auth;
  }

  readonly modoDemo = signal(false);
  readonly carritoDemo = computed(() => this.carrito().some(i => i.producto.id < 0));
  verDemo(): void {
    if (this.compraPendiente()) { this.errorCompra.set('Resuelve la compra pendiente antes de abrir la demostración.'); return; }
    this.modoDemo.set(true); this.productos.set(CATALOGO_DEMO); this.errorCatalogo.set(null);
  }
  busqueda = signal('');
  orden = signal('relevancia');
  tipoSeleccionado = signal('Todos');
  categoria = signal('Todas');
  metodoPago = signal('Yape');
  readonly metodosPago = environment.production
    ? ['Yape', 'Plin', 'BiPay']
    : ['Yape', 'Plin', 'BiPay', 'TARJETA_RECHAZADA'];
  readonly categorias = computed(() => [
    ...new Set(this.productos().map(producto => producto.categoria).filter(Boolean))
  ]);
  readonly productos = signal<Producto[]>([]);
  readonly cargando = signal(true);
  readonly errorCatalogo = signal<string | null>(null);
  readonly procesando = signal(false);
  readonly compraPendiente = signal(false);
  private readonly intentos = new CompraIntentoStore(sessionStorage);
  readonly mensajeCompra = signal<string | null>(null);
  readonly errorCompra = signal<string | null>(null);
  private readonly estadoOrden = signal<string | null>(null);

  readonly carrito = signal<ArticuloCarrito[]>(this.recuperarCarrito());
  readonly cantidad = computed(() => this.carrito().reduce((s, x) => s + x.cantidad, 0));
  readonly subtotal = computed(() => this.carrito().reduce((s, x) => s + x.producto.precio * x.cantidad, 0));

  readonly productosFiltrados = computed(() => {
    const q = this.busqueda().trim().toLocaleLowerCase();
    let lista = this.productos().filter(p =>
      (this.categoria() === 'Todas' || p.categoria === this.categoria()) &&
      (!q || `${p.nombre} ${p.categoria} ${p.descripcion}`.toLocaleLowerCase().includes(q))
    );
    if (this.tipoSeleccionado() === 'Asesoría') {
      lista = lista.filter(producto => /asesor|consult/i.test(producto.categoria));
    } else if (this.tipoSeleccionado() === 'Digital') {
      lista = lista.filter(producto => !/asesor|consult/i.test(producto.categoria));
    }
    if (this.orden() === 'menor') lista = [...lista].sort((a,b) => a.precio-b.precio);
    if (this.orden() === 'mayor') lista = [...lista].sort((a,b) => b.precio-a.precio);
    if (this.orden() === 'nombre') lista = [...lista].sort((a,b) => a.nombre.localeCompare(b.nombre));
    return lista;
  });

  ngOnInit(): void {
    if(this.route)this.busqueda.set(this.route.snapshot.queryParamMap.get('buscar')??'');
    try {
      const cliente = this.auth.idCliente();
      const pendiente = cliente ? this.intentos.actual(cliente) : null;
      this.compraPendiente.set(!!pendiente);
      if (pendiente) this.mensajeCompra.set('Hay una compra sin confirmar. Reintenta para recuperar el resultado con la misma clave.');
    } catch {
      this.errorCompra.set('No se pudo recuperar el intento anterior. Verifica tus solicitudes antes de continuar.');
    }
    this.cargarCatalogo();
  }

  cargarCatalogo(): void {
    this.cargando.set(true); this.errorCatalogo.set(null);
    this.servicioApi.listar().subscribe({
      next: servicios => {
        this.modoDemo.set(false);
        this.productos.set(servicios
          .filter((servicio): servicio is ServicioLegal & { id: number; precio: number } =>
            Number.isSafeInteger(servicio.id)
            && typeof servicio.precio === 'number'
            && Number.isFinite(servicio.precio)
            && servicio.precio >= 0
            && !/^(inactivo|inactiva)$/i.test(servicio.estado ?? ''))
          .map(servicio => this.aProducto(servicio)));
        this.cargando.set(false);
      },
      error: () => {
        this.errorCatalogo.set('No se pudo cargar el catálogo. Verifica el Gateway y el servicio legal.');
        this.cargando.set(false);
      }
    });
  }

  nombreUsuario(): string {
    return this.auth.currentUser()?.name ?? 'Visitante';
  }

  inicialesUsuario(): string {
    return this.nombreUsuario().split(/[ ._-]+/).filter(Boolean).slice(0, 2).map(parte => parte[0].toUpperCase()).join('');
  }

  private aProducto(servicio: ServicioLegal & { id: number; precio: number }): Producto {
    return {
      id: servicio.id,
      nombre: servicio.nombre,
      categoria: servicio.categoria || 'Servicios legales',
      descripcion: servicio.descripcion || 'Servicio profesional de Illari Legal.',
      precio: servicio.precio,
      icono: this.iconoCategoria(servicio.categoria || '')
    };
  }

  private recuperarCarrito(): ArticuloCarrito[] {
    const almacenado = sessionStorage.getItem(CARRITO_STORAGE_KEY);
    if (!almacenado) {
      return [];
    }
    try {
      const parsed: unknown = JSON.parse(almacenado);
      if (!Array.isArray(parsed) || !parsed.every(esArticuloCarrito)) {
        throw new Error('El contenido almacenado no tiene el formato de carrito esperado.');
      }
      return parsed;
    } catch (error) {
      console.error('No se pudo recuperar el carrito guardado.', error);
      sessionStorage.removeItem(CARRITO_STORAGE_KEY);
      return [];
    }
  }

  private actualizarCarrito(items: ArticuloCarrito[]): void {
    try {
      sessionStorage.setItem(CARRITO_STORAGE_KEY, JSON.stringify(items));
      this.carrito.set(items);
      window.dispatchEvent(new Event('illari-cart-updated'));
    } catch (error) {
      console.error('No se pudo guardar el carrito de compras.', error);
      this.errorCompra.set('No se pudo guardar el carrito en esta sesión.');
    }
  }

  seleccionarCategoria(cat: string) { this.categoria.set(cat || 'Todas'); }

  iconoCategoria(cat: string): string {
    const iconos: Record<string,string> = {
      'Asesorías':'⚖', 'Códigos y leyes':'§', 'Libros jurídicos':'▤',
      'Modelos de escritos':'▧', 'Contratos':'▣', 'Capacitaciones':'♧', 'Jurisprudencia':'⌕'
    };
    return iconos[cat] ?? '⚖';
  }

  etiquetaProducto(categoria: string): string {
    if (/asesor|consult/i.test(categoria)) return 'Asesoría';
    if (/código|ley/i.test(categoria)) return 'Código';
    if (/libro/i.test(categoria)) return 'Libro';
    if (/contrato/i.test(categoria)) return 'Contrato';
    if (/modelo|escrito/i.test(categoria)) return 'Modelo';
    if (/capacit/i.test(categoria)) return 'Capacitación';
    return 'Servicio';
  }

  tipoProducto(categoria: string): string {
    return /asesor|consult/i.test(categoria) ? 'Videollamada o presencial' : 'Libro digital (PDF)';
  }

  duracionProducto(categoria: string): string {
    return /asesor|consult/i.test(categoria) ? '30 minutos' : 'Edición digital';
  }

  nombreCorto(nombre: string): string {
    return nombre.length > 24 ? `${nombre.slice(0, 24)}...` : nombre;
  }

  nombreMetodoPago(metodo: string): string {
    if (metodo === 'TARJETA_RECHAZADA') return 'Rechazo de prueba (solo local)';
    return `${metodo} (simulado)`;
  }

  agregar(producto: Producto): void {
    if (this.procesando() || this.compraPendiente()) return;
    if (this.carrito().some(i => (i.producto.id < 0) !== (producto.id < 0))) {
      this.errorCompra.set('Vacía el carrito antes de cambiar entre productos reales y de demostración.'); return;
    }
    const items = this.carrito();
    const existente = items.find(item => item.producto.id === producto.id);
    this.actualizarCarrito(existente
      ? items.map(item => item.producto.id === producto.id ? {...item, cantidad:item.cantidad+1} : item)
      : [...items, {producto, cantidad:1}]);
  }

  cambiarCantidad(id: number, cambio: number): void {
    this.actualizarCarrito(this.carrito()
      .map(x => x.producto.id === id ? {...x, cantidad:x.cantidad+cambio} : x)
      .filter(x => x.cantidad > 0));
  }

  quitar(id: number): void {
    this.actualizarCarrito(this.carrito().filter(x => x.producto.id !== id));
  }

  continuarCompra(): void {
    if (this.modoDemo() || this.carritoDemo()) {
      this.errorCompra.set('Los productos de demostración no se pueden comprar.'); return;
    }

    this.errorCompra.set(null);
    this.mensajeCompra.set(null);
    if (!this.auth.autenticado()) {
      void this.auth.iniciarLogin('/dashboard').catch(() => {
        this.errorCompra.set('No se pudo iniciar sesión con Keycloak.');
      });
      return;
    }
    if ((this.carrito().length === 0 && !this.compraPendiente()) || this.procesando()) {
      return;
    }

    this.procesando.set(true);
    const request = {
      metodoPago: this.metodoPago(),
      detalles: this.carrito().map(item => ({ idServicio: item.producto.id, cantidad: item.cantidad }))
    };
    const cliente = this.auth.idCliente();
    let intento: IntentoCompra;
    let esReintento = false;
    try {
      if (!cliente) throw new Error('No se pudo identificar al cliente.');
      const previo = this.intentos.actual(cliente);
      esReintento = !!previo;
      intento = previo ?? this.intentos.preparar(cliente, request);
      this.compraPendiente.set(true);
    } catch (error) {
      this.procesando.set(false);
      this.errorCompra.set(error instanceof Error ? error.message : 'No se pudo guardar el intento de compra.');
      return;
    }
    this.http.post<OrdenLegal>(`${environment.apiBaseUrl}/v1/ordenes`, intento.solicitud,
      { headers: { 'Idempotency-Key': intento.clave, 'X-Trace-ID': intento.traceId! } }).subscribe({
      next: order => {
        try { this.intentos.completar(cliente!, intento.clave); }
        catch { this.errorCompra.set('La orden fue creada; no se pudo limpiar el intento local. Conserva su número antes de continuar.'); }
        this.compraPendiente.set(false);
        this.carrito.set([]);
        try {
          sessionStorage.removeItem(CARRITO_STORAGE_KEY);
          window.dispatchEvent(new Event('illari-cart-updated'));
        } catch (error) {
          console.error('La orden se creó, pero no se pudo limpiar el carrito de la sesión.', error);
        }
        this.estadoOrden.set(order.estado);
        this.mensajeCompra.set(`Solicitud #${order.id} enviada. Estado: ${order.estado}. Esperando confirmación del pago...`);
        this.esperarResultado(order.id);
      },
      error: (error: HttpErrorResponse) => {
        this.procesando.set(false);
        // A rejection on a later attempt cannot prove that the original request did not commit.
        if (!esReintento && [400, 403, 404, 422].includes(error.status)) {
          try { this.intentos.completar(cliente!, intento.clave); this.compraPendiente.set(false); } catch { /* preserve uncertain attempt */ }
        }
        this.errorCompra.set(this.mensajeError(error, 'No se pudo registrar la solicitud.'));
      }
    });
  }

  private esperarResultado(id: number): void {
    timer(800, 1000).pipe(
      switchMap(() => this.http.get<OrdenLegal>(`${environment.apiBaseUrl}/v1/ordenes/${id}`)),
      takeWhile(order => ['PENDIENTE_PAGO', 'CANCELACION_PENDIENTE'].includes(order.estado), true),
      take(15)
    ).subscribe({
      next: order => {
        this.estadoOrden.set(order.estado);
        if (!['PENDIENTE_PAGO', 'CANCELACION_PENDIENTE'].includes(order.estado)) {
          this.mensajeCompra.set(order.estado === 'PAGADA'
            ? `Solicitud #${order.id} pagada y confirmada.`
            : `Solicitud #${order.id} cancelada: el pago fue rechazado.`);
        }
      },
      error: (error: HttpErrorResponse) => {
        this.procesando.set(false);
        this.errorCompra.set(this.mensajeError(error, 'La solicitud se creó, pero no se pudo consultar el resultado del pago.'));
      },
      complete: () => {
        this.procesando.set(false);
        if (['PENDIENTE_PAGO', 'CANCELACION_PENDIENTE'].includes(this.estadoOrden() ?? '')) {
          this.mensajeCompra.set('La solicitud sigue pendiente. Consulta su estado en Mis Solicitudes.');
        }
      }
    });
  }

  private mensajeError(error: HttpErrorResponse, fallback: string): string {
    const detail: unknown = error.error?.detail ?? error.error?.message;
    if (typeof detail === 'string' && detail.length > 0) {
      return detail;
    }
    if (typeof error.error === 'string' && error.error.length > 0) {
      return error.error;
    }
    return `${fallback} HTTP ${error.status || 'sin respuesta'}.`;
  }
}

interface OrdenLegal {
  id: number;
  estado: string;
  total: number;
}
