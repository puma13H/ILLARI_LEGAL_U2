import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from './auth-service';
import { destinationForRoles } from './auth-destination';

@Component({
  selector: 'app-auth-callback',
  standalone: true,
  template: `
    <main class="auth-screen">
      <div class="auth-card">
        <h2>ILLARI LEGAL · Inicio de sesión</h2>
        @if (error()) {
          <p>{{ error() }}</p>
          <a href="/login">Volver al inicio</a>
        } @else {
          <p>Verificando la respuesta de Keycloak y PKCE...</p>
        }
      </div>
    </main>
  `
})
export class AuthCallback {
  protected readonly error = signal<string | null>(null);

  constructor() {
    const router = inject(Router);
    const route = inject(ActivatedRoute);
    const auth = inject(AuthService);

    const code = route.snapshot.queryParamMap.get('code');
    const state = route.snapshot.queryParamMap.get('state');
    const error = route.snapshot.queryParamMap.get('error');

    if (error) {
      this.error.set('Keycloak rechazó el inicio de sesión: ' + error);
      return;
    }

    if (!code || !state) {
      this.error.set('Falta el código de retorno de Keycloak.');
      return;
    }

    void auth.completarLogin(code, state)
      .then(() => {
        auth.destinoPendiente();
        const destination = destinationForRoles(auth.roles());
        if (!destination) throw new Error('Tu cuenta no tiene un rol autorizado en ILLARI LEGAL.');
        return router.navigateByUrl(destination, { replaceUrl: true });
      })
      .catch((err: unknown) =>
        this.error.set(err instanceof Error ? err.message : 'No se pudo iniciar sesión.')
      );
  }
}

