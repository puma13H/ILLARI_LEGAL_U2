import { describe, expect, it } from 'vitest';
import { destinationForRole, destinationForRoles } from './auth-destination';

describe('auth role destinations', () => {
  it('redirects ADMIN to its dashboard', () => {
    expect(destinationForRole('ADMIN')).toBe('/dashboard/admin');
    expect(destinationForRoles(['ADMIN'])).toBe('/dashboard/admin');
  });

  it('redirects ABOGADO to its dashboard', () => {
    expect(destinationForRole('ABOGADO')).toBe('/dashboard/abogado');
    expect(destinationForRoles(['ABOGADO'])).toBe('/dashboard/abogado');
  });

  it('redirects CLIENTE to its dashboard', () => {
    expect(destinationForRole('CLIENTE')).toBe('/dashboard/cliente');
    expect(destinationForRoles(['CLIENTE'])).toBe('/dashboard/cliente');
  });

  it('normalizes token role case and preserves ADMIN-first precedence', () => {
    expect(destinationForRoles(['cliente', 'abogado', 'admin'])).toBe('/dashboard/admin');
  });

  it('does not assign a destination to an unknown role', () => {
    expect(destinationForRoles(['ANALISTA'])).toBeNull();
  });
});
