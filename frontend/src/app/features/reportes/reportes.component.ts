import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';

import { DashboardService } from '../../core/services/dashboard.service';
import { CasoService } from '../../core/services/caso.service';
import { DashboardStats, Caso } from '../../core/models';

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.Default,
  template: `
    <div class="page-header">
      <div>
        <h1 class="page-title">Reportes</h1>
        <p class="page-subtitle">Consulta métricas y reportes de gestión.</p>
      </div>
    </div>
    <div class="stats-grid">
      @if (stats) {
        <div class="stat-card card">
          <div class="card-body">
            <p class="summary-label">Total casos</p>
            <p class="summary-value">{{ stats.totalCasos }}</p>
          </div>
        </div>
      }
      @if (stats) {
        <div class="stat-card card">
          <div class="card-body">
            <p class="summary-label">Clientes</p>
            <p class="summary-value">{{ stats.totalClientes }}</p>
          </div>
        </div>
      }
      @if (stats) {
        <div class="stat-card card">
          <div class="card-body">
            <p class="summary-label">Abogados</p>
            <p class="summary-value">{{ stats.totalAbogados }}</p>
          </div>
        </div>
      }
      @if (stats) {
        <div class="stat-card card">
          <div class="card-body">
            <p class="summary-label">Casos activos</p>
            <p class="summary-value">{{ stats.casosActivos }}</p>
          </div>
        </div>
      }
    </div>
    
    <div class="card" style="margin-top:1rem">
      <div class="card-header"><h2>Casos recientes</h2></div>
      <div class="card-body">
        <table class="data-table">
          <thead><tr><th>#</th><th>Nombre</th><th>Cliente</th><th>Abogado</th></tr></thead>
          <tbody>
            @for (c of recentCasos; track c) {
              <tr>
                <td>{{ c.id }}</td>
                <td>{{ c.nombreCaso }}</td>
                <td>{{ c.clienteNombre }}</td>
                <td>{{ c.abogadoNombre }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
    `
})
export class ReportesComponent implements OnInit {
  stats: DashboardStats | null = null;
  recentCasos: Caso[] = [];
  loading = false;

  constructor(private dashboardService: DashboardService, private casoService: CasoService) {}

  ngOnInit(): void {
    this.loading = true;
    this.dashboardService.stats().subscribe({ next: s => { this.stats = s; this.loading = false; }, error: () => this.loading = false });
    this.casoService.list().subscribe(c => this.recentCasos = (c || []).slice(0,6));
  }
}
