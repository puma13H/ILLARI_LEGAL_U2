import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DashboardService } from '../../core/services/dashboard.service';
import { CasoService } from '../../core/services/caso.service';
import { DocumentoService } from '../../core/services/documento.service';
import { AuthService } from '../../core/auth/auth-service';
import { forkJoin } from 'rxjs';
import { Caso, DashboardStats } from '../../core/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.Default,
  template: `
    <div>
      @if (loadError) { <p class="alert alert-error" role="alert">{{ loadError }}</p> }
      @switch (role) {
        @case ('admin') {
          <div class="dashboard-view admin-view">
            <div class="dashboard-hero">
              <div>
                <div style="margin-bottom:.75rem; display:flex; align-items:center; gap:1rem;">
                  <div class="admin-badge"><span class="crown">👑</span> Administrador</div>
                  <div style="color:var(--muted-light);">Vista completa del sistema</div>
                </div>
                <p class="eyebrow">Hola, {{ auth.currentUser()?.name }} 👋</p>
                <h1 class="page-title">Panel Administrativo</h1>
                <p class="page-subtitle">Administra casos, clientes y abogados desde un solo panel.</p>
              </div>
              <div class="hero-actions">
                <div class="search-card">
                  <p>Datos de los recursos autorizados en esta sesión.</p>
                </div>
                <div class="hero-meta">
                  <div>
                    <span class="meta-label">Hoy</span>
                    <strong>{{ today | date:'d MMM yyyy' }}</strong>
                  </div>
                  <div>
                    <span class="meta-label">Rol</span>
                    <strong>Administrador</strong>
                  </div>
                </div>
              </div>
            </div>
            <div class="summary-grid">
              <div class="summary-card">
                <div>
                  <p class="summary-label">Total casos</p>
                  <p class="summary-value">{{ stats?.totalCasos ?? 0 }}</p>
                </div>
                <span class="summary-icon">📁</span>
              </div>
              <div class="summary-card">
                <div>
                  <p class="summary-label">Casos activos</p>
                  <p class="summary-value">{{ stats?.casosActivos ?? 0 }}</p>
                </div>
                <span class="summary-icon">✓</span>
              </div>
              <div class="summary-card">
                <div>
                  <p class="summary-label">Clientes</p>
                  <p class="summary-value">{{ stats?.totalClientes ?? 0 }}</p>
                </div>
                <span class="summary-icon">👥</span>
              </div>
              <div class="summary-card">
                <div>
                  <p class="summary-label">Abogados</p>
                  <p class="summary-value">{{ stats?.totalAbogados ?? 0 }}</p>
                </div>
                <span class="summary-icon">⚖️</span>
              </div>
            </div>
            <div class="dashboard-grid">
              <div class="dashboard-column">
                <div class="card">
                  <div class="card-header">
                    <h2>Visión del sistema</h2>
                  </div>
                  <div class="card-body">
                    <p>Resumen completo de usuarios, casos y recursos del sistema.</p>
                  </div>
                </div>
                <div class="card">
                  <div class="card-header">
                    <h2>Casos registrados</h2>
                  </div>
                  <div class="card-body activity-list">
                    @for (caso of casos.slice(0, 3); track caso.id) {
                      <div class="activity-item"><div><strong>{{ caso.nombreCaso }}</strong><p>{{ caso.estado }}</p></div><span>{{ caso.fechaApertura }}</span></div>
                    } @empty { <p>Sin casos registrados.</p> }
                  </div>
                </div>
              </div>
              <div class="dashboard-column dashboard-right">
                <div class="card quick-actions-card">
                  <div class="card-header">
                    <h2>Accesos administrativos</h2>
                  </div>
                  <div class="card-body quick-actions">
                    <a routerLink="/clientes" class="quick-action">
                      <span>Gestionar clientes</span>
                      <span>➡</span>
                    </a>
                    <a routerLink="/abogados" class="quick-action">
                      <span>Gestionar abogados</span>
                      <span>➡</span>
                    </a>
                    <a routerLink="/usuarios" class="quick-action">
                      <span>Gestionar usuarios</span>
                      <span>➡</span>
                    </a>
                    <a routerLink="/configuracion" class="quick-action">
                      <span>Mi configuración</span>
                      <span>➡</span>
                    </a>
                  </div>
                </div>
              </div>
            </div>
          </div>
        }
        @case ('abogado') {
          <div class="dashboard-view abogado-view abogado-new">
            <div class="abogado-top">
              <div class="abogado-badge">⚖️ <span>ABOGADO</span></div>
              <div class="abogado-sub">Gestión de casos y clientes asignados</div>
            </div>
            <div class="abogado-hero">
              <div class="welcome-card">
                <div class="welcome-left">
                  <h3>Bienvenido, {{ auth.currentUser()?.name }}</h3>
                  <p class="muted">Gestión jurídica</p>
                </div>
                <div class="welcome-meta">
                  <div class="small-card">
                    <div class="label">Mis Casos Activos</div>
                    <div class="value">{{ myCasosCount }}</div>
                  </div>
                  <div class="small-card">
                    <div class="label">Próximas audiencias</div>
                    <div class="value">{{ upcomingAudienciasCount }}</div>
                  </div>
                  <div class="small-card">
                    <div class="label">Documentos</div>
                    <div class="value">{{ documentosCount }}</div>
                  </div>
                </div>
              </div>
              <div class="stats-row">
                <div class="stat"> <div class="stat-label">Mis casos</div> <div class="stat-value">{{ myCasosCount }}</div> </div>
                <div class="stat"> <div class="stat-label">Audiencias</div> <div class="stat-value">{{ upcomingAudienciasCount }}</div> </div>
                <div class="stat"> <div class="stat-label">Clientes</div> <div class="stat-value">{{ stats?.totalClientes ?? 0 }}</div> </div>
                <div class="stat"> <div class="stat-label">Documentos</div> <div class="stat-value">{{ documentosCount }}</div> </div>
              </div>
            </div>
            <div class="abogado-main">
              <div class="left-col">
                <div class="card cases-card">
                  <div class="card-header"><h2>Mis casos recientes</h2></div>
                  <div class="card-body cases-list">
                    @if (casos.length === 0) {
                      <div class="empty">No tienes casos recientes.</div>
                    }
                    @for (c of casos | slice:0:5; track c) {
                      <div class="case-item">
                        <div class="case-title">{{ c.nombreCaso }}</div>
                        <div class="case-meta">Cliente: {{ c.clienteNombre || '-' }} · {{ c.estado }}</div>
                        <div class="case-actions"><a routerLink="/casos/{{c.id}}">Ver</a></div>
                      </div>
                    }
                  </div>
                </div>
                <div class="card agenda-card">
                  <div class="card-header"><h2>Agenda de audiencias</h2></div>
                  <div class="card-body">
                    @if (upcomingAudienciasCount === 0) {
                      <div>No hay próximas audiencias.</div>
                    }
                    <!-- simplified list -->
                    @for (c of proximasAudiencias | slice:0:5; track c.id) {
                      <div class="agenda-item">
                        <div class="time">{{ c.fechaAudiencia ? (c.fechaAudiencia | date:'HH:mm') : '—' }}</div>
                        <div class="agenda-desc">{{ c.nombreCaso }} · {{ c.clienteNombre || '-' }}</div>
                      </div>
                    }
                  </div>
                </div>
              </div>
              <div class="right-col">
                <div class="card quick-actions-card">
                  <div class="card-header"><h2>Acciones rápidas</h2></div>
                  <div class="card-body quick-actions">
                    <a routerLink="/casos/nuevo" class="quick-action">Nuevo Caso</a>
                    <a routerLink="/clientes" class="quick-action">Nuevo Cliente</a>
                    <a routerLink="/documentos" class="quick-action">Subir Documento</a>
                    <a routerLink="/tareas" class="quick-action">Ver tareas</a>
                  </div>
                </div>
              </div>
            </div>
          </div>
        }
        @case ('cliente') {
          <div class="dashboard-view cliente-view">
            <div class="dashboard-hero">
              <div>
                <div style="margin-bottom:.75rem; display:flex; align-items:center; gap:1rem;">
                  <div class="admin-badge"><span class="crown">👥</span> Cliente</div>
                  <div style="color:var(--muted-light);">Consulta tus casos y documentos</div>
                </div>
                <p class="eyebrow">Hola, {{ auth.currentUser()?.name }} 👋</p>
                <h1 class="page-title">Panel de Cliente</h1>
                <p class="page-subtitle">Visualiza el estado de tus casos y documentos.</p>
              </div>
              <div class="hero-actions">
                <div class="search-card">
                  <p>Datos de los recursos autorizados en esta sesión.</p>
                </div>
                <div class="hero-meta">
                  <div>
                    <span class="meta-label">Hoy</span>
                    <strong>{{ today | date:'d MMM yyyy' }}</strong>
                  </div>
                  <div>
                    <span class="meta-label">Rol</span>
                    <strong>Cliente</strong>
                  </div>
                </div>
              </div>
            </div>
            <div class="summary-grid">
              <div class="summary-card">
                <div>
                  <p class="summary-label">Mis casos</p>
                  <p class="summary-value">{{ myCasosCount }}</p>
                </div>
                <span class="summary-icon">📂</span>
              </div>
              <div class="summary-card">
                <div>
                  <p class="summary-label">Documentos</p>
                  <p class="summary-value">{{ documentosCount }}</p>
                </div>
                <span class="summary-icon">📎</span>
              </div>
              <div class="summary-card">
                <div>
                  <p class="summary-label">Casos abiertos</p>
                  <p class="summary-value">{{ countByState('En trámite') }}</p>
                </div>
                <span class="summary-icon">🔔</span>
              </div>
              <div class="summary-card">
                <div>
                  <p class="summary-label">Casos finalizados</p>
                  <p class="summary-value">{{ countByState('Finalizado') }}</p>
                </div>
                <span class="summary-icon">🏁</span>
              </div>
            </div>
            <div class="dashboard-grid">
              <div class="dashboard-column">
                <div class="card">
                  <div class="card-header">
                    <h2>Mi relación con el caso</h2>
                  </div>
                  <div class="card-body">
                    <p>Tienes {{ myCasosCount }} casos en el sistema.</p>
                  </div>
                </div>
                <div class="card">
                  <div class="card-header">
                    <h2>Documentos</h2>
                  </div>
                  <div class="card-body">
                    <p>Hay {{ documentosCount }} documentos vinculados a tus casos.</p>
                  </div>
                </div>
              </div>
              <div class="dashboard-column dashboard-right">
                <div class="card quick-actions-card">
                  <div class="card-header">
                    <h2>Tu espacio jurídico</h2>
                  </div>
                  <div class="card-body quick-actions"><a routerLink="/tienda-juridica" class="quick-action">Explorar tienda jurídica →</a><a routerLink="/servicios" class="quick-action">Solicitar asesoría →</a><a routerLink="/mis-ordenes" class="quick-action">Mis solicitudes y pagos →</a>
                    <a routerLink="/casos" class="quick-action">
                      <span>Ver mis casos</span>
                      <span>➡</span>
                    </a>
                    <a class="quick-action" routerLink="/documentos">
                      <span>Ver documentos</span>
                      <span>➡</span>
                    </a>
                    <a class="quick-action" routerLink="/calendario">
                      <span>Revisar agenda</span>
                      <span>➡</span>
                    </a>
                    <a class="quick-action" routerLink="/mensajes">
                      <span>Mensajes del caso</span>
                      <span>➡</span>
                    </a>
                  </div>
                </div>
              </div>
            </div>
          </div>
        }
        @default {
          <div class="dashboard-view cliente-view">
            <p>Rol no identificado. Vuelve a iniciar sesión.</p>
          </div>
        }
      }
    </div>
    `
})
export class DashboardComponent implements OnInit {
  stats: DashboardStats | null = null;
  loadError: string | null = null;
  private readonly cdr = inject(ChangeDetectorRef);
  casos: Caso[] = [];
  myCasosCount = 0;
  upcomingAudienciasCount = 0;
  documentosCount = 0;
  today = new Date();
  constructor(
    public auth: AuthService,
    private dashboardService: DashboardService,
    private casoService: CasoService,
    private documentoService: DocumentoService
  ) {}

