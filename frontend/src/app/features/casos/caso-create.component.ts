import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AbogadoService } from '../../core/services/abogado.service';
import { CasoService } from '../../core/services/caso.service';
import { ClienteService } from '../../core/services/cliente.service';
import { AuthService } from '../../core/auth/auth-service';
import { Abogado, Caso, Cliente } from '../../core/models';

@Component({
  selector: 'app-caso-create',
  standalone: true,
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.Default,
  template: `
    <div class="page-header">
      <div>
        <a routerLink="/casos" class="btn-link">← Volver a casos</a>
        <h1 class="page-title">Nuevo caso</h1>
        <p class="page-subtitle">Crear un caso nuevo como abogado o administrador</p>
      </div>
    </div>
    
    <div class="card">
      <div class="card-body">
        <form (ngSubmit)="createCase()" class="form-grid">
          <div class="form-group">
            <label>Cliente</label>
            <select class="input" [(ngModel)]="newCaso.clienteId" name="clienteId" required>
              <option [ngValue]="undefined">Seleccione un cliente</option>
              @for (cliente of clientes; track cliente) {
                <option [ngValue]="cliente.id">{{ cliente.nombre }}</option>
              }
            </select>
          </div>
    
          @if (auth.hasRole('admin')) {
            <div class="form-group">
              <label>Abogado</label>
              <select class="input" [(ngModel)]="newCaso.abogadoId" name="abogadoId" required>
                <option [ngValue]="undefined">Seleccione un abogado</option>
                @for (abogado of abogados; track abogado) {
                  <option [ngValue]="abogado.id">{{ abogado.nombre }}</option>
                }
              </select>
            </div>
          }
    
          <div class="form-group">
            <label>Nombre del caso</label>
            <input class="input" type="text" [(ngModel)]="newCaso.nombreCaso" name="nombreCaso" required />
          </div>
    
          <div class="form-group">
            <label>Estado</label>
            <select class="input" [(ngModel)]="newCaso.estado" name="estado">
              <option value="En trámite">En trámite</option>
              <option value="Pendiente">Pendiente</option>
              <option value="Finalizado">Finalizado</option>
              <option value="Cancelado">Cancelado</option>
            </select>
          </div>
    
          <div class="form-group">
            <label>Apertura</label>
            <input class="input" type="date" [(ngModel)]="newCaso.fechaApertura" name="fechaApertura" required />
          </div>
    
          <div class="form-group">
            <label>Tarifa</label>
            <input class="input" type="number" [(ngModel)]="newCaso.tarifa" name="tarifa" />
          </div>
    
          <div class="form-group">
            <label>Fecha audiencia</label>
            <input class="input" type="date" [(ngModel)]="newCaso.fechaAudiencia" name="fechaAudiencia" />
          </div>
    
          <div class="form-group" style="grid-column: span 2;">
            <label>Descripción</label>
            <textarea class="input" rows="4" [(ngModel)]="newCaso.descripcion" name="descripcion"></textarea>
          </div>
    
          <div class="form-group" style="grid-column: span 2; display:flex; justify-content:flex-end;">
            <button type="submit" class="btn btn-primary">Crear caso</button>
          </div>
        </form>
      </div>
    </div>
    `
})
export class CasoCreateComponent implements OnInit {
  clientes: Cliente[] = [];
  abogados: Abogado[] = [];
  newCaso: Partial<Caso> & { clienteId?: number; abogadoId?: number } = {
    nombreCaso: '',
    clienteId: undefined,
    abogadoId: undefined,
    estado: 'En trámite',
    fechaApertura: new Date().toISOString().slice(0, 10),
    descripcion: '',
    tarifa: undefined,
    fechaAudiencia: ''
  };

  constructor(
    private casoService: CasoService,
    private clienteService: ClienteService,
    private abogadoService: AbogadoService,
    private router: Router,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.clienteService.list().subscribe(clients => this.clientes = clients);
    if (this.auth.hasRole('admin')) {
      this.abogadoService.list().subscribe(abogados => this.abogados = abogados);
    }
  }

  createCase(): void {
    if (!this.newCaso.nombreCaso?.trim() || !this.newCaso.clienteId || !this.newCaso.fechaApertura) {
      return;
    }

    const payload: Partial<Caso> & { clienteId: number; abogadoId?: number } = {
      clienteId: this.newCaso.clienteId,
      nombreCaso: this.newCaso.nombreCaso,
      estado: this.newCaso.estado,
      fechaApertura: this.newCaso.fechaApertura,
      descripcion: this.newCaso.descripcion,
      tarifa: this.newCaso.tarifa,
      fechaAudiencia: this.newCaso.fechaAudiencia,
      abogadoId: this.newCaso.abogadoId
    };

    this.casoService.create(payload).subscribe(() => {
      this.router.navigate(['/casos']);
    });
  }
}
