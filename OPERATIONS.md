# Proxima production operations

The production Docker stack keeps application data private. Only Caddy exposes
ports `80` and `443` publicly. PostgreSQL, Redis, Kafka, Elasticsearch,
Prometheus, and Spring Actuator are never public.

The EC2 host is administered only through AWS Systems Manager (SSM). Do not
create an SSH key pair or open port `22`; the security group allows only public
HTTP/HTTPS traffic.

## Before launching an instance

Use the intended production baseline in Mumbai (`ap-south-1`): Ubuntu 24.04
amd64, an encrypted 80 GiB gp3 volume, the `jobz-prod-web` security group,
and an instance profile with `AmazonSSMManagedInstanceCore`. The instance must
have outbound HTTPS access for SSM, Docker image pulls, OAuth, scrapers, and
certificate issuance.

Before the first `docker compose up`:

1. Associate a stable public address with the instance and point the final DNS
   `A` record at it.
2. Add `https://YOUR_DOMAIN` as the Google Authorized JavaScript origin.
3. Add `https://YOUR_DOMAIN/api/v1/auth/github/callback` as the GitHub OAuth
   callback URL.
4. Confirm inbound TCP `80` and `443` are allowed. The Caddy configuration also
   maps UDP `443` for HTTP/3; keep it allowed if HTTP/3 is desired.

Caddy cannot issue a trusted certificate for an IP address, so do not start the
production stack before DNS points to the host.

## Host bootstrap and SSM access

Preferred: before launching the instance, paste the complete contents of
`scripts/bootstrap-ubuntu-host.sh` into **Advanced details → User data** in the
EC2 launch form. It contains no application secrets and runs as root during the
first boot.

If the instance is already running, run the same script through an already
working Session Manager shell instead:

```bash
sudo bash /opt/jobz/job-portal/scripts/bootstrap-ubuntu-host.sh
```

The script installs Docker Engine and the Compose plugin, persists the required
Elasticsearch `vm.max_map_count=262144` setting, enables Docker and the SSM
agent, disables the unused SSH service, and creates protected deployment and
backup directories. It does **not** clone code, create secrets, open ports, or
start application containers.

After bootstrap, clone the backend and frontend as root-owned sibling folders
so the production Compose file can build the frontend without custom path
changes:

```text
/opt/jobz/job-portal
/opt/jobz/proxima-frontend
```

```bash
sudo git clone BACKEND_REPOSITORY_URL /opt/jobz/job-portal
sudo git clone FRONTEND_REPOSITORY_URL /opt/jobz/proxima-frontend
```

For private repositories, use a read-only deployment credential or a CI/CD
artifact workflow. Do not paste a personal GitHub token into shell commands or
commit any deployment credential.

Use the AWS Console's Session Manager for the initial host session. After AWS
CLI SSO is configured locally, a terminal session can be started with:

```bash
aws ssm start-session \
  --target i-YOUR_INSTANCE_ID \
  --region ap-south-1 \
  --profile YOUR_SSO_PROFILE
```

Run host administration with `sudo`. Docker group membership is intentionally
not granted to human users because it effectively provides root-level access.

## First production deployment

From `/opt/jobz/job-portal`:

```bash
sudo cp .env.production.example .env.production
sudo chmod 600 .env.production
sudoedit .env.production
```

Populate the final domain, production-only database/JWT/API/OAuth values, and
the exact deployed commit as `APP_VERSION`. Never copy a local `.env` file to
the host and never print `.env.production` into a terminal, log, or ticket.

Validate the configuration without displaying its values, then start the stack:

```bash
sudo docker compose --env-file .env.production -f docker-compose.production.yml config --quiet
sudo docker compose --env-file .env.production -f docker-compose.production.yml up -d --build --wait --remove-orphans
```

## Release checks

After a deployment, run the following from the backend repository in an SSM
shell:

```bash
sudo docker compose --env-file .env.production -f docker-compose.production.yml ps
sudo docker compose --env-file .env.production -f docker-compose.production.yml logs --tail=200 backend
curl -fsS https://YOUR_DOMAIN/api/v1/health
```

The public health endpoint returns only aggregate status. To inspect the
internal component health from the server:

```bash
sudo docker compose --env-file .env.production -f docker-compose.production.yml exec backend \
  curl -fsS http://localhost:8080/actuator/health
```

