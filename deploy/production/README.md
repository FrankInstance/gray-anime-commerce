# Production configuration

`docker-compose.production.yml` is an application-hardening overlay for the local Compose stack. It deliberately has no credential defaults. Supply every required value from the deployment platform's secret store or process environment.

Required sensitive values:

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `RABBITMQ_PASSWORD`
- `MINIO_ROOT_USER`
- `MINIO_ROOT_PASSWORD`
- `JWT_PRIVATE_KEY_FILE`
- `JWT_PUBLIC_KEY_FILE`
- `AUTH_LOGIN_THROTTLE_SECRET` with at least 32 random characters

Required non-secret deployment identity values:

- `JWT_ISSUER`
- `JWT_AUDIENCE`
- `JWT_KEY_ID`
- `CORS_ALLOWED_ORIGIN_PATTERNS` as one or more exact HTTPS origins
- `PUBLIC_DOMAIN`, for example `qicaoji.icu`
- `LETSENCRYPT_EMAIL`, used only for certificate expiry notices

AI is disabled by default. When it is enabled, also provide:

- `AI_API_KEY_FILE`, pointing to a root-readable file outside the repository
- `AI_BASE_URL`, the exact HTTPS OpenAI-compatible endpoint for the selected Model Studio workspace
- optional `AI_CHAT_MODEL`, `AI_ENABLE_THINKING`, and `AI_EMBEDDING_MODEL` overrides

On a new single-host Ubuntu deployment, generate the key pair and protected environment file directly on the server. Existing secrets are preserved:

```bash
sudo bash deploy/production/provision-secrets.sh demo.example.com
```

The resulting `/etc/gray/gray.env` is readable only by root. Load it immediately before invoking Compose; never copy its contents into the repository.

Build the browser assets with `npm run build`; production builds use the real payment-session boundary. Then validate the resolved deployment before starting it:

```powershell
docker compose -f docker-compose.yml -f docker-compose.production.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --build --wait
```

## Public portfolio demo

The public demo keeps the same deployment secrets, HTTPS cookie, and exact CORS requirements as production, but uses a clearly labelled payment simulation. It never receives real funds.

Build the demo browser assets and add the explicit demo overlay last:

```powershell
npm run build:demo
docker compose -f docker-compose.yml -f docker-compose.production.yml -f docker-compose.demo.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.production.yml -f docker-compose.demo.yml up -d --build --wait
```

For a public single-host deployment, set `PUBLIC_DOMAIN` to a DNS name that resolves to the host and add the public overlay last:

```powershell
docker compose -f docker-compose.yml -f docker-compose.production.yml -f docker-compose.demo.yml -f docker-compose.public.yml up -d --build --wait
```

The public overlay exposes only the frontend Nginx on ports 80 and 443. MySQL, Redis, RabbitMQ, Nacos, MinIO, Qdrant, and the gateway remain internal. Nginx serves both browser applications, proxies `/api`, disables buffering for assistant SSE, replaces client-supplied forwarding headers, and redirects `www` permanently to the primary domain.

## Database migration before deployment

Back up the live database and verify that the archive can be restored before applying the payment-state migration. Do not remove the MySQL volume.

```bash
sudo -u gray bash deploy/operations/mysql-backup.sh
sudo -u gray bash deploy/operations/mysql-restore-verify.sh
sudo bash deploy/production/migrate-payment-state.sh
```

The migration runner copies the UTF-8 SQL into MySQL, applies it through the production Compose configuration, and verifies all nine columns plus both event tables. The migration is idempotent and preserves existing users, orders, and payments. Existing unassigned pending payments become `CREATED`; old outbox rows that do not use the new event envelope are retained as `DEAD` for audit rather than republished.

## First certificate and renewal

Install Certbot on the Ubuntu host, set a real `LETSENCRYPT_EMAIL` in `/etc/gray/gray.env`, and ensure both the root and `www` DNS records point to this host. Start the public stack once; without a certificate, Nginx automatically loads its HTTP challenge configuration.

```bash
sudo apt-get update
sudo apt-get install -y certbot
sudo install -d -m 0755 /opt/gray/certbot/www
docker compose --env-file /etc/gray/gray.env \
  -f docker-compose.yml -f docker-compose.production.yml \
  -f docker-compose.demo.yml -f docker-compose.public.yml \
  up -d --build --wait
sudo bash deploy/production/issue-certificate.sh
sudo bash deploy/production/install-certbot-renewal.sh
sudo certbot renew --dry-run
```

The certificate and private key stay under `/etc/letsencrypt` on the host and are mounted read-only into Nginx. The Certbot systemd timer renews certificates automatically; its deploy hook reloads Nginx gracefully only after a successful renewal.

To inspect the active configuration and certificate:

