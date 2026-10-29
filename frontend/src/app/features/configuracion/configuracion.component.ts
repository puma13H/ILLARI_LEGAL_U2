import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { AuthService } from '../../core/auth/auth-service';
import { AuthUser } from '../../core/models';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-configuracion',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Default,
  template: `
    <div class="page-header">
      <div>
        <h1 class="page-title">Mi Perfil</h1>
        <p class="page-subtitle">Consulta la información de tu cuenta. Los datos de identidad se administran en Keycloak.</p>
      </div>
    </div>
    <div class="card">
      <div class="card-body">
        <div class="form-group">
          <label>Nombre</label>
          <input class="input" [value]="profile.name" readonly />
        </div>
        <div class="form-group">
          <label>Correo electrónico</label>
          <input class="input" type="email" [value]="profile.email || 'No incluido en el token'" readonly />
        </div>
        <a class="btn btn-primary" [href]="keycloakAccountUrl" target="_blank" rel="noopener noreferrer">
          Administrar cuenta en Keycloak
        </a>
      </div>
    </div>
    `
})
export class ConfiguracionComponent implements OnInit {
  profile: AuthUser = { userId: 0, name: '', email: '', role: 'cliente', token: '' };
  readonly keycloakAccountUrl = `${environment.keycloak.issuer}/account`;

  constructor(private auth: AuthService) {}

  ngOnInit(): void {
    const user = this.auth.currentUser();
    if (user) {
      this.profile = { ...user };
    }
  }

}
