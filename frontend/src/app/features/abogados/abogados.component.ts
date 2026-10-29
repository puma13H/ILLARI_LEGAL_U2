import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { AbogadoService } from '../../core/services/abogado.service';
import { Abogado } from '../../core/models';

@Component({
  selector: 'app-abogados',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.Default,
  template: `
    <div class="page-header">
      <div>
        <h1 class="page-title">Abogados</h1>
        <p class="page-subtitle">Equipo legal del bufete</p>
      </div>
    </div>
    
    <div class="form-card">
      <h3>Registrar nuevo abogado</h3>
      <form (ngSubmit)="crear()">
        <div class="form-group">
          <label>Nombre completo</label>
          <input class="input" [(ngModel)]="form.nombre" name="nombre" required />
        </div>
        <div class="form-group">
          <label>Especialidad</label>
          <input class="input" [(ngModel)]="form.especialidad" name="especialidad" required />
        </div>
        <div class="form-group">
          <label>Teléfono</label>
          <input class="input" [(ngModel)]="form.telefono" name="telefono" required />
        </div>
        <div class="form-group">
          <label>Correo electrónico</label>
          <input class="input" type="email" [(ngModel)]="form.email" name="email" required />
        </div>
        <div class="form-group">
          <label>Dirección</label>
          <input class="input" [(ngModel)]="form.direccion" name="direccion" />
        </div>
        <button type="submit" class="btn btn-primary">Crear abogado</button>
        @if (passwordInfo) {
          <div class="alert alert-success" style="margin-top:1rem">
            Contraseña temporal: <strong>{{ passwordInfo }}</strong>
          </div>
        }
        @if (successMsg) {
          <div class="alert alert-success" style="margin-top:1rem">
            {{ successMsg }}
          </div>
        }
        @if (errorMsg) {
          <div class="alert alert-error" style="margin-top:1rem">
            {{ errorMsg }}
          </div>
        }
      </form>
    </div>
    
    <div class="card">
      <div class="card-header"><h2>Listado de abogados</h2></div>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr><th>Nombre</th><th>Especialidad</th><th>Email</th><th>Teléfono</th><th></th></tr>
          </thead>
          <tbody>
            @for (a of abogados; track a) {
              <tr>
                <td><strong>{{ a.nombre }}</strong></td>
                <td><span class="badge badge-primary">{{ a.especialidad }}</span></td>
                <td>{{ a.email }}</td>
                <td>{{ a.telefono }}</td>
                <td><button type="button" class="btn btn-danger" (click)="eliminar(a.id)">Eliminar</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
    `
})
export class AbogadosComponent implements OnInit {
  abogados: Abogado[] = [];
  form = { nombre: '', telefono: '', email: '', especialidad: '', direccion: '' };
  passwordInfo = '';
  successMsg = '';
  errorMsg = '';

  constructor(private abogadoService: AbogadoService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.abogadoService.list().subscribe({ next: a => this.abogados = a, error: err => console.error('Error al cargar abogados:', err) });
  }

  crear(): void {
    this.errorMsg = '';
    this.successMsg = '';
    this.passwordInfo = '';

    this.abogadoService.create(this.form).subscribe({
      next: resp => {
        const body: any = resp.body;
        if (body && typeof body === 'object') {
          this.passwordInfo = body.passwordTemporal ?? '';
          this.successMsg = `Abogado creado correctamente${body.email ? ' (' + body.email + ')' : ''}`;
        } else {
          this.successMsg = 'Abogado creado correctamente';
        }
        this.form = { nombre: '', telefono: '', email: '', especialidad: '', direccion: '' };
        this.load();
      },
      error: err => {
        console.error('Error al crear abogado:', err);
        if (err.error) {
          if (typeof err.error === 'string') {
            this.errorMsg = err.error;
          } else if (err.error.message) {
            this.errorMsg = err.error.message;
          } else {
            this.errorMsg = JSON.stringify(err.error);
          }
        } else if (err.statusText) {
          this.errorMsg = err.statusText;
        } else {
          this.errorMsg = 'Error desconocido al crear abogado';
        }
      }
    });
  }

  eliminar(id: number): void {
    if (confirm('¿Eliminar abogado?')) {
      this.abogadoService.delete(id).subscribe(() => this.load());
    }
  }
}