Test a real Google and GitHub sign-in after HTTPS is working. The local smoke
test deliberately cannot validate OAuth redirects or secure cookies.

## Fresh-database verification

Before the first production deployment and before any release that changes a
Liquibase migration, verify a blank database locally:

```bash
bash scripts/verify-fresh-production-db.sh
```

The script uses a separate Docker Compose project and removes only the volumes
it created when it exits. It never reads the local `.env`, reuses existing
development volumes, starts a scraper or data seeder, makes live metadata
requests, or contacts AWS.

## Full production-stack verification

Before a release, verify the browser-facing production routing as well:

```bash
bash scripts/verify-full-production-stack.sh
```

This builds the frontend and backend images, starts an isolated stack with
dummy credentials, disables scrapers and metadata seeding, and checks the
homepage plus both Caddy API route forms. It binds only `127.0.0.1:18080` by
default and removes its own containers, volumes, and locally built test images
when it finishes. It deliberately uses local HTTP, so OAuth and secure-cookie
round trips must still be tested after the final HTTPS domain is configured.

## Metrics

Prometheus collects JVM, HTTP, database/cache health, and scraper metrics every
15 seconds. It binds only to `127.0.0.1:9090`. Open its private UI from your own
computer through an SSM port-forward, never through the public internet:

```bash
aws ssm start-session \
  --target i-YOUR_INSTANCE_ID \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["9090"],"localPortNumber":["9090"]}' \
  --region ap-south-1 \
  --profile YOUR_SSO_PROFILE
```

Then visit `http://localhost:9090`. Useful initial queries are:

```promql
up{job="jobz-backend"}
rate(http_server_requests_seconds_count[5m])
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))
sum by (outcome) (jobz_scraper_runs_total)
sum by (scraper, result) (jobz_scraper_jobs_total)
```

Do not expose `/actuator` or port `9090` through Caddy. For external outage
alerts, configure any uptime monitor to request
`https://YOUR_DOMAIN/api/v1/health` every five minutes and alert on a non-200
response.

## Backups and recovery

Do not accept production traffic until both of these are configured and a
restore has been tested:

1. A daily encrypted PostgreSQL backup stored **outside** the EC2 volume, with
   retention (for example, a compressed `pg_dump` uploaded to a private S3
   bucket using a least-privilege instance role).
2. Scheduled encrypted EBS snapshots for the host volume.

`/var/backups/jobz` is only a protected staging directory; files stored only
there are not backups. Do not run `docker compose down -v` in production: it
would delete Docker-managed PostgreSQL and other persistent volumes.

Before a release containing a Liquibase migration, take a database backup.
Database migrations are not automatically reversible; rolling back code after a
migration may require a forward fix or a tested database restore.

### What is backed up

PostgreSQL is the application source of truth: user accounts, saved/tracked/
applied jobs, feedback, companies, scraper configuration and runs, job data,
and outbox records. The scheduled logical backup protects this data.

Redis is a cache and distributed-lock store; Elasticsearch is rebuilt from
PostgreSQL through the existing reindex action; Kafka/ZooKeeper carry
asynchronous work; and Prometheus contains operational history. Do **not** try
to restore any of those stores independently from a PostgreSQL logical backup.
After a database-only recovery, start them empty, reindex Elasticsearch, and
rerun any scrapers or enrichment work that was in flight.

### S3 database backup

Create one private general-purpose S3 bucket in `ap-south-1`, with a globally
unique name such as `jobz-prod-backups-ACCOUNT_ID-ap-south-1`. Configure:

1. **Block all public access**, **ACLs disabled / bucket owner enforced**, and
   **versioning enabled**.
2. Default encryption with **SSE-S3**. The backup script explicitly sends the
   same `AES256` encryption mode for each upload.
3. A lifecycle rule limited to the `postgres/` prefix: expire current versions
   after 35 days, permanently delete noncurrent versions after 7 days, delete
   expired delete markers, and abort incomplete multipart uploads after 1 day.
   Do not move the first small production backup set to Glacier; fast recovery
   is more valuable than a marginal storage saving.
4. The TLS-only and encrypted-upload bucket policy in
   `deploy/aws/jobz-backup-bucket-policy.json`, after replacing every
   `REPLACE_WITH_BACKUP_BUCKET_NAME` placeholder with the real bucket name.

