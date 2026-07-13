# Authentication keys

Run `npm run setup:auth-keys` once before starting Docker Compose. The generated PEM files are ignored by Git.

These files are for local development only. Production deployments must inject independently managed RSA keys through the configured secret paths. Only `user-service` may receive the private key; gateways and business services receive the public key only.
