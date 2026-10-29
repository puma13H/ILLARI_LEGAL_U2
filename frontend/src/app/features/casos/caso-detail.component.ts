import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CasoService } from '../../core/services/caso.service';
import { MensajeService } from '../../core/services/mensaje.service';
import { DocumentoService } from '../../core/services/documento.service';
import { AuthService } from '../../core/auth/auth-service';
import { Caso, Documento, Mensaje } from '../../core/models';

@Component({
  selector: 'app-caso-detail',
  standalone: true,
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.Default,
  template: `
    <div class="page-header">
      <div>
        <a routerLink="/casos" class="btn-link">← Volver a casos</a>
        @if (caso) {
          <h1 class="page-title" style="margin-top:.5rem">{{ caso.nombreCaso }}</h1>
          <p class="page-subtitle">Caso #{{ caso.id }}</p>
        }
      </div>
    </div>
    
    @if (caso) {
      <div class="card" style="margin-bottom:1.5rem">
        <div class="card-body">
          <div class="detail-grid">
            <div class="detail-item"><label>Cliente</label><span>{{ caso.clienteNombre }}</span></div>
            <div class="detail-item"><label>Abogado</label><span>{{ caso.abogadoNombre }}</span></div>
            <div class="detail-item"><label>Estado</label>
            @if (canEditState()) {
              <select class="input" [(ngModel)]="caso.estado" name="estado">
                <option value="En trámite">En trámite</option>
                <option value="Finalizado">Finalizado</option>
                <option value="Pendiente">Pendiente</option>
                <option value="Cancelado">Cancelado</option>
              </select>
            } @else {
              <span class="badge badge-primary">{{ caso.estado }}</span>
            }
          </div>
          <div class="detail-item"><label>Apertura</label><span>{{ caso.fechaApertura }}</span></div>
          @if (caso.tarifa) {
            <div class="detail-item"><label>Tarifa</label><span>S/ {{ caso.tarifa }}</span></div>
          }
        </div>
        @if (caso.descripcion) {
          <p style="margin:1rem 0 0;color:var(--muted)">{{ caso.descripcion }}</p>
        }
        @if (canEditState()) {
          <div style="margin-top:1rem; display:flex; gap:.75rem; align-items:center;">
            <button type="button" class="btn btn-primary" (click)="saveCase()">Guardar cambios</button>
            <span style="color:var(--muted)">Cambio de estado permitido para abogado y admin.</span>
          </div>
        }
      </div>
    </div>
    <div class="card" style="margin-bottom:1.5rem">
      <div class="card-header"><h2>Mensajes</h2></div>
      <div class="card-body">
        <div class="msg-list">
          @for (m of mensajes; track m) {
            <div class="msg-item">
              <strong>{{ m.userName }}</strong>
              <p>{{ m.contenido }}</p>
            </div>
          }
          @if (!mensajes.length) {
            <p class="empty-state" style="padding:1rem">Sin mensajes aún</p>
          }
        </div>
        <form (ngSubmit)="enviarMensaje()" style="margin-top:1rem">
          <textarea class="input" [(ngModel)]="nuevoMensaje" name="mensaje" rows="3" placeholder="Escribe un mensaje..."></textarea>
          <button type="submit" class="btn btn-primary" style="margin-top:.5rem">Enviar mensaje</button>
        </form>
      </div>
    </div>
    <div class="card">
      <div class="card-header"><h2>Documentos</h2></div>
      <div class="card-body">
        @if (documentos.length) {
          <table class="data-table">
            <thead><tr><th>Nombre</th><th>Tipo</th><th></th></tr></thead>
            <tbody>
              @for (d of documentos; track d) {
                <tr>
                  <td>{{ d.nombre }}</td>
                  <td><span class="badge badge-warning">{{ d.tipoArchivo }}</span></td>
                  <td><button type="button" class="btn-link" (click)="descargar(d.id)">Descargar</button></td>
                </tr>
              }
            </tbody>
          </table>
        } @else {
          <p class="empty-state" style="padding:1rem">Sin documentos</p>
        }
        <form (ngSubmit)="subirDocumento()" style="margin-top:1.5rem;display:grid;gap:.75rem;max-width:480px">
          <div class="form-group">
            <label>Nombre del documento</label>
            <input class="input" type="text" [(ngModel)]="docNombre" name="docNombre" required />
          </div>
          <div class="form-group">
            <label>Archivo</label>
            <input class="input" type="file" (change)="onFile($event)" required />
          </div>
          <button type="submit" class="btn btn-primary" [disabled]="uploadingDoc">
            {{ uploadingDoc ? 'Subiendo...' : 'Subir documento' }}
          </button>
          @if (uploadError) {
            <div class="alert alert-error" style="margin-top:.5rem">
              {{ uploadError }}
            </div>
          }
          @if (uploadSuccess) {
            <div class="alert alert-success" style="margin-top:.5rem">
              {{ uploadSuccess }}
            </div>
          }
        </form>
      </div>
    </div>
    } @else {
      <div class="empty-state">Caso no encontrado</div>
    }
    `
})
export class CasoDetailComponent implements OnInit {
  caso: Caso | null = null;
  mensajes: Mensaje[] = [];
  documentos: Documento[] = [];
  nuevoMensaje = '';
  docNombre = '';
  selectedFile: File | null = null;
  uploadingDoc = false;
  uploadError = '';
  uploadSuccess = '';
  private casoId = 0;