```bash
docker compose --env-file /etc/gray/gray.env \
  -f docker-compose.yml -f docker-compose.production.yml \
  -f docker-compose.demo.yml -f docker-compose.public.yml \
  exec -T frontend nginx -T
sudo certbot certificates
curl -I https://qicaoji.icu
curl -I https://www.qicaoji.icu
```

If certificate issuance fails, Nginx remains in HTTP bootstrap mode. Check DNS, ports 80/443, `/.well-known/acme-challenge/`, and Certbot logs, then rerun the idempotent issue script. To roll back the application, check out the previous release and rebuild the same Compose project; keep `/etc/letsencrypt` and the database volume in place.

`docker-compose.demo.yml` switches every backend service to the `demo` profile and explicitly enables the Demo payment provider. The application refuses to start if Demo payment is combined with `prod`, `local`, or `test`. The legacy `mock-confirm` endpoint is not registered in a public Demo deployment.

The public gateway applies Redis-backed token-bucket limits to login, registration, password recovery, and payment operations. The production overlay removes the gateway's direct host port, so public traffic reaches it through the frontend reverse proxy and its trusted forwarded address.

## AI assistant overlay

The base stack starts `assistant-service` with AI disabled, so deployments without a provider key remain healthy and the browser hides the assistant entry. To enable it, store the provider key outside the repository with mode `0600`, set `AI_API_KEY_FILE` and `AI_BASE_URL`, and add the AI overlay last:

```powershell
docker compose -f docker-compose.yml -f docker-compose.production.yml -f docker-compose.ai.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.production.yml -f docker-compose.ai.yml up -d --build --wait
```

For a public Demo, place `docker-compose.ai.yml` after `docker-compose.demo.yml` and before optional observability/public overlays. The container entrypoint reads the key from `/run/secrets/gray_ai_api_key` without printing it. Qdrant is only reachable on the internal Compose network in production and public deployments.

The user service also limits failed logins by normalized account identity across IP addresses and service replicas. It stores only an HMAC digest in Redis, starts a 30-second block after five failures, doubles later blocks up to 15 minutes, and clears the state after a successful password check. `AUTH_LOGIN_THROTTLE_SECRET` must be a dedicated random deployment secret and must not reuse a JWT key or database password.

The Demo user service also enables a persistent cleanup schedule. Its first startup records a run six months in the future; a daily 04:00 Asia/Shanghai check executes overdue work even after downtime. A database-backed claim prevents multiple service replicas from running the cleanup together. Cleanup removes non-admin users and their sessions, points records, bookshelf state, carts, orders, and payments, restores inventory affected by their reservations, and schedules the next run six months later. Admin users and catalog/content data are retained.

`PAYMENT_DEMO_ENABLED=true` and `DEMO_CLEANUP_ENABLED=true` are mandatory explicit opt-ins in the Demo overlay. Production rejects both Demo payment and Demo cleanup.

Do not put real values in a tracked `.env` file. This overlay does not replace host firewalling, TLS termination, Nacos hardening, or a managed secret service.

For a new production installation, use a clean database. For an existing deployment, preserve the volume, take and verify a backup, then apply the tracked migrations in order. A database previously started with the `local` profile may contain seeded development accounts; changing profiles does not delete those rows automatically.

## Observability overlay

Add `docker-compose.observability.yml` last to run Prometheus and Grafana beside either production or Demo. `GRAFANA_ADMIN_PASSWORD` is mandatory and must come from the deployment secret store.

```powershell
docker compose -f docker-compose.yml -f docker-compose.production.yml -f docker-compose.observability.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.production.yml -f docker-compose.observability.yml up -d --build --wait
```

For a public Demo, keep `docker-compose.demo.yml` before the observability overlay:

```powershell
docker compose -f docker-compose.yml -f docker-compose.production.yml -f docker-compose.demo.yml -f docker-compose.observability.yml up -d --build --wait
```

Prometheus and Grafana bind to `127.0.0.1` by default. Do not publish them directly to the Internet. Reach them through an SSH tunnel, VPN, or an authenticated reverse proxy. Prometheus evaluates the bundled alert rules locally; external notifications require a separately secured Alertmanager integration.

Every backend exposes `/actuator/prometheus` only on the internal Compose network. Production console logs use Logstash JSON and include MDC fields such as `traceId`; use the matching `X-Trace-Id` response header to follow a request through gateway and service logs.

## Backup and deployment verification

The repository includes a consistent MySQL backup, an isolated restore verification, systemd timer templates, and a write-enabled Demo smoke test. Follow [`../operations/README.md`](../operations/README.md) before publishing the deployment. A backup kept only on the application host is not disaster recovery; copy encrypted backups to independent storage and test restoration regularly.
