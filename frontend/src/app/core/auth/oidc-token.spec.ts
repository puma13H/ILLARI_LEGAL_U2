import { beforeAll, afterEach, describe, expect, it, vi } from 'vitest';
import { validarIdToken } from './oidc-token';
const issuer = 'http://issuer.test/realms/illari-legal';
let pair: CryptoKeyPair;
let publicJwk: JsonWebKey;
function encode(value: Uint8Array) { return btoa(String.fromCharCode(...value)).replaceAll('=', '').replaceAll('+', '-').replaceAll('/', '_'); }
function json(value: object) { return encode(new TextEncoder().encode(JSON.stringify(value))); }
async function token(changes = {}) {
  const content = json({ alg: 'RS256', kid: 'oidc-test' }) + '.' + json({ sub: 'test-sub', iss: issuer,
    aud: 'illari-frontend', nonce: 'expected-nonce', exp: Date.now() / 1000 + 120, ...changes });
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', pair.privateKey, new TextEncoder().encode(content));
  return content + '.' + encode(new Uint8Array(signature));
}
beforeAll(async () => {
  pair = await crypto.subtle.generateKey({ name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256', modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]) }, true, ['sign', 'verify']);
  publicJwk = await crypto.subtle.exportKey('jwk', pair.publicKey);
});
afterEach(() => vi.unstubAllGlobals());
function certs() { vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ keys: [{ ...publicJwk, kid: 'oidc-test' }] })))); }
describe('validación OIDC del navegador', () => {
  it('verifica firma, issuer, audiencia, nonce y expiración', async () => {
    certs(); expect((await validarIdToken(await token(), issuer, 'illari-frontend', 'expected-nonce')).sub).toBe('test-sub');
  });
  it('rechaza otra audiencia', async () => {
    certs(); await expect(validarIdToken(await token({ aud: 'other' }), issuer, 'illari-frontend', 'expected-nonce')).rejects.toThrow();
  });
  it('rechaza otro issuer', async () => {
    certs(); await expect(validarIdToken(await token({ iss: 'http://other.test' }), issuer, 'illari-frontend', 'expected-nonce')).rejects.toThrow();
  });
  it('rechaza un nonce distinto', async () => {
    certs(); await expect(validarIdToken(await token({ nonce: 'other' }), issuer, 'illari-frontend', 'expected-nonce')).rejects.toThrow();
  });
  it('rechaza un ID token vencido', async () => {
    certs(); await expect(validarIdToken(await token({ exp: 1 }), issuer, 'illari-frontend', 'expected-nonce')).rejects.toThrow();
  });
  it('rechaza una firma modificada', async () => {
    certs(); const parts = (await token()).split('.'); parts[2] = (parts[2][0] === 'A' ? 'B' : 'A') + parts[2].slice(1);
    await expect(validarIdToken(parts.join('.'), issuer, 'illari-frontend', 'expected-nonce')).rejects.toThrow();
  });
});


it('permite nonce ausente solo en refresh y conserva firma e identidad',async()=>{
 certs();const jwt=await token({nonce:undefined});
 await expect(validarIdToken(jwt,issuer,'illari-frontend','expected-nonce')).rejects.toThrow();
 await expect(validarIdToken(jwt,issuer,'illari-frontend','expected-nonce',true)).resolves.toHaveProperty('sub','test-sub');
});
it('rechaza nonce diferente incluso durante refresh',async()=>{
 certs();await expect(validarIdToken(await token({nonce:'foreign'}),issuer,'illari-frontend','expected-nonce',true)).rejects.toThrow();
});