  constructor(
    private route: ActivatedRoute,
    private casoService: CasoService,
    private mensajeService: MensajeService,
    private documentoService: DocumentoService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.casoId = Number(this.route.snapshot.paramMap.get('id'));
    this.casoService.get(this.casoId).subscribe(c => this.caso = c);
    this.loadMensajes();
    this.loadDocumentos();
  }

  loadMensajes(): void {
    this.mensajeService.list(this.casoId).subscribe(m => this.mensajes = m);
  }

  loadDocumentos(): void {
    this.documentoService.list(this.casoId).subscribe(d => this.documentos = d);
  }

  canEditState(): boolean {
    return this.auth.hasRole('admin') || this.auth.hasRole('abogado');
  }

  saveCase(): void {
    if (!this.caso) return;
    this.casoService.update(this.caso.id, {
      clienteId: this.caso.clienteId,
      nombreCaso: this.caso.nombreCaso,
      estado: this.caso.estado,
      fechaApertura: this.caso.fechaApertura,
      descripcion: this.caso.descripcion,
      tarifa: this.caso.tarifa,
      fechaAudiencia: this.caso.fechaAudiencia
    }).subscribe(c => this.caso = c);
  }

  enviarMensaje(): void {
    if (!this.nuevoMensaje.trim()) return;
    this.mensajeService.send(this.casoId, this.nuevoMensaje).subscribe(() => {
      this.nuevoMensaje = '';
      this.loadMensajes();
    });
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
    this.uploadError = '';
    this.uploadSuccess = '';
  }

  subirDocumento(): void {
    if (!this.selectedFile || !this.docNombre.trim()) {
      this.uploadError = 'Debes seleccionar un archivo y proporcionar un nombre';
      return;
    }
    
    this.uploadingDoc = true;
    this.uploadError = '';
    this.uploadSuccess = '';
    
    this.documentoService.upload(this.casoId, this.docNombre, this.selectedFile).subscribe({
      next: () => {
        this.uploadingDoc = false;
        this.uploadSuccess = 'Documento subido exitosamente';
        this.docNombre = '';
        this.selectedFile = null;
        // Limpiar mensajes después de 3 segundos
        setTimeout(() => this.uploadSuccess = '', 3000);
        this.loadDocumentos();
      },
      error: (err) => {
        this.uploadingDoc = false;
        console.error('Error al subir documento:', err);
        if (err.error && typeof err.error === 'string') {
          this.uploadError = err.error;
        } else if (err.error?.message) {
          this.uploadError = err.error.message;
        } else if (err.status === 401) {
          this.uploadError = 'No autenticado. Por favor inicia sesión nuevamente.';
        } else if (err.status === 403) {
          this.uploadError = 'No tienes permiso para subir documentos en este caso.';
        } else if (err.status === 413) {
          this.uploadError = 'El archivo es demasiado grande. Máximo 5MB.';
        } else {
          this.uploadError = 'Error al subir el documento: ' + (err.statusText || 'Error desconocido');
        }
      }
    });
  }

  descargar(id: number): void {
    this.documentoService.download(id).subscribe({
      next: (res) => {
        const blob = res.body!;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        
        // Extraer nombre del archivo del header Content-Disposition
        let filename = `documento-${id}`;
        const disposition = res.headers.get('content-disposition');
        
        console.log('Content-Disposition header:', disposition);
        
        if (disposition) {
          // Intentar extraer del formato: attachment; filename="nombre.ext"
          let filenameMatch = disposition.match(/filename="([^"]+)"/);
          if (filenameMatch && filenameMatch[1]) {
            filename = filenameMatch[1];
          } else {
            // Intentar sin comillas: attachment; filename=nombre.ext
            filenameMatch = disposition.match(/filename=([^;\s]+)/);
            if (filenameMatch && filenameMatch[1]) {
              filename = filenameMatch[1];
            }
          }
        }
        
        console.log('Filename para descargar:', filename);
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        console.error('Error al descargar documento:', err);
        alert('No se pudo descargar el documento');
      }
    });
  }
}
