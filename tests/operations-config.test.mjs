import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8');

test('Grafana dashboard is valid and covers the operational core', async () => {
  const dashboard = JSON.parse(await read('deploy/observability/grafana/dashboards/gray-commerce-overview.json'));

  assert.equal(dashboard.uid, 'gray-commerce-overview');
  assert.ok(dashboard.panels.length >= 8);
  const titles = new Set(dashboard.panels.map((panel) => panel.title));
  for (const title of [
    'Healthy services / 9',
    '5xx rate',
    'Login throttles / 1h',
    'Payments confirmed / 1h',
    'Request throughput',
    'Request latency p95',
    'Outbox backlog',
    'Dead outbox events'
  ]) {
    assert.ok(titles.has(title), `dashboard must contain ${title}`);
  }
});

test('Prometheus scrapes every application service and defines core alerts', async () => {
  const config = await read('deploy/observability/prometheus/prometheus.yml');
  const alerts = await read('deploy/observability/prometheus/alerts.yml');
  const compose = await read('docker-compose.observability.yml');

  for (const service of [
    'gateway',
    'user-service',
    'content-service',
    'shop-service',
    'inventory-service',
    'order-service',
    'payment-service',
    'ingestion-service',
    'assistant-service'
  ]) {
    assert.match(config, new RegExp(`- ${service}:8080`));
  }
  for (const alert of [
    'GrayServiceDown',
    'GrayHighServerErrorRate',
    'GrayHighRequestLatency',
    'GrayLoginThrottleSpike',
    'GrayPaymentServerErrors',
    'GrayOutboxBacklog',
    'GrayOutboxDeadEvents'
  ]) {
    assert.match(alerts, new RegExp(`alert: ${alert}`));
  }
  assert.doesNotMatch(compose, /web\.enable-lifecycle/);
  assert.equal((compose.match(/no-new-privileges:true/g) ?? []).length, 2);
  assert.equal((compose.match(/cap_drop:/g) ?? []).length, 2);
  assert.equal((compose.match(/read_only: true/g) ?? []).length, 2);
  assert.match(compose, /GF_PLUGINS_PREINSTALL_DISABLED: "true"/);
  assert.match(compose, /GF_PLUGINS_PREINSTALL_AUTO_UPDATE: "false"/);
});

test('operations scripts keep credentials out of command arguments and require explicit smoke opt-in', async () => {
  const backup = await read('deploy/operations/mysql-backup.sh');
  const restore = await read('deploy/operations/mysql-restore-verify.sh');
  const smoke = await read('deploy/operations/demo-smoke.mjs');

  assert.match(backup, /set -Eeuo pipefail/);
  assert.match(backup, /MYSQL_PWD="\$MYSQL_PASSWORD"/);
  assert.doesNotMatch(backup, /--password=/);
  assert.match(restore, /DROP DATABASE IF EXISTS/);
  assert.match(restore, /MYSQL_PWD="\$MYSQL_ROOT_PASSWORD"/);
  assert.match(restore, /Checksum file is missing/);
  assert.match(restore, /CHECK TABLE/);
  assert.doesNotMatch(restore, /--password=/);
  assert.match(smoke, /SMOKE_ALLOW_WRITE/);
  assert.match(smoke, /SMOKE_EXPECT_DEMO_PAYMENT/);
  assert.match(smoke, /SMOKE_ALLOW_HTTP/);
});

