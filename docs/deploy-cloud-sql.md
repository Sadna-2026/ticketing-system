# Deploying the remote database (Cloud SQL for PostgreSQL) — V3-29

V3 (Requirement 7) mandates a **remote** database — *"support a remote database (not on the same machine
where the system runs)"* — with the connection details in **configuration, never hard-coded**, and the
local↔cloud switch being **config-only (no code change)**. This project already satisfies the application
side (V3-12): every datasource setting is an environment variable with H2 defaults (see
[README "Database configuration"](../README.md)). This runbook covers the remaining **operational** steps —
provisioning the instance and setting a budget alert — which must run in *your* Google Cloud account.

The instance spec follows the V3 document's recommended cheapest-viable configuration.

| Setting | Value | Why |
|---|---|---|
| Engine | PostgreSQL | matches our JPA/Hibernate dialect + driver |
| Tier | `db-f1-micro` (shared core, 0.6 GB) | cheapest tier (per spec) |
| Storage | **HDD**, 10 GB | cheaper than SSD; minimum size (per spec) |
| Region | `us-east1` | spec's recommended cost-effective region (`us-west1` also fine) |
| Backups / HA | **disabled** | minimise cost for a low-load academic project (per spec) |
| Connectivity | public IP + authorized networks | simplest path the spec allows (it does not mandate the Auth Proxy) |

> **Connectivity choice.** The spec does not require the Cloud SQL Auth Proxy, and explicitly says not to
> over-engineer. Public IP + an authorized-network allowlist is the simplest option that keeps the instance
> non-public and satisfies "reachable via config". If you prefer Google's more-secure default, the
> [Cloud SQL Auth Proxy](https://cloud.google.com/sql/docs/postgres/connect-auth-proxy) is a drop-in
> alternative — only the `DB_URL` changes (point it at `127.0.0.1:5432` with the proxy running).

## Prerequisites (you)
- `gcloud` CLI installed and authenticated: `gcloud auth login`.
- A GCP **project** with **billing enabled** (the $50 education credit applies).
- You'll choose a **DB password** below — keep it out of git (it goes in your shell / deployment secrets only).

```bash
# Fill these in:
export PROJECT_ID="your-gcp-project-id"
export BILLING_ACCOUNT_ID="XXXXXX-XXXXXX-XXXXXX"   # gcloud billing accounts list
export REGION="us-east1"
export INSTANCE="ticketing-db"
export DB_NAME="ticketing"
export DB_USER="ticketing"
export DB_PASSWORD="choose-a-strong-password"      # do NOT commit this

gcloud config set project "$PROJECT_ID"
gcloud services enable sqladmin.googleapis.com cloudbilling.googleapis.com billingbudgets.googleapis.com
```

## 1. Create the cheapest-tier instance
```bash
gcloud sql instances create "$INSTANCE" \
  --database-version=POSTGRES_16 \
  --tier=db-f1-micro \
  --region="$REGION" \
  --storage-type=HDD \
  --storage-size=10GB \
  --no-backup \
  --availability-type=zonal          # zonal = no High Availability
```

## 2. Create the database and an application user
```bash
gcloud sql databases create "$DB_NAME" --instance="$INSTANCE"
gcloud sql users create "$DB_USER" --instance="$INSTANCE" --password="$DB_PASSWORD"
```

## 3. Allow your machine / CI to connect (public IP + authorized network)
```bash
# Whitelist your current public IP (re-run if your IP changes; add CI egress IPs the same way):
MY_IP="$(curl -s ifconfig.me)"
gcloud sql instances patch "$INSTANCE" --authorized-networks="${MY_IP}/32"

# (Recommended) require TLS:
gcloud sql instances patch "$INSTANCE" --ssl-mode=ENCRYPTED_ONLY

# Read back the instance's public IP — you'll put it in DB_URL:
gcloud sql instances describe "$INSTANCE" --format='value(ipAddresses[0].ipAddress)'
```

## 4. Set a budget + alerts (stay inside the $50 credit)
```bash
gcloud billing budgets create \
  --billing-account="$BILLING_ACCOUNT_ID" \
  --display-name="Ticketing Cloud SQL budget" \
  --budget-amount=15USD \
  --threshold-rule=percent=0.5 \
  --threshold-rule=percent=0.9 \
  --threshold-rule=percent=1.0
```
If the CLI command isn't available in your environment, set the same budget + 50/90/100% alerts via
**Billing → Budgets & alerts** in the Cloud Console.

## 5. Point the app at the remote DB (config only — no code change)
Set these before launching the app (substitute the public IP from step 3):
```bash
export TICKETING_PERSISTENCE=jpa
export DB_URL="jdbc:postgresql://<PUBLIC_IP>:5432/ticketing"
export DB_DRIVER=org.postgresql.Driver
export DB_USERNAME=ticketing
export DB_PASSWORD="$DB_PASSWORD"
export DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect

# First launch only — create the schema from the entities, then stop:
export DB_DDL_AUTO=update
mvn spring-boot:run
# After the schema exists, switch to validation for normal runs:
export DB_DDL_AUTO=validate
```

The app's `ExternalSystemsHandshakeRunner`, `DevSeedDataInitializer` and (optionally)
`InitialStateRunner` then bring the platform up against the remote DB exactly as documented in
[docs/use-cases-initialization.md](use-cases-initialization.md).

## 6. Turn it off when idle (preserve credits — per spec)
```bash
gcloud sql instances patch "$INSTANCE" --activation-policy=NEVER   # stop
gcloud sql instances patch "$INSTANCE" --activation-policy=ALWAYS  # start again
```

## Notes
- **Tests never touch the remote DB:** the suite defaults to `ticketing.persistence=memory` and the JPA
  round-trip tests use embedded H2 (`@DataJpaTest`), so `mvn test` stays offline and never pollutes the
  cloud instance (V3 testing requirement).
- **Never commit credentials.** `DB_PASSWORD` lives only in your shell / deployment secrets, not in
  `application.yml` or git.
