import { keycloakConfig } from './keycloak-config';
export const environment = {
  production: true,
  apiBaseUrl: '',
  apiUrl: '/api',
  keycloak: keycloakConfig()
};
