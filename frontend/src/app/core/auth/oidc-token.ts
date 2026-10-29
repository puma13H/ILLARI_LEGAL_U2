import { decodeJwtPayload } from './jwt';

function bytes(value: string): Uint8Array<ArrayBuffer> {
  const raw = atob(value.replace(/-/g, '+').replace(/_/g, '/'));
  return Uint8Array.from(raw, char => char.charCodeAt(0));
}

/** Verify the OIDC ID token before establishing the browser session. No API JWT is sent to JWKS. */
export async function validarIdToken(token: string, issuer: string, clientId: string, nonce: string, allowMissingNonce = false) {
  const parts = token.split('.');
  if (parts.length !== 3) throw new Error('ID token inválido.');
  const header = JSON.parse(new TextDecoder().decode(bytes(parts[0]))) as { alg?: string; kid?: string };
  if (header.alg !== 'RS256' || !header.kid) throw new Error('Firma del ID token no admitida.');
  const response = await fetch(issuer + '/protocol/openid-connect/certs', { credentials: 'omit', redirect: 'error' });
  if (!response.ok) throw new Error('No se pudo verificar la firma de Keycloak.');
  const jwks = await response.json() as { keys: (JsonWebKey & { kid?: string })[] };
  const jwk = jwks.keys.find(key => key.kid === header.kid && key.kty === 'RSA' && (!key.use || key.use === 'sig'));
  if (!jwk) throw new Error('Clave del ID token desconocida.');
  const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
  const valid = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, bytes(parts[2]),
    new TextEncoder().encode(parts[0] + '.' + parts[1]));
  const claims = decodeJwtPayload(token);
  const audiences = typeof claims?.aud === 'string' ? [claims.aud] : claims?.aud || [];
  const now = Date.now() / 1000;
  if (!valid || !claims?.sub || claims.iss !== issuer || !audiences.includes(clientId)
    || (!(allowMissingNonce && claims.nonce === undefined) && claims.nonce !== nonce) || !Number.isFinite(claims.exp) || claims.exp <= now
    || (claims.iat !== undefined && claims.iat > now + 5)
    || (audiences.length > 1 && claims.azp !== clientId)) throw new Error('ID token de Keycloak rechazado.');
  return claims;
}

