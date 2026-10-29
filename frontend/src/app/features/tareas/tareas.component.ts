import { Component, ChangeDetectionStrategy } from '@angular/core';

import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-tareas',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.Default,
  template: `
    <div class="page-header">
      <div>
        <h1 class="page-title">Tareas</h1>
        <p class="page-subtitle">Organiza las actividades y tareas pendientes del equipo.</p>
      </div>
    </div>
    <div class="card">
      <div class="card-body">
        <p>Esta sección está en construcción. Aquí podrás gestionar las tareas del despacho.</p>
        <a routerLink="/dashboard" class="btn btn-primary">Volver al dashboard</a>
      </div>
    </div>
  `
})
export class TareasComponent {}
