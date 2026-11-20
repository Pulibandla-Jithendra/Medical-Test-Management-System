# MediCore backend

Spring Boot 3.5 / Java 21 backend for the MediCore Medical Test Management System. The application is a modular monolith with REST/JSON APIs, Spring Data JPA, Flyway-managed MySQL schema, Spring Security, JWT access cookies, rotating refresh tokens and protected PDF reports.

The original MTMS report remains the source requirements baseline. Approved architecture decisions add assignment and consent checks, technician assignment by ADMIN, append-only audit history, approved result corrections and report reissue. A request contains one medical test. The application is an academic prototype and must use synthetic records only.

## Prerequisites

- Windows 11 and PowerShell.
- JDK 21 (the build compiles with `--release 21`; use `JAVA_HOME` to select JDK 21).
- MySQL 8.4 on `localhost:3307`, database `medicore_db`, application user `medicore_app`.
- Python launcher (`py`) for the static frontend server.

Maven Wrapper is included. The backend uses a small Hikari pool (maximum three connections) for the 8 GB development machine. Do not run `mvn clean` against a shared `target` folder while another build is using it.

## Inspect the database before first startup

Flyway V1 creates the approved initial schema and does not drop tables. Before the first backend startup or DB-backed test, inspect `medicore_db` using a MySQL administrator or the application user. If it contains existing tables or data, take a backup and compare the schema with `src/main/resources/db/migration/V1__initial_medicore_schema.sql` first. Do not mark or baseline an existing schema without reviewing it. Hibernate is validation-only (`ddl-auto=validate`); it never creates, updates or drops schema.

Read-only inspection in MySQL:

```sql
SELECT TABLE_NAME
FROM information_schema.tables
WHERE table_schema = 'medicore_db'
ORDER BY TABLE_NAME;

SHOW TABLES FROM medicore_db LIKE 'flyway_schema_history';
```

If MySQL is stopped, inspect the Windows service name and start the installed service from an elevated PowerShell window:

```powershell
Get-Service *mysql*
Start-Service MySQL84
Test-NetConnection localhost -Port 3307
```

Use the service name returned on your machine if it is not `MySQL84`. A listening port confirms only that the server is reachable; it does not confirm database credentials or schema compatibility.

The configured MySQL password is not in this repository. `.env.example` documents variable names only; Spring Boot does not load `.env` files automatically.

## Configure and start the backend

Open PowerShell in `backend/medicore`. Keep secrets in the current shell or a protected run configuration; do not paste them into source files or commit a populated `.env` file.

```powershell
$env:MEDICORE_DB_URL = 'jdbc:mysql://localhost:3307/medicore_db?serverTimezone=UTC'
$env:MEDICORE_DB_USERNAME = 'medicore_app'
$secureDbPassword = Read-Host 'MySQL password for medicore_app' -AsSecureString
$env:MEDICORE_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secureDbPassword).Password
$env:MEDICORE_JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:MEDICORE_CORS_ALLOWED_ORIGINS = 'http://localhost:4173'
$env:MEDICORE_COOKIE_SECURE = 'false'
$env:MEDICORE_DEMO_SEED_ENABLED = 'false'
```

Set `JAVA_HOME` to the installed JDK 21 directory if it is not the default, then start the server:

```powershell
$env:JAVA_HOME = 'C:\Path\To\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run
```

The server listens on port 8080. Startup runs Flyway migration/validation and then Hibernate schema validation. A missing MySQL password, schema mismatch or migration error stops startup; do not disable Flyway or change Hibernate validation to work around it.

## Railway deployment

Deploy from `backend/medicore` with the Dockerfile. Railway supplies `PORT` automatically; configure these variables in the Railway service:

- `PORT`: supplied by Railway; do not set a conflicting manual value.
- `MEDICORE_DB_URL`: the managed MySQL JDBC URL for the dedicated application database/user.
- `MEDICORE_DB_USERNAME`: the dedicated application database username.
- `MEDICORE_DB_PASSWORD`: the dedicated application database password.
- `MEDICORE_DB_POOL_SIZE`: optional Hikari pool size.
- `MEDICORE_JWT_SECRET`: a secure Base64 or 32-byte-or-longer secret that remains stable across restarts.
- `MEDICORE_CORS_ALLOWED_ORIGINS`: exact HTTPS Vercel frontend origin(s), comma-separated if multiple.
- `MEDICORE_COOKIE_SECURE=true`: required for HTTPS production cookies.
- `MEDICORE_REPORT_STORAGE_DIR=/app/storage/reports`: persistent PDF storage mount point.
- `MEDICORE_DEMO_SEED_ENABLED=false`: keep synthetic demo seeding disabled.
- `MEDICORE_BOOTSTRAP_ADMIN_ENABLED=false` after the initial administrator is created.

