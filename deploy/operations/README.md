# Operations runbook

This runbook covers monitoring, MySQL recovery checks, and public Demo acceptance. Commands assume the repository is checked out at `/opt/gray` on a Linux server unless noted otherwise.

## Monitoring

Set a Grafana administrator password through the deployment platform's Secret store, then add the observability overlay last:

```bash
export GRAFANA_ADMIN_PASSWORD='<secret>'
docker compose \
  -f docker-compose.yml \
  -f docker-compose.production.yml \
  -f docker-compose.demo.yml \
  -f docker-compose.observability.yml \
  up -d --build --wait
```

Prometheus and Grafana bind only to loopback by default:

- Prometheus: `http://127.0.0.1:9090`
- Grafana: `http://127.0.0.1:3000`

Use an SSH tunnel instead of opening either port publicly:

```bash
ssh -L 3000:127.0.0.1:3000 -L 9090:127.0.0.1:9090 deploy@example.com
```

The provisioned `Gray Commerce Overview` dashboard shows service availability, traffic, latency, server errors, login throttling, confirmed payments, and JVM heap use. Prometheus evaluates the bundled rules, but sending notifications requires a separately secured Alertmanager integration.

## Request tracing

The gateway accepts a safe hexadecimal `X-Trace-Id` or creates one, returns it to the caller, and passes it to downstream services. Invalid or log-injection-shaped values are replaced. Local logs include `traceId` in the level prefix; production logs are structured Logstash JSON and include MDC fields.

For a failed request, copy its response `X-Trace-Id` and search the Compose logs:

```bash
docker compose logs --since 30m gateway user-service order-service payment-service | grep '<trace-id>'
```

## MySQL backup

Run a manual backup from the repository root:

```bash
BACKUP_DIR=/var/backups/gray/mysql RETENTION_DAYS=14 \
  bash deploy/operations/mysql-backup.sh
```

The script uses a consistent single transaction, writes a hidden partial file first, validates gzip, atomically publishes the archive, and writes a SHA-256 sidecar. Database passwords stay in the MySQL container environment and are not passed as command-line arguments.

Copy completed archives and checksums to encrypted storage outside the ECS instance. Local retention protects disk space; it does not replace off-host backup or provider snapshots.

## Restore verification

Verify the newest backup without replacing the live database:

```bash
BACKUP_DIR=/var/backups/gray/mysql \
  bash deploy/operations/mysql-restore-verify.sh
```

The script imports into a randomly named temporary database, compares table counts, checks core tables, then removes the temporary database. To verify a specific archive, pass its path as the first argument.

Install the included timers after reviewing the service account and paths:

```bash
sudo install -d -o gray -g gray -m 0700 /opt/gray/backups/mysql
sudo install -d -m 0750 /etc/gray
sudo install -m 0644 deploy/operations/systemd/gray-mysql-*.service /etc/systemd/system/
sudo install -m 0644 deploy/operations/systemd/gray-mysql-*.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now gray-mysql-backup.timer gray-mysql-restore-verify.timer
systemctl list-timers 'gray-mysql-*'
```

The default schedule creates a backup daily at 03:30 and performs an isolated restore check every Sunday at 04:30. Both timers are persistent, so a missed run is performed after the host starts again. The optional `/etc/gray/operations.env` can set `RETENTION_DAYS`; do not store database credentials there. If `BACKUP_DIR` is moved outside `/opt/gray/backups`, update the services' `ReadWritePaths` hardening rule to the same directory.

## Demo deployment smoke test

Run this only against a Demo deployment. It creates a disposable user and a 10 RMB simulated points order, rotates the refresh token, signs out and back in, confirms the Demo payment, and verifies the resulting points balance.

```bash
SMOKE_BASE_URL=https://demo.example.com \
SMOKE_ALLOW_WRITE=true \
SMOKE_EXPECT_DEMO_PAYMENT=true \
npm run smoke:demo
```

The command requires HTTPS by default and refuses to write unless both safety acknowledgements are explicit. For local verification only, add `SMOKE_ALLOW_HTTP=true` and use `http://127.0.0.1`.

## Routine checks

```bash
npm run test:operations
docker compose -f docker-compose.yml -f docker-compose.observability.yml config --quiet
docker compose ps
docker compose exec -T prometheus promtool check config /etc/prometheus/prometheus.yml
```

After deployment, confirm all Prometheus targets are up, Grafana can query the provisioned data source, the latest backup checksum passes, and the most recent restore verification completed successfully.
