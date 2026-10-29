import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef, inject } from '@angular/core';

import { RouterLink } from '@angular/router';
import { CasoService } from '../../core/services/caso.service';
import { AuthService } from '../../core/auth/auth-service';
import { Caso } from '../../core/models';

@Component({
  selector: 'app-casos-list',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.Default,
  template: `
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ auth.hasRole('cliente') ? 'Mis Casos' : 'Casos' }}</h1>
        <p class="page-subtitle">{{ auth.hasRole('cliente') ? 'Lista de casos asignados a tu cuenta' : 'Listado de casos legales' }}</p>
      </div>
      @if (auth.hasRole('abogado') || auth.hasRole('admin')) {
        <div>
          <a routerLink="/casos/nuevo" class="btn btn-primary">Nuevo caso</a>
        </div>
      }
    </div>
    
    <div class="card">
      <div class="table-wrap">
        @if (error) {
          <div class="alert alert-error" role="alert">{{ error }} <button type="button" class="btn btn-primary" (click)="cargar()">Reintentar</button></div>
        } @else if (loading) {
          <div class="empty-state">Cargando casos...</div>
        } @else {
          @if (casos.length) {
            <table class="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Nombre del caso</th>
                  <th>Cliente</th>
                  <th>Abogado</th>
                  <th>Estado</th>
                  <th>Apertura</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (caso of casos; track caso) {
                  <tr>
                    <td>#{{ caso.id }}</td>
                    <td><strong>{{ caso.nombreCaso }}</strong></td>
                    <td>{{ caso.clienteNombre }}</td>
                    <td>{{ caso.abogadoNombre }}</td>
                    <td><span class="badge badge-primary">{{ caso.estado }}</span></td>
                    <td>{{ caso.fechaApertura }}</td>
                    <td><a [routerLink]="['/casos', caso.id]" class="btn-link">Ver detalle</a></td>
                  </tr>
                }
              </tbody>
            </table>
          } @else {
            <div class="empty-state">No hay casos registrados</div>
          }
        }
      </div>
    </div>
    `
})
export class CasosListComponent implements OnInit {
  casos: Caso[] = [];
  loading = false;
  error: string | null = null;
  private readonly cdr = inject(ChangeDetectorRef);

  constructor(
    private casoService: CasoService,
    public auth: AuthService
  ) {}

  ngOnInit(): void { this.cargar(); }

  cargar(): void {
    this.error = null;
    this.loading = true;
    this.casoService.list().subscribe({
      next: data => { this.casos = data; this.loading = false; this.cdr.markForCheck(); },
      error: () => { this.loading = false; this.error = 'No se pudieron cargar los casos. Inténtalo de nuevo.'; this.cdr.markForCheck(); }
    });
  }
}
