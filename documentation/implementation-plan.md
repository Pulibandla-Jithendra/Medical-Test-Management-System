# MediCore Implementation Plan

## Status and baseline

Planning only. No application source code, frontend or backend implementation has been created. The original MTMS report remains the requirements baseline and is not to be modified. The project owner has approved the decisions in this plan; implementation must wait for final confirmation.

## Architecture summary

Use a three-layer modular monolith sized for Windows 11 with 8 GB RAM:

1. **Frontend:** HTML, CSS and Vanilla JavaScript with role-specific views, same-origin JSON API, CSRF helper and no browser token persistence.
2. **Backend:** Java 21, Spring Boot 3.5.x, Maven, Spring MVC, Spring Security/JWT, DTO validation, role/resource authorization and Spring Data JPA/Hibernate.
3. **Persistence and files:** MySQL 8.4 stores the report’s seven core entities plus assignment/consent, refresh sessions, correction requests and audit logs. Development PDFs use a protected configurable local directory. Production PDFs use private persistent storage; MySQL stores metadata/version links.

Keep one Spring Boot process and one MySQL instance. Transactions cover state, result versions, audit events and report metadata. Files are accessed only through an authorization-checked backend.

## Approved authorization design

- A DOCTOR receives access only when a non-revoked admin-created assignment episode and an active patient-recorded consent both exist.
- Consent is tied to one assignment episode. Revoking consent or assignment immediately denies current and historical doctor report/data access. A new assignment requires fresh consent.
- PATIENT accesses their own records. ADMIN retains authorized administrative access to preserved records after revocation.
- LAB_TECHNICIAN sees/processes only requests assigned to their account. Only that technician may enter results or generate/reissue the report.
- ADMIN alone grants/revokes assignments, schedules/assigns, reschedules before IN_PROGRESS, manages roles and approves corrections to completed results.
- Every protected resource operation checks the current principal, assignment/consent, ownership and/or assigned technician in backend queries/services. Frontend hiding is not security.

## Approved authentication and bootstrap design

- Access JWT lifetime is exactly 15 minutes. Refresh token lifetime is 30 days absolute from the login family.
- JWT access cookie and opaque refresh cookie are HttpOnly, SameSite=Lax and Secure in HTTPS deployment; CSRF applies to cookie-authenticated mutations. Tokens are never stored in localStorage/sessionStorage.
- Store only high-entropy refresh-token hashes in MySQL. Rotate atomically on every successful refresh, detect replay, revoke the token family and audit the event. Logout revokes its family; logout-all and role/account changes revoke all affected refresh sessions. Check family status and USER.security_version on protected requests for immediate revocation.
- Hash passwords with Spring Security PasswordEncoder; never store plaintext.
- Create the first ADMIN using a one-time protected setup command reading protected environment variables or secure input, only if no ADMIN exists. Hash immediately, emit no secret in logs, fail closed on repeat and remove the bootstrap secret/disable setup after success. No hardcoded default credentials and no public bootstrap API.

## Results, corrections and reports

- Initial result entry by the assigned technician occurs while IN_PROGRESS and moves the request to COMPLETED.
- Any correction after completion requires an ADMIN-approved correction request, whether or not a PDF exists. The assigned technician applies the approved correction by inserting a new result version containing actor, timestamp and reason; keep the original version.
- If an issued report is affected, retain it as SUPERSEDED and generate a new PDF version from the corrected result. Keep both files and metadata. Request status does not move backward.
- PDF files are stored behind a storage adapter. Local development uses a protected directory outside the web root; production uses private persistent storage. MySQL stores opaque keys and metadata.
- Configure report retention and backup policies; verify restoration of MySQL metadata and matching PDF files. Never put real patient information in the demo.

## Module responsibilities

| Module | Responsibility |
|---|---|
| common/config | Configuration, DTO conventions, validation, safe errors and correlation IDs |
| auth/security | Password hashing, JWT, refresh rotation/reuse/revocation, CSRF, security_version and role policy |
| user/admin | User provisioning, roles, deactivation and profile administration |
| doctor-patient assignment/consent | Admin assignment episodes, patient consent/revocation, active-access checks |
| medical-test | Catalogue read/write and deactivation |
| test-request | Doctor request creation, scope checks and lifecycle transitions |
| scheduling/lab | Admin schedule/assignment/reschedule, assigned-only work queue and start action |
| result/correction | Immutable versions, correction requests, ADMIN decisions and technician application |
| report/storage | PDF generation, report versions, superseded history, local/private persistent storage adapter and authorized downloads |
| audit | Append-only AUDIT_LOG write/read paths and event coverage |

