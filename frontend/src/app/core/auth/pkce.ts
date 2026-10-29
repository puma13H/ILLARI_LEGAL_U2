function base64Url(bytes: Uint8Array): string {
  let text = '';
  for (const byte of bytes) text += String.fromCharCode(byte);
  return btoa(text).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

export function codeVerifier(): string {
  return base64Url(crypto.getRandomValues(new Uint8Array(32)));
}

export function stateAleatorio(): string {
  return base64Url(crypto.getRandomValues(new Uint8Array(16)));
}

export async function codeChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}
