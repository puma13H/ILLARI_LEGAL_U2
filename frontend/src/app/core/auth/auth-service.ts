import { Injectable, computed, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { codeChallenge, codeVerifier, stateAleatorio } from './pkce';
import { decodeJwtPayload } from './jwt';
import { AuthUser } from '../models';
import { validarIdToken } from './oidc-token';
import { destinationForRoles } from './auth-destination';
interface Session { accessToken: string; idToken: string; refreshToken: string; expiresAt: number; nonce: string; }
const PREFIX = 'illari_s12_';


@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly sesion = signal<Session | null>(null);
  private expiryTimer: ReturnType<typeof setTimeout> | undefined;
  private refreshInFlight: Promise<boolean> | undefined;
  readonly autenticado = computed(() => !!this.sesion() && this.sesion()!.expiresAt > Date.now());
  readonly roles = computed(() => decodeJwtPayload(this.sesion()?.accessToken ?? '')?.realm_access?.roles ?? []);
  readonly idCliente = computed(() => decodeJwtPayload(this.sesion()?.accessToken ?? '')?.sub ?? null);
  readonly nombreUsuario = computed(() => decodeJwtPayload(this.sesion()?.accessToken ?? '')?.preferred_username ?? null);
  readonly currentUser = computed<AuthUser | null>(() => {
    if (!this.autenticado()) return null;
    const payload = decodeJwtPayload(this.sesion()?.accessToken ?? '');
    const roles = (payload?.realm_access?.roles ?? []).map(r => r.toUpperCase());
    const role = ['ADMIN', 'ABOGADO', 'CLIENTE'].find(r => roles.includes(r));
    if (!role) return null;
    return {
      userId: 0,
      name: payload?.name ?? payload?.preferred_username ?? 'usuario',
      email: payload?.email ?? '',
      role: role.toLowerCase() as AuthUser['role'],
      token: this.sesion()?.accessToken ?? '',
    };
  });
  tieneRol(...roles: string[]) { return roles.some(r => this.roles().map(x => x.toUpperCase()).includes(r.toUpperCase())); }
  hasRole(role: string): boolean { return this.tieneRol(role); }
  accessToken() { return this.autenticado() ? this.sesion()?.accessToken ?? null : null; }

  /** Tokens only live in JS memory; verifier and state survive just one redirect. */
  async iniciarLogin(destino = location.pathname + location.search, registro = false): Promise<void> {
    try {
      const response = await fetch(`${environment.keycloak.issuer}/.well-known/openid-configuration`, {
        credentials:'omit',redirect:'error',cache:'no-store',signal:AbortSignal.timeout(6000)
      });
      if (!response.ok) throw new Error('Discovery no disponible');
      const config = await response.json() as {issuer?: string};
      if (config.issuer !== environment.keycloak.issuer) throw new Error('Realm inesperado');
    } catch {
      throw new Error('Servicio de autenticación no disponible. No se pudo conectar con Keycloak.');
    }
    const nonce = stateAleatorio();
    sessionStorage.setItem(PREFIX + 'nonce', nonce);
    const verifier = codeVerifier(); const state = stateAleatorio();
    sessionStorage.setItem(PREFIX + 'verifier', verifier);
    sessionStorage.setItem(PREFIX + 'state', state);
    sessionStorage.setItem(PREFIX + 'destino', destino.startsWith('/') && !destino.startsWith('//') ? destino : '/');
    sessionStorage.removeItem(PREFIX + 'rol-solicitado');
    const search = new URLSearchParams({client_id: environment.keycloak.clientId, response_type: 'code',
      redirect_uri: environment.keycloak.redirectUri, scope:'openid profile email', state, nonce,
      code_challenge: await codeChallenge(verifier), code_challenge_method:'S256'});
    
    location.assign(`${environment.keycloak.issuer}/protocol/openid-connect/${registro ? 'registrations' : 'auth'}?${search}`);
  }

  async completarLogin(code: string, stateRecibido: string): Promise<void> {
    const state = sessionStorage.getItem(PREFIX + 'state');
    const verifier = sessionStorage.getItem(PREFIX + 'verifier');
    const nonce = sessionStorage.getItem(PREFIX + 'nonce');
    sessionStorage.removeItem(PREFIX + 'nonce');
    sessionStorage.removeItem(PREFIX + 'state');
    sessionStorage.removeItem(PREFIX + 'verifier');
    if (!state || state !== stateRecibido || !verifier) throw new Error('State o code_verifier inválido; inicia sesión otra vez.');
    const body = new URLSearchParams({grant_type:'authorization_code',client_id:environment.keycloak.clientId,
      redirect_uri:environment.keycloak.redirectUri,code,code_verifier:verifier});
    const response = await fetch(`${environment.keycloak.issuer}/protocol/openid-connect/token`, {
      method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body});
    if (!response.ok) throw new Error(`Keycloak no entregó el token (HTTP ${response.status}).`);
    const tokens = await response.json() as {access_token:string; id_token?:string; refresh_token?:string; expires_in:number};
    if (!tokens.access_token || !Number.isFinite(tokens.expires_in) || tokens.expires_in <= 0) {
      throw new Error('Keycloak devolvió un token o una expiración inválidos.');
    }
    if (!tokens.id_token || !nonce) throw new Error('Falta la prueba de identidad OIDC.');
    const identity = await validarIdToken(tokens.id_token, environment.keycloak.issuer, environment.keycloak.clientId, nonce);
    const access = decodeJwtPayload(tokens.access_token);
    if (access?.sub !== identity.sub || access.iss !== environment.keycloak.issuer)
      throw new Error('La identidad y el token de acceso no corresponden a la misma sesión.');
    const tokenExpiry = decodeJwtPayload(tokens.access_token)?.exp;
    const expiresAt = Math.min(
      Date.now() + tokens.expires_in * 1000,
      typeof tokenExpiry === 'number' ? tokenExpiry * 1000 : Number.POSITIVE_INFINITY,
    );
    if (expiresAt <= Date.now()) throw new Error('Keycloak devolvió un token vencido.');
    if (!destinationForRoles(access?.realm_access?.roles ?? [])) {
      this.clearSession();
      throw new Error('Tu cuenta no tiene un rol autorizado en ILLARI LEGAL.');
    }
    this.sesion.set({accessToken:tokens.access_token, idToken:tokens.id_token ?? '',
      refreshToken:tokens.refresh_token ?? '', expiresAt, nonce});
    this.scheduleExpiry(expiresAt);
    sessionStorage.removeItem(PREFIX + 'rol-solicitado');
  }
  destinoPendiente(): string {
    const d = sessionStorage.getItem(PREFIX + 'destino') ?? '/';
    sessionStorage.removeItem(PREFIX + 'destino');
    return d.startsWith('/') && !d.startsWith('//') && !d.startsWith('/auth/callback') && d !== '/login' ? d : '/';
  }
  logout(): void {
    this.cerrarSesion();
  }

  cerrarSesion(): void {
    const id = this.sesion()?.idToken;
    this.clearSession();
    const params = new URLSearchParams({client_id: environment.keycloak.clientId,
      post_logout_redirect_uri:environment.keycloak.postLogoutRedirectUri});
    if (id) params.set('id_token_hint', id);
    location.assign(`${environment.keycloak.issuer}/protocol/openid-connect/logout?${params}`);
  }

  private scheduleExpiry(expiresAt: number): void {
    this.clearExpiryTimer();
    const remaining = expiresAt - Date.now();
    const delay = remaining > 30000 ? remaining - 30000 : Math.max(0, remaining * .8);
    this.expiryTimer = setTimeout(async () => {
      const expected = this.sesion();
      const renewed = await this.renovarSesion();
      // A logout or another login while refresh was pending must never be undone.
      if (renewed || this.sesion() !== expected) return;
      this.clearSession();
      location.assign('/login?session_expired=1');
    }, Math.min(delay, 2_147_000_000));
  }

  /** Single-flight rotation; access/refresh/ID tokens remain only in memory. */
  renovarSesion(): Promise<boolean> {
    if (this.refreshInFlight) return this.refreshInFlight;
    const current = this.sesion();
    if (!current?.refreshToken) return Promise.resolve(false);
    this.refreshInFlight = this.refresh(current).finally(() => { this.refreshInFlight = undefined; });
    return this.refreshInFlight;
  }

  private async refresh(current: Session): Promise<boolean> {
    try {
      const body = new URLSearchParams({grant_type:'refresh_token',client_id:environment.keycloak.clientId,
        refresh_token:current.refreshToken});
      const response = await fetch(`${environment.keycloak.issuer}/protocol/openid-connect/token`, {
        method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body,
        signal:AbortSignal.timeout(10000)});
      if (!response.ok) return false;
      const tokens = await response.json() as {access_token:string;id_token?:string;refresh_token?:string;expires_in:number};
      if (!Number.isFinite(tokens.expires_in) || tokens.expires_in <= 0) return false;
      const claims = decodeJwtPayload(tokens.access_token);
      const previous = decodeJwtPayload(current.accessToken);
      const expiresAt = Math.min(Date.now() + tokens.expires_in * 1000, (claims?.exp ?? 0) * 1000);
      const audiences = typeof claims?.aud === 'string' ? [claims.aud] : claims?.aud ?? [];
      if (!claims?.sub || claims.sub !== previous?.sub || claims.iss !== environment.keycloak.issuer
          || !audiences.includes('illari-api') || !Number.isFinite(expiresAt) || expiresAt <= Date.now()) return false;
      if (tokens.id_token) {
        const identity = await validarIdToken(tokens.id_token, environment.keycloak.issuer,
          environment.keycloak.clientId, current.nonce, true);
        if (identity.sub !== claims.sub) return false;
      }
      if (this.sesion() !== current) return false;
      this.sesion.set({accessToken:tokens.access_token,idToken:tokens.id_token ?? current.idToken,
        refreshToken:tokens.refresh_token ?? current.refreshToken,expiresAt,nonce:current.nonce});
      this.scheduleExpiry(expiresAt);
      return true;
    } catch { return false; }
  }

  private clearExpiryTimer(): void {
    if (this.expiryTimer !== undefined) clearTimeout(this.expiryTimer);
    this.expiryTimer = undefined;
  }

  private clearSession(): void {
    this.clearExpiryTimer();
    this.sesion.set(null);
  }
}
