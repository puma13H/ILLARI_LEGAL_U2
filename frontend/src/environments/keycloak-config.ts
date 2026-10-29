export function keycloakConfig() {
  const runtime = (globalThis as typeof globalThis & { __ILLARI_CONFIG__?: { keycloakUrl?: string } }).__ILLARI_CONFIG__;
  const url = (runtime?.keycloakUrl || 'http://localhost:18180').replace(/\/$/, '');
  const origin = globalThis.location?.origin || 'http://localhost:4200';
  return { url, realm: 'illari-legal', issuer: url + '/realms/illari-legal', clientId: 'illari-frontend',
    redirectUri: origin + '/auth/callback', postLogoutRedirectUri: origin + '/' };
}

