/** Compare parsed origins and API path boundaries; an empty base never matches every URL. */
export function esApiPropia(destino: string, bases: readonly string[], origen: string): boolean {
  try {
    const target = new URL(destino, origen + '/');
    if (!['http:', 'https:'].includes(target.protocol) || target.username || target.password) return false;
    return bases.some(base => {
      const api = new URL(base || '/api', origen + '/');
      const path = api.pathname.replace(/\/+$/, '') || '/api';
      return target.origin === api.origin && (target.pathname === path || target.pathname.startsWith(path + '/'));
    });
  } catch { return false; }
}