Add the least-privilege policy in
`deploy/aws/jobz-backup-writer-policy.json` to the **existing** EC2 instance
role (`jobz-prod-ec2-ssm`) after replacing its bucket-name placeholders. It
permits uploads only under `postgres/`; it intentionally cannot read, delete,
or list backup objects. The host authenticates with its short-lived EC2 role
credentials, never static AWS keys.

After versioning is first enabled, wait about 15 minutes before the first
upload because S3 documents a short propagation window.

Install the backup timer after the application has been cloned to
`/opt/jobz/job-portal` and the production stack is healthy:

```bash
sudo install -m 0600 deploy/backup.env.example /etc/jobz/backup.env
sudoedit /etc/jobz/backup.env
sudo install -m 0644 deploy/systemd/jobz-postgres-backup.service /etc/systemd/system/jobz-postgres-backup.service
sudo install -m 0644 deploy/systemd/jobz-postgres-backup.timer /etc/systemd/system/jobz-postgres-backup.timer
sudo systemctl daemon-reload
sudo systemctl enable --now jobz-postgres-backup.timer
sudo systemctl start jobz-postgres-backup.service
sudo systemctl status jobz-postgres-backup.service --no-pager
sudo systemctl list-timers jobz-postgres-backup.timer
```

The timer runs once daily at 03:17 UTC plus up to 15 minutes of deliberate
randomization, outside the application's six-hour scraper cadence. It uses
`pg_dump` custom format, validates the archive with `pg_restore --list`, writes
a SHA-256 sidecar, uploads both files using SSE-S3, and deletes the local
staging copies. Review its most recent run through Session Manager:

```bash
sudo journalctl -u jobz-postgres-backup.service --since today --no-pager
```

### Host-volume snapshots

Create a separate EC2 Data Lifecycle Manager EBS snapshot policy in
`ap-south-1` after the instance exists. Target the instance tag
`Backup=jobz-prod`, run daily after the database-dump window, retain seven
snapshots, and copy the source tags. The root EBS volume must show
**Encrypted: Yes**. These snapshots are a crash-consistent whole-host fallback
for Docker volumes and Caddy state; they do not replace the application-
consistent PostgreSQL archive.

### Restore rehearsal

Before launch, and monthly after launch, perform an isolated restore rehearsal
from a newly uploaded archive:

1. Using an administrator session (not the application's upload-only EC2
   role), download one `.dump` and its `.sha256` sidecar from S3 and verify the
   checksum with `sha256sum -c`.
2. Start an isolated Postgres 16 container or a separate Compose project with
   a fresh volume; never target the live `postgres` service.
3. Restore the archive with `pg_restore --exit-on-error --no-owner
   --no-privileges`, then verify `databasechangelog`, `app_users`, `jobs`,
   `companies`, and `feedback_submissions` counts.
4. Remove the isolated test container and volume. For a genuine recovery,
   stop backend writes first, restore PostgreSQL before starting the backend,
   start Redis/Kafka/Elasticsearch empty, run the authenticated reindex action,
   and rerun any in-flight scraper work.

Keep production environment values such as the JWT secret and OAuth client
secret in an encrypted password manager or AWS Parameter Store. They are not
safe to place in the database-backup bucket and PostgreSQL dumps do not
recreate database roles/passwords.

## Rollback

Keep the previous verified Git commit available. If a release has not changed
the database incompatibly, check out that commit, update `APP_VERSION` in the
host-only `.env.production`, and rebuild the stack:

```bash
git checkout PREVIOUS_VERIFIED_COMMIT
sudo docker compose --env-file .env.production -f docker-compose.production.yml up -d --build --wait --remove-orphans
```

If the release included a database migration, stop and evaluate the migration
before rolling back. Restore only from a tested backup and do not delete volumes
to attempt recovery.

## Logs and incident correlation

Production container logs are structured key/value lines and include
`request_id`, HTTP method, path, status, and duration. The same request ID is
returned to callers in the `X-Request-Id` header and in API error responses.

```bash
sudo docker compose --env-file .env.production -f docker-compose.production.yml logs -f backend
sudo docker compose --env-file .env.production -f docker-compose.production.yml logs --tail=200 caddy
sudo docker stats
```

Docker retains three local log files of up to 10 MB per service. Use the
request ID from a browser/API response when reporting an error; never include
access tokens, OAuth callback URLs, or passwords in support messages.
