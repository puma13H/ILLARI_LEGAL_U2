import { describe, expect, it } from 'vitest';
import { esApiPropia } from './api-destination';
describe('destinos autorizados para JWT', () => {
  const origin = 'http://localhost:4200';
  it('autoriza solo el API propio con base de producción vacía', () => {
    for (const url of ['/api','/api/v1/ordenes','http://localhost:4200/api/casos'])
      expect(esApiPropia(url, ['', '/api'], origin)).toBe(true);
    for (const url of ['https://evil.test/api','//evil.test/api','/assets/icon.svg','/apiary','https://localhost:4200/api',
      'http://localhost:4200.evil.test/api','http://localhost:4200@evil.test/api','data:text/plain,/api'])
      expect(esApiPropia(url, ['', '/api'], origin)).toBe(false);
  });
  it('respeta host, puerto y límite de ruta del Gateway de desarrollo', () => {
    const bases = ['http://localhost:18080', 'http://localhost:18080/api'];
    expect(esApiPropia('http://localhost:18080/api/v1/clientes', bases, origin)).toBe(true);
    for (const url of ['http://localhost:18080.evil.test/api','http://localhost:18081/api','http://localhost:18080/actuator',
      'http://localhost:18180/realms/illari-legal','http://localhost:18080/api-other'])
      expect(esApiPropia(url, bases, origin)).toBe(false);
  });
});

