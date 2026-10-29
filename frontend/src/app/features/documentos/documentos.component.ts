import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';

import { CasoService } from '../../core/services/caso.service';
import { DocumentoService } from '../../core/services/documento.service';
import { AuthService } from '../../core/auth/auth-service';
import { Caso, Documento } from '../../core/models';

@Component({
  selector: 'app-documentos',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.Default,
  template: `
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ auth.hasRole('cliente') ? 'Mis Documentos' : 'Documentos' }}</h1>
        <p class="page-subtitle">{{ auth.hasRole('cliente') ? 'Visualiza y descarga tus archivos de caso' : 'Administra archivos y documentos de los casos.' }}</p>
      </div>
    </div>
    <div class="card">
      <div class="card-header"><h2>Subir documento</h2></div>
      <div class="card-body">
        <div class="form-group">
          <label>Seleccionar caso</label>
          <select class="input" (change)="selectCaso($any($event.target).value ? +$any($event.target).value : null)">
            <option value="">-- Selecciona un caso --</option>
            @for (c of casos; track c) {
              <option [value]="c.id">#{{c.id}} - {{ c.nombreCaso }}</option>
            }
          </select>
        </div>
    
        @if (selectedCasoId) {
          <div>
            <label>Archivo</label>
            <input type="file" (change)="onFileChange($event)" />
            @if (uploading) {
              <div>Subiendo...</div>
            }
          </div>
        }
    
        @if (!selectedCasoId) {
          <div style="margin-top:1rem;color:var(--muted)">Selecciona un caso para ver o subir documentos.</div>
        }
      </div>
    </div>
    
    <div class="card" style="margin-top:1rem">
      <div class="card-header"><h2>Documentos</h2></div>
      <div class="card-body">
        @if (!documentos.length) {
          <div>No hay documentos para el caso seleccionado.</div>
        }
        @if (documentos.length) {
          <table class="data-table">
            <thead><tr><th>Nombre</th><th>Tipo</th><th>Tamaño</th><th></th></tr></thead>
            <tbody>
              @for (d of documentos; track d) {
                <tr>
                  <td>{{ d.nombre }}</td>
                  <td>{{ d.tipoArchivo }}</td>
                  <td>{{ d.tamanoArchivo }} bytes</td>
                  <td><button class="btn btn-secondary" (click)="download(d)">Descargar</button></td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    </div>
    `
})
export class DocumentosComponent implements OnInit {
  casos: Caso[] = [];
  selectedCasoId: number | null = null;
  documentos: Documento[] = [];
  uploading = false;
  fileName = '';

  constructor(private casoService: CasoService, private documentoService: DocumentoService, public auth: AuthService) {}

  ngOnInit(): void {
    this.casoService.list().subscribe(c => this.casos = c);
  }

  selectCaso(id: number | null): void {
    this.selectedCasoId = id;
    this.documentos = [];
    if (id) this.loadDocs(id);
  }

  loadDocs(casoId: number): void {
    this.documentoService.list(casoId).subscribe({ next: d => this.documentos = d, error: () => this.documentos = [] });
  }

  onFileChange(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file || !this.selectedCasoId) return;
    this.uploading = true;
    this.documentoService.upload(this.selectedCasoId, file.name, file).subscribe({
      next: () => { this.uploading = false; this.loadDocs(this.selectedCasoId!); },
      error: () => { this.uploading = false; }
    });
  }

  download(doc: Documento): void {
    this.documentoService.download(doc.id).subscribe({ next: resp => {
      const blob = resp.body as Blob;
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = doc.nombre;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    }});
  }
}
