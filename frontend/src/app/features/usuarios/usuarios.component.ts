import { Component, ChangeDetectionStrategy } from '@angular/core';


@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.Default,
  template: `
    <div class="page-header">
      <div>
        <p class="eyebrow">Administración</p>
        <h1 class="page-title">Usuarios</h1>
        <p class="page-subtitle">La autenticación y los roles se administran en Keycloak.</p>
      </div>
    </div>
    <div class="card">
      <div class="card-header">
        <h2>Administración de identidades</h2>
      </div>
      <div class="card-body">
        <p>Esta aplicación no incluye un CRUD global de usuarios. ADMIN, ABOGADO y CLIENTE se asignan en Keycloak por un operador autorizado. El rol ADMIN de la aplicación no concede acceso a la consola del proveedor.</p>
      </div>
    </div>
  `
})
export class UsuariosComponent {}