  get role(): string {
    return this.auth.currentUser()?.role?.toLowerCase() ?? 'unknown';
  }

  get roleLabel(): string {
    if (this.role === 'admin') {
      return 'Administrador';
    }
    if (this.role === 'abogado') {
      return 'Abogado';
    }
    return 'Cliente';
  }

  get roleDescription(): string {
    if (this.role === 'admin') {
      return 'Vista completa del sistema';
    }
    if (this.role === 'abogado') {
      return 'Gestiona tus casos y audiencias';
    }
    return 'Consulta tus casos y documentos';
  }

  get roleIcon(): string {
    if (this.role === 'admin') {
      return '👑';
    }
    if (this.role === 'abogado') {
      return '⚖️';
    }
    return '👥';
  }

  get pageTitle(): string {
    if (this.role === 'admin') {
      return 'Panel Administrativo';
    }
    if (this.role === 'abogado') {
      return 'Panel de Abogado';
    }
    return 'Panel de Cliente';
  }

  get pageSubtitle(): string {
    if (this.role === 'admin') {
      return 'Administra casos, clientes y recursos del sistema.';
    }
    if (this.role === 'abogado') {
      return 'Revisa tus casos asignados y prepara tus audiencias.';
    }
    return 'Visualiza el estado de tus casos y documentos.';
  }

