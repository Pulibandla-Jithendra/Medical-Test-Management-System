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