Mount a Railway persistent volume at `/app/storage/reports`. The application stores generated PDFs under that directory in `ReportFileStorage`, and database records retain the PDF filenames. A container redeploy or restart will otherwise lose local report files.

## Production MySQL database

Use Railway's MySQL 8.4 template or a compatible managed MySQL 8 service. Create a dedicated empty
database and a dedicated application user; do not expose or use the root account. Railway's database
service is private by default, so connect the backend with the private host and port whenever both
services are in the same Railway project.

Required Spring environment variables:

| Variable | Purpose |
|---|---|
| `MEDICORE_DB_URL` | JDBC URL using the private MySQL host and application database name |
| `MEDICORE_DB_USERNAME` | Dedicated MediCore application username |
| `MEDICORE_DB_PASSWORD` | Dedicated MediCore application password |
| `MEDICORE_DB_POOL_SIZE` | Optional Hikari pool size; omit or set only when sized deliberately |
| `MEDICORE_REPORT_STORAGE_DIR` | Set to `/app/storage/reports` |
| `MEDICORE_JWT_SECRET` | Stable 32-byte-or-longer secret stored as a Railway secret |
| `MEDICORE_CORS_ALLOWED_ORIGINS` | Exact HTTPS Vercel origin(s), comma-separated |
| `MEDICORE_COOKIE_SECURE` | Set to `true` |
| `MEDICORE_DEMO_SEED_ENABLED` | Set to `false` |

Example JDBC shape without credentials:

```text
jdbc:mysql://<private-mysql-host>:3306/<database-name>?serverTimezone=UTC
```

If Railway exposes MySQL variables to the backend through variable references, map the private host,
port and database variables into `MEDICORE_DB_URL`. Store only the separate generated username and
password in Railway secrets. Do not enable public database access for normal backend operation.

The empty database startup sequence is:

1. Create an empty database and dedicated `medicore_app`-style user through a secure database
   administrator session.
2. Grant the user DML and the migration privileges required for `CREATE TABLE`, `CREATE TRIGGER`,
   foreign keys, indexes and the Flyway schema-history table only on that database.
3. Set the variables above on the backend service.
4. Start the backend once and verify `/actuator/health` or `/api/health`.
5. Confirm one Flyway row is recorded for `1` and that Hibernate validation completes.

Never grant the application user server-wide privileges or use the provisioned database root account.
The dedicated user is expected to include migration privileges during controlled startup; on later
deployments, runtime privileges can be reduced after reviewing future migrations.

### Flyway compatibility findings

`V1__initial_medicore_schema.sql` creates 12 tables and 2 append-only audit triggers. It uses MySQL
8 features already compatible with MySQL 8.4: InnoDB, `utf8mb4_0900_ai_ci`, `DATETIME(6)`, JSON
columns, checks, foreign keys, unique indexes and triggers. The migration does not use `DROP`,
`DROP DATABASE`, or destructive schema cleanup; Flyway cleaning is also disabled in
`application.properties`.

An actual clean-database migration run requires a reachable MySQL 8.4 database. This workspace could
not execute that run because no MySQL instance or database credential is available locally.

### Persistent PDF storage

In Railway, add a volume to the backend service and set:

- Mount path: `/app/storage/reports`
- Environment: `MEDICORE_REPORT_STORAGE_DIR=/app/storage/reports`

Keep the Dockerfile mount and environment in this exact location. `ReportFileStorage`:

- resolves `/app/storage/reports` as an absolute path;
- creates the directory;
- generates a UUID `.pdf` key;
- writes only that key into a strict UUID filename;
- loads bytes for authorized report downloads; and
- deletes a file when a database transaction rolls back.

`ReportService` generates the PDF, writes it, stores `report_file_key` in MySQL and serves it only
through the existing authorization checks. The database and PDF volume must be backed up together:
restoring the database alone cannot reconstruct PDF bytes, and restoring files alone cannot repair
database references.

### Database and PDF backup/recovery

- Enable or schedule the managed MySQL provider's backups. Verify a restorable retention window.
- Before schema changes or migrations, create a logical backup and retain it outside the database.
- Do not download backups to Git or application directories.
- Back up the Railway report volume separately and regularly, with retention and encryption.
- Test recovery quarterly or before a major deployment: restore the database and PDF volume into
  an isolated environment, start only the application, verify Flyway history and execute synthetic
  report generation/download tests.