  ngOnInit(): void {
    this.dashboardService.stats().subscribe({
      next: s => { this.stats = s; this.cdr.markForCheck(); },
      error: () => { this.loadError = 'No se pudo cargar el resumen. Vuelve a abrir el panel.'; this.cdr.markForCheck(); }
    });
    this.casoService.list().subscribe(c => {
      this.casos = c;
      this.computeRoleCounts();
      this.cdr.markForCheck();
    }, () => { this.loadError = 'No se pudieron cargar los casos del panel.'; this.cdr.markForCheck(); });
  }

  private getRoleLabel(): string {
    if (this.auth.hasRole('admin')) {
      return 'Administrador';
    }
    if (this.auth.hasRole('abogado')) {
      return 'Abogado';
    }
    return 'Cliente';
  }

  private getRoleDescription(): string {
    if (this.auth.hasRole('admin')) {
      return 'Vista completa del sistema';
    }
    if (this.auth.hasRole('abogado')) {
      return 'Gestiona tus casos y audiencias';
    }
    return 'Consulta tus casos y documentos';
  }

  private getRoleIcon(): string {
    if (this.auth.hasRole('admin')) {
      return '👑';
    }
    if (this.auth.hasRole('abogado')) {
      return '⚖️';
    }
    return '👥';
  }

