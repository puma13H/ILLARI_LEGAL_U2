import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ClienteService } from '../../core/services/cliente.service';
import { Cliente } from '../../core/models';

@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.Default,
  template: `
    <div class="page-header">
      <div>
        <h1 class="page-title">Clientes</h1>
        <p class="page-subtitle">Gestión de clientes del bufete</p>
      </div>
    </div>
    
    <div class="form-card">
      <h3>Registrar nuevo cliente</h3>
      @if (errorMsg) {
        <div class="alert alert-error" style="margin-bottom:1rem; padding:1rem; background-color:#fee; color:#c00; border-radius:4px">
          ❌ {{ errorMsg }}
        </div>
      }
      @if (successMsg) {
        <div class="alert alert-success" style="margin-bottom:1rem; padding:1rem; background-color:#efe; color:#060; border-radius:4px">
          ✓ {{ successMsg }}
        </div>
      }
      <form (ngSubmit)="crear()">
        <div class="form-group">
          <label>Nombre completo</label>
          <input class="input" [(ngModel)]="form.nombre" name="nombre" required />
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
        <button type="submit" class="btn btn-primary">Crear cliente</button>
        @if (passwordInfo) {
          <div class="alert alert-success" style="margin-top:1rem">
            Contraseña temporal: <strong>{{ passwordInfo }}</strong>
          </div>
        }
      </form>
    </div>
    
    <div class="card">
      <div class="card-header"><h2>Listado de clientes</h2></div>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr><th>Nombre</th><th>Email</th><th>Teléfono</th><th></th></tr>
          </thead>
          <tbody>
            @for (c of clientes; track c) {
              <tr>
                <td><strong>{{ c.nombre }}</strong></td>
                <td>{{ c.email }}</td>
                <td>{{ c.telefono }}</td>
                <td><button type="button" class="btn btn-danger" (click)="eliminar(c.id)">Eliminar</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
    `
})
export class ClientesComponent implements OnInit {
  clientes: Cliente[] = [];
  form = { nombre: '', telefono: '', email: '', direccion: '' };
  passwordInfo = '';
  errorMsg = '';
  successMsg = '';

  constructor(private clienteService: ClienteService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.clienteService.list().subscribe({
      next: c => this.clientes = c,
      error: err => console.error('Error al cargar clientes:', err)
    });
  }

  crear(): void {
    this.errorMsg = '';
    this.successMsg = '';
    
    this.clienteService.create(this.form).subscribe({
      next: resp => {
        // resp is HttpResponse<any> when using observe:'response'
        const status = resp.status;
        const body: any = resp.body;
        if (status >= 200 && status < 300) {
          // handle JSON body with passwordTemporal or accept text/plain
          if (body && typeof body === 'object' && (body.passwordTemporal || body.email)) {
            this.passwordInfo = body.passwordTemporal ?? '';
            this.successMsg = `Cliente creado. Email: ${body.email ?? ''}`;
          } else if (typeof body === 'string' && body.trim()) {
            this.successMsg = `Cliente creado: ${body}`;
          } else {
            this.successMsg = 'Cliente creado correctamente';
          }
          this.form = { nombre: '', telefono: '', email: '', direccion: '' };
          this.load();
        } else {
          this.errorMsg = `Error: ${status}`;
        }
      },
      error: err => {
        console.error('Error al crear cliente:', err);
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
          this.errorMsg = 'Error desconocido al crear cliente';
        }
      }
    });
  }

  eliminar(id: number): void {
    if (confirm('¿Eliminar cliente?')) {
      this.clienteService.delete(id).subscribe(() => this.load());
    }
  }
}
