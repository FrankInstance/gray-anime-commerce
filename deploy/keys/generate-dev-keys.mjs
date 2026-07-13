import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { generateKeyPairSync } from 'node:crypto';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = dirname(fileURLToPath(import.meta.url));
const privateKeyPath = resolve(directory, 'access-token-private.pem');
const publicKeyPath = resolve(directory, 'access-token-public.pem');

if (existsSync(privateKeyPath) && existsSync(publicKeyPath)) {
  console.log('Authentication keys already exist.');
  process.exit(0);
}

if (existsSync(privateKeyPath) || existsSync(publicKeyPath)) {
  throw new Error('Authentication key pair is incomplete. Remove the remaining PEM file and run this command again.');
}

mkdirSync(directory, { recursive: true });
const { privateKey, publicKey } = generateKeyPairSync('rsa', {
  modulusLength: 3072,
  publicKeyEncoding: { type: 'spki', format: 'pem' },
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
});

writeFileSync(privateKeyPath, privateKey, { encoding: 'utf8', mode: 0o600 });
writeFileSync(publicKeyPath, publicKey, { encoding: 'utf8', mode: 0o644 });
console.log('Generated local RS256 authentication keys.');
