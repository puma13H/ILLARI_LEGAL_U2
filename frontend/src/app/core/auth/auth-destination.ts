export type ApplicationRole = 'ADMIN' | 'ABOGADO' | 'CLIENTE';

export function destinationForRole(role: ApplicationRole): string {
  return `/dashboard/${role.toLowerCase()}`;
}

export function destinationForRoles(roles: readonly string[]): string | null {
  const normalizedRoles = new Set(roles.map(role => role.toUpperCase()));
  const role = (['ADMIN', 'ABOGADO', 'CLIENTE'] as const)
    .find(candidate => normalizedRoles.has(candidate));
  return role ? destinationForRole(role) : null;
}
