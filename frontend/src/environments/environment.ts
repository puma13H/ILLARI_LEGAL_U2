import { keycloakConfig } from './keycloak-config';
export const environment = {
  production: false,
  apiBaseUrl: '/api',
  apiUrl: '/api',
  keycloak: keycloakConfig()
};
