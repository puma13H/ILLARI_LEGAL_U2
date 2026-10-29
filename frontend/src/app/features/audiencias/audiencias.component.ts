import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CasoService } from '../../core/services/caso.service';
import { Caso } from '../../core/models';

@Component({
  selector: 'app-audiencias',
  standalone: true,
  imports: [CommonModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.Default,
  template: `
    <div class="page-header">
      <div>
        <h1 class="page-title">Audiencias</h1>
        <p class="page-subtitle">Gestiona las próximas audiencias y eventos judiciales.</p>
      </div>
    </div>
    <div class="card">
      <div class="card-header"><h2>Agenda de audiencias</h2></div>
      <div class="card-body">
        @if (loading) {
          <div>Cargando audiencias...</div>
        }
        @if (error) {
          <div class="alert alert-error">{{ error }}</div>
        }
        @if (!loading && audiencias.length === 0) {
          <div>No hay audiencias programadas.</div>
        }
    
        @if (audiencias.length) {
          <div class="agenda-list">
            @for (a of audiencias; track a) {
              <div class="agenda-item">
                <div class="time">{{ a.fechaAudiencia | date:'EEE dd/MM HH:mm' }}</div>
                <div class="info">
                  <div class="case-title">{{ a.nombreCaso }}</div>
                  <div class="case-meta">Cliente: {{ a.clienteNombre }} — Abogado: {{ a.abogadoNombre }}</div>
                </div>
                <div class="actions">
                  <a [routerLink]="['/casos', a.id]" class="btn btn-secondary">Ver caso</a>
                </div>
              </div>
            }
          </div>
        }
      </div>
    </div>
    `
})
export class AudienciasComponent implements OnInit {
  audiencias: Caso[] = [];
  loading = false;
  error = '';

  constructor(private casoService: CasoService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.casoService.list().subscribe({
      next: (c) => {
        // filter cases that have a fechaAudiencia set and sort ascending
        this.audiencias = (c || [])
          .filter(x => !!x.fechaAudiencia)
          .sort((a, b) => new Date(a.fechaAudiencia!).getTime() - new Date(b.fechaAudiencia!).getTime());
        this.loading = false;
      },
      error: (e) => { this.error = 'No se pudieron cargar audiencias'; this.loading = false; }
    });
  }
}