test('systemd timers use hardened services without relying on executable file mode', async () => {
  for (const unit of ['gray-mysql-backup.service', 'gray-mysql-restore-verify.service']) {
    const service = await read(`deploy/operations/systemd/${unit}`);
    assert.match(service, /EnvironmentFile=-\/etc\/gray\/operations\.env/);
    assert.match(service, /ExecStart=\/usr\/bin\/bash \/opt\/gray\/deploy\/operations\//);
    assert.match(service, /ProtectSystem=strict/);
    assert.match(service, /ProtectHome=true/);
    assert.match(service, /ReadWritePaths=\/opt\/gray\/backups \/run\/docker\.sock/);
  }
});

test('public deployment exposes only Nginx and supports HTTP bootstrap plus TLS', async () => {
  const compose = await read('docker-compose.public.yml');
  const http = await read('deploy/nginx/public-http.conf.template');
  const tls = await read('deploy/nginx/public-tls.conf.template');
  const app = await read('deploy/nginx/public-app.inc');
  const selector = await read('deploy/nginx/40-gray-public-config.sh');

  assert.equal((compose.match(/ports: !reset \[\]/g) ?? []).length, 7);
  assert.match(compose, /PUBLIC_DOMAIN: \$\{PUBLIC_DOMAIN:\?PUBLIC_DOMAIN must be supplied/);
  assert.match(compose, /- "80:80"/);
  assert.match(compose, /- "443:443"/);
  assert.doesNotMatch(compose, /caddy/i);
  assert.match(http, /\.well-known\/acme-challenge/);
  assert.match(tls, /listen 443 ssl/);
  assert.match(tls, /return 301 https:\/\/\$\{PUBLIC_DOMAIN\}\$request_uri/);
  assert.match(tls, /Strict-Transport-Security/);
  assert.match(app, /proxy_buffering off/);
  assert.match(app, /proxy_set_header X-Forwarded-For \$remote_addr/);
  assert.doesNotMatch(app, /\$proxy_add_x_forwarded_for/);
  assert.match(selector, /fullchain\.pem/);
  assert.match(selector, /nginx -t/);
});

test('certificate scripts use webroot renewal and reload Nginx without storing keys in Git', async () => {
  const issue = await read('deploy/production/issue-certificate.sh');
  const install = await read('deploy/production/install-certbot-renewal.sh');
  const hook = await read('deploy/production/certbot-deploy-hook.sh');

  assert.match(issue, /certbot certonly --webroot/);
  assert.match(issue, /--domain "\$PUBLIC_DOMAIN" --domain "www\.\$PUBLIC_DOMAIN"/);
  assert.match(issue, /\/opt\/gray\/certbot\/www/);
  assert.match(install, /systemctl enable --now certbot\.timer/);
  assert.match(hook, /frontend nginx -s reload/);
});

test('payment migration is additive, repeatable, and preserves existing business rows', async () => {
  const migration = await read('deploy/mysql/migrations/20260719-payment-state-machine.sql');

  assert.match(migration, /SET NAMES utf8mb4/);
  assert.match(migration, /CREATE PROCEDURE gray_add_column_if_missing/);
  assert.match(migration, /FROM information_schema\.columns/);
  assert.match(migration, /CALL gray_add_column_if_missing\('orders', 'fulfillment_status'/);
  assert.match(migration, /UPDATE payment SET status = 'CREATED' WHERE status = 'PENDING' AND channel = 'UNASSIGNED'/);
  assert.match(migration, /WHERE fulfillment_status = 'NOT_REQUIRED'[\s\S]*status IN \('PAID', 'PENDING_PAYMENT'\)/);
  assert.match(migration, /gray_add_index_if_missing/);
  assert.match(migration, /CREATE TABLE IF NOT EXISTS inbox_event/);
  assert.match(migration, /CREATE TABLE IF NOT EXISTS payment_transition/);
  assert.doesNotMatch(migration, /\bTRUNCATE\b|DROP TABLE|DELETE FROM (orders|payment|app_user)/i);
});

test('production secret provisioning is idempotent and keeps values off disk until runtime', async () => {
  const script = await read('deploy/production/provision-secrets.sh');

  assert.match(script, /set -Eeuo pipefail/);
  assert.match(script, /openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072/);
  assert.match(script, /already exists; existing deployment secrets were preserved/);
  assert.match(script, /install -m 0600 "\$TEMP_ENV" "\$ENV_FILE"/);
  assert.doesNotMatch(script, /gray_pass|root_pass|minioadmin/);
});

test('all application services expose Prometheus and request histograms', async () => {
  for (const service of [
    'gateway-service',
    'user-service',
    'content-service',
    'shop-service',
    'inventory-service',
    'order-service',
    'payment-service',
    'ingestion-service',
    'assistant-service'
  ]) {
    const config = await read(`backend/${service}/src/main/resources/application.yml`);
    assert.match(config, /include: health,info,prometheus/);
    assert.match(config, /http\.server\.requests: true/);
  }
});

test('AI deployment keeps the provider key in an explicit secret overlay', async () => {
  const compose = await read('docker-compose.ai.yml');
  const application = await read('backend/assistant-service/src/main/resources/application.yml');

  assert.match(compose, /AI_API_KEY_FILE: \/run\/secrets\/gray_ai_api_key/);
  assert.match(compose, /AI_API_KEY_FILE must point to a deployment-managed secret file/);
  assert.doesNotMatch(compose, /sk-[A-Za-z0-9]{8,}/);
  assert.match(application, /enabled: \$\{AI_ENABLED:false\}/);
  assert.match(application, /api-key: \$\{AI_API_KEY:disabled\}/);
});