  private getPageTitle(): string {
    if (this.auth.hasRole('admin')) {
      return 'Panel Administrativo';
    }
    if (this.auth.hasRole('abogado')) {
      return 'Panel de Abogado';
    }
    return 'Panel de Cliente';
  }

  private getPageSubtitle(): string {
    if (this.auth.hasRole('admin')) {
      return 'Administra casos, clientes y recursos del sistema.';
    }
    if (this.auth.hasRole('abogado')) {
      return 'Revisa tus casos asignados y prepara tus audiencias.';
    }
    return 'Visualiza el estado de tus casos y documentos.';
  }

  private computeRoleCounts(): void {
    // The backend already scopes this list using the stable Keycloak/local identity binding.
    // A local profile ID is not a Keycloak subject nor the legacy users.id (OIDC currentUser has no local ID).
    const relevant = this.auth.hasRole('abogado') || this.auth.hasRole('cliente') ? this.casos : [];
    this.myCasosCount = relevant.length;
    this.upcomingAudienciasCount = relevant.filter(c => c.fechaAudiencia && new Date(c.fechaAudiencia) > new Date()).length;
    if (relevant.length === 0) { this.documentosCount = 0; return; }

    const calls = relevant.map(caso => this.documentoService.list(caso.id));
    forkJoin(calls).subscribe(results => {
      this.documentosCount = results.reduce((sum, arr) => sum + (arr?.length ?? 0), 0);
      this.cdr.markForCheck();
    }, () => this.documentosCount = 0);
  }

  get proximasAudiencias(): Caso[] {
    return this.casos.filter(c => c.fechaAudiencia && new Date(c.fechaAudiencia) > this.today)
      .sort((a,b) => new Date(a.fechaAudiencia!).getTime() - new Date(b.fechaAudiencia!).getTime());
  }

  countByState(state: string): number {
    return this.casos.filter(c => c.estado === state).length;
  }

  percentByState(state: string): number {
    if (!this.casos.length) {
      return 0;
    }
    return Math.round((this.countByState(state) / this.casos.length) * 100);
  }
}