## Frontend page structure

- Common: login, patient registration, CSRF/auth/API helpers.
- Patient: dashboard, profile, available tests, own request/status list, doctor-assignment/consent list, grant/withdraw consent and report history.
- Doctor: active authorized-patient list, request creation for one active test, request/status/results/report history only for active assignment plus consent.
- Technician: assigned-only queue, process control, initial result entry, correction request/application and PDF generation/reissue.
- Admin: users/roles, doctor-patient assignments/revocations, catalogue, schedule/technician assignment, pre-processing reschedule, correction approvals, reports, monitoring and audit search.

## Proposed repository structure

- MediCore/
  - documentation/ (source report and five planning documents)
  - frontend/
    - pages/ (login, register, patient, doctor, technician, admin, report)
    - assets/css/
    - assets/js/api/
    - assets/js/auth/
    - assets/js/modules/ (profile, assignment, consent, catalogue, request, scheduling, result, correction, report, admin, audit)
  - backend/
    - pom.xml and Maven wrapper
    - src/main/java/.../config, security, auth, user, patient, doctor
    - src/main/java/.../assignment, consent, medicaltest, testrequest, scheduling, lab
    - src/main/java/.../result, correction, report, storage, audit, admin, common
    - src/main/resources/application.yml
    - src/main/resources/db/migration/ (Flyway is proposed)
    - src/test/java/... (planned JUnit verification)
  - .gitignore, .env.example, README.md

Keep Flyway as a proposed migration tool pending adoption. The report directory is configured outside static resources. Keep credentials, report files and all real patient data out of Git. Avoid Docker, microservices, Redis and search clusters on the 8 GB laptop; use Maven Wrapper, one backend process and local MySQL.

## Development roadmap

The report proposes an indicative 12-week schedule and four two-week sprints; dates depend on the academic calendar.

| Phase | Timing | Planned output |
|---|---|---|
| 0. Final confirmation | Before coding | Confirm the approved architecture and remaining deployment parameters; retain source PDF unchanged. |
| 1. Foundation/schema | Week 1 | Git workflow, Maven skeleton, API conventions, initial-admin setup design and migrations for core plus assignment/consent, refresh and audit tables. |
| 2. Authentication/identity/access | Weeks 2-3 | Patient registration, password hashing, 15-minute JWT/30-day refresh rotation, logout/revocation, bootstrap ADMIN, roles, profiles, assignment and patient-consent flows. |
| 3. Catalogue/requests | Weeks 4-5 | Catalogue, doctor’s authorized-and-consenting patient list, one-test request creation, scoped request lists. |
| 4. Scheduling/lab workflow | Weeks 6-7 | ADMIN schedule/assignment/reschedule, audit snapshots, assigned-only technician queue and status changes. |
| 5. Results/reports | Weeks 8-9 | Initial results, PDF generation, private local file storage, report metadata/history and protected downloads. |
| 6. Corrections/admin | Week 10 | Admin-approved completed-result corrections, immutable versions, superseded report/new PDF reissue, audit viewer. |
| 7. Verification/delivery | Weeks 11-12 | Planned JUnit/Postman/privacy and integration cases, synthetic demo data, configurable backup/retention guide and presentation. |

### Dependency order

1. Freeze entity/API names, consent text/version handling, role rules and request lifecycle.
2. Implement schema/migrations for user security, assignment/consent, refresh token and audit records.
3. Build one-time ADMIN bootstrap and authentication before protected administration.
4. Build assignment and patient consent before doctor patient access/request creation.
5. Build doctor requests, then ADMIN schedule/assignment/reschedule and assigned technician queue.
6. Build initial result version and completed result correction approval before report history/reissue.
7. Add private storage adapter, protected download, audit searches and admin screens.
8. Integrate role views and verify access immediately after consent/assignment revocation using synthetic data only.

### Planned verification (not yet performed)

After coding, cover role/ownership, assignment plus consent required, access revocation for historical reports, assigned-technician isolation, admin-only schedule/reschedule, audit old/new schedule values, lifecycle transitions, result version/approval/reason/approver, report supersession and reissue, JWT expiry, refresh rotation/reuse family revocation, bootstrap repeat rejection, PDF authorization and backup/restore. Follow the report’s equivalence partitioning and integration-test approach.

## Remaining deployment parameters

The architecture decisions are approved. Before deployment, configure the patient-consent wording/version, report storage provider, report/audit retention and backup values, PDF template and secure environment-variable handling/removal for bootstrap. Confirm whether a standalone PDF-only correction (with no result change) needs a separate approval/reissue flow; the approved baseline covers reissue caused by an approved completed-result correction.