- Never copy real patient data into backups for this academic prototype.

The JWT secret above is generated for the current development session. Keep it stable while the server is running; changing it invalidates access tokens. Do not print the environment to a terminal transcript.

## Bootstrap the first administrator

The default is disabled. On a fresh schema only, configure a one-time administrator before starting the server:

```powershell
$env:MEDICORE_BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:MEDICORE_BOOTSTRAP_ADMIN_EMAIL = 'admin@medicore.test'
$secureAdminPassword = Read-Host 'One-time administrator password (16+ characters)' -AsSecureString
$env:MEDICORE_BOOTSTRAP_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $secureAdminPassword).Password
```

After the first successful startup creates the account, stop the server with Ctrl+C and remove the one-time values before restarting:

```powershell
Remove-Item Env:MEDICORE_BOOTSTRAP_ADMIN_ENABLED, Env:MEDICORE_BOOTSTRAP_ADMIN_EMAIL, Env:MEDICORE_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
```

Bootstrap refuses to run if an administrator already exists. It is not a public registration endpoint and does not use default credentials.

## Optional synthetic demo records

Demo seeding is disabled by default. After the MySQL schema is reviewed and the bootstrap administrator variables are set for the first launch, optionally enable:

```powershell
$env:MEDICORE_DEMO_SEED_ENABLED = 'true'
$secureDemoPassword = Read-Host 'Synthetic demo account password (12+ characters)' -AsSecureString
$env:MEDICORE_DEMO_PASSWORD = [System.Net.NetworkCredential]::new('', $secureDemoPassword).Password
```

The seed creates only synthetic accounts and records: two patients, one doctor, one laboratory technician, three catalogue entries, two doctor-patient assignments with consent, one requested test and one scheduled test. All four demo accounts use the password entered for `MEDICORE_DEMO_PASSWORD`; the password is hashed and never included in source or logs. Accounts are:

- `patient.one@example.test`
- `patient.two@example.test`
- `doctor.demo@example.test`
- `technician.demo@example.test`

The bootstrap administrator email is the value you configured in `MEDICORE_BOOTSTRAP_ADMIN_EMAIL`. The seeder does not overwrite existing demo accounts. Disable the seeder after its first successful run. Never use real patient information.

## Start the frontend

Open a second PowerShell window at the project root:

```powershell
py -m http.server 4173 --directory frontend
```

Open `http://localhost:4173`. The frontend uses the centralized API base `http://localhost:8080/api/v1` and sends credentialed requests to the backend. Use `localhost` consistently so the CORS origin matches. Override `MEDICORE_CORS_ALLOWED_ORIGINS` when serving the frontend from a different local origin.

## Verify

Public health endpoint:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Expected JSON:

```json
{"status":"UP","application":"MediCore"}
```

Run the automated tests after inspecting the database. When `MEDICORE_DB_PASSWORD` is set, `DatabaseStartupIntegrationTest` starts the real MySQL-backed application context and Flyway; it is not an API-only substitute:

```powershell
.\mvnw.cmd test
```

The database integration test is intentionally skipped when `MEDICORE_DB_PASSWORD` is absent. A skipped test is not evidence that MySQL or Flyway succeeded.

## Implemented API areas

- `/api/v1/auth`: patient registration, login, CSRF token, refresh rotation, logout, logout-all and current account.
- `/api/v1/patients` and `/api/v1/doctors`: own profile, consent/assignment status and authorized doctor patient list.
- `/api/v1/admin`: user management, doctor-patient assignments, request scheduling/rescheduling, correction decisions and audit log.
- `/api/v1/medical-tests`: catalogue read/manage.
- `/api/v1/test-requests` and `/api/v1/lab`: scoped request search, scheduling workflow, result versions and approved corrections.
- `/api/v1/reports`: protected report metadata and PDF download.

Every API mutation requires CSRF protection for cookie-authenticated sessions. Access is rechecked on the backend, including current doctor assignment/consent and technician request assignment. The frontend does not store tokens in local storage.

## Current scope limits

- Local report files use a configurable private directory outside the static frontend. A production private persistent storage adapter, numeric retention policy and backup/restore automation remain deployment work; no public report URLs are generated.
- Real database startup/migration cannot be verified until the correct `MEDICORE_DB_PASSWORD` is supplied and the existing schema is inspected.
- Demo data is synthetic and optional; no production or clinical use is intended.
