export interface JwtPayload {
  sub: string;
  exp: number;
  iss?: string;
  aud?: string | string[];
  nonce?: string;
  iat?: number;
  azp?: string;
  name?: string;
  email?: string;
  preferred_username?: string;
  realm_access?: { roles: string[] };
}

export function decodeJwtPayload(token: string): JwtPayload | null {
  const parts = token.split('.');
  if (parts.length !== 3) return null;

  try {
    const padded = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const decoded = atob(padded);
    const bytes = Uint8Array.from(decoded, ch => ch.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as JwtPayload;
  } catch {
    return null;
  }
}
