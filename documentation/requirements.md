# MediCore (MTMS) Requirements

## Source of truth and planning status

The unchanged source of truth is the original MTMS project report at documentation/Medical_Test_Management_System_SE_Project_Report.pdf. The task used documentation/reference/MTMS_Project_Report.pdf, which is not a file in this workspace; the 16-page report already reviewed during the initial planning phase is retained without modification.

This document keeps the original medical-test workflow and makes the decisions below explicit. **Approved implementation decision** identifies a decision confirmed by the project owner. The plan remains documentation-only: no application source code, frontend or backend implementation has been started.

## Purpose and boundary

MediCore is a Medical Test Management System for patient and doctor profiles, the medical-test catalogue, one-test-per-request doctor requests, scheduling, technician assignment, laboratory processing, result history, PDF reports and authorized access.

The report excludes hospital-system integration, diagnostic-instrument interfaces, insurance, payments, telemedicine and automated diagnosis. The system records and presents results; it does not provide clinical interpretation. Demonstrations use synthetic data only; do not store real patient information in demo environments.

## Approved implementation decisions

1. **Assignment and consent:** A doctor needs both an active administrator-created patient assignment and recorded patient consent before reading protected patient data, requests, results or reports, or creating a request.
2. **Revocation:** Revoking either the assignment or patient consent immediately blocks further doctor access to current and historical reports. Medical records remain stored and available to authorized administrators.
3. **Corrections:** Every correction to a completed result requires administrator approval. Preserve the original and corrected result, the assigned technician who entered the correction, the approving administrator, timestamps and reason. If a report already exists, issue a new report version and preserve the earlier PDF as superseded.
4. **Authentication:** Spring Security with 15-minute JWT access tokens and 30-day refresh tokens. Rotate refresh tokens securely, detect reuse and revoke sessions/families. Passwords are hashed; plaintext passwords are never stored.
5. **Initial administrator:** Bootstrap the first administrator through a secure one-time setup command/environment-variable flow. Never use hardcoded default credentials.
6. **Report storage:** Generate downloadable PDFs. MySQL stores report metadata. Development files use a protected local directory; production uses private persistent report storage with configurable retention and backup policies.
7. **Scheduling:** ADMIN assigns an enabled technician when scheduling. ADMIN may reschedule before processing starts; normal rescheduling is rejected after IN_PROGRESS. Record previous and new schedules and assignments in the audit log.
8. **Audit:** Maintain append-only audit records for important security and medical workflow operations.

## Roles and authorization rules

| Role | Allowed operations and scope |
|---|---|
| PATIENT | Register; read/update permitted own profile; view own requests, schedules, statuses, results and current/prior reports; view doctor assignments and grant/withdraw consent for an active assignment. |
| DOCTOR | Access a patient only when an active DOCTOR_PATIENT_ASSIGNMENT and active PATIENT_CONSENT both exist. Create one-test requests for such patients. Read requests, results and reports for those patients only while both conditions remain active. |
| LAB_TECHNICIAN | View/process only requests assigned to their account. Start eligible work, enter the initial result, request a correction to a completed result, apply an approved correction and generate/reissue reports. Cannot schedule, reschedule, assign work or approve corrections. |
| ADMIN | Provision/disable accounts and manage roles; assign/revoke doctor-patient relationships; schedule and assign technicians; reschedule before processing; manage catalogue and profiles; approve/reject result corrections; monitor records and read audit events/reports for administrative purposes. |
| Anonymous | Register as PATIENT or authenticate. Cannot choose a privileged role or access protected data. |

All protected reads and writes enforce policy in the backend. The API derives the acting user from the authenticated principal. Never trust client-supplied role or actor IDs. The doctor consent/assignment policy is rechecked on every patient/request/result/report access, including report download; do not rely on a previously loaded page or cached authorization.

### Resource access

- Patients can access only their own records.
- Doctors require an active assignment and current recorded consent for every protected patient record/report request. Revoking either denies access immediately, including historical report files. Revocation never deletes medical records; ADMIN retains authorized administrative access.
- Revocation blocks future server/API access; it cannot recall a report file that an authorized doctor already downloaded. Do not expose public URLs, and send report responses with private/no-store cache headers.
- A technician can view/open/update only a request whose assigned_technician_user_id matches the authenticated user and whose state permits the action.
- ADMIN alone assigns technicians, schedules/reschedules, grants/revokes assignments, approves corrections, manages roles and reads audit logs.
- Only the assigned technician enters results or generates/reissues a report. Completed-result correction requires an ADMIN-approved correction request before the technician can apply it.
- File endpoints repeat report authorization and stream from private storage. No public report URLs or raw storage paths are returned.

## Functional requirements

| ID | Requirement | Source / priority |
|---|---|---|
| FR-01 | A patient can register with required profile information. Public registration cannot create privileged roles. | Report / High |
| FR-02 | The system authenticates users and routes them to role-appropriate views using Spring Security/JWT. | Report + Approved decision / High |
| FR-03 | Backend authorization enforces role, patient ownership, active doctor assignment plus consent, and technician assignment. | Report + Brief + Approved decision / High |
| FR-04 | Patients manage permitted own profile fields. ADMIN manages patient/doctor profiles and provisions staff accounts. | Report + Brief / High |
| FR-05 | Authenticated users view active tests. ADMIN creates/updates/deactivates test definitions, including description, price and reference range. | Report / High |
| FR-06 | A DOCTOR creates a request for one active test only when an active assignment and patient consent exist for that patient. | Report + Brief + Approved decision / High |
| FR-07 | ADMIN schedules a REQUESTED request and assigns an enabled LAB_TECHNICIAN in the same operation. | Brief + Approved decision / High |
| FR-08 | Each technician sees only requests assigned to their account. | Brief + Approved decision / High |
| FR-09 | The assigned technician starts eligible work, moving SCHEDULED to IN_PROGRESS. | Report + Approved decision / High |
| FR-10 | The assigned technician records the initial result value, remarks and result date. Each result version records author/time. | Report + Approved decision / High |
| FR-11 | Generate downloadable PDF reports; store report metadata in MySQL and PDF files in protected storage. | Report + Brief + Approved decision / High |
| FR-12 | Status follows REQUESTED -> SCHEDULED -> IN_PROGRESS -> COMPLETED -> REPORT_GENERATED. Invalid/skipped transitions are rejected. | Report + Brief / High |
| FR-13 | Patients read their own current and historical reports. Doctors read them only while assignment and patient consent are active. ADMIN retains permitted administrative access after revocation. | Report + Approved decision / High |
| FR-14 | Authorized users search requests by patient/date/test/status within their role scope; technicians search assigned work only. | Report + Brief + Approved decision / Medium |
| FR-15 | ADMIN manages users, roles, patients, doctors, tests and requests, and reads records/audit entries for administrative purposes. | Report + Brief + Approved decision / High |
| FR-16 | ADMIN creates and revokes doctor-patient assignments. The patient records consent for that assignment; re-assignment requires new consent. | Approved decision / High |
| FR-17 | ADMIN alone reschedules before processing. Previous/new schedule and technician assignment are recorded. Reschedule after IN_PROGRESS is rejected. | Approved decision / High |
| FR-18 | Any correction to a completed result requires an ADMIN decision. The technician applies an approved correction as a new result version; if a report exists, a new report is issued and the old report retained/superseded. | Approved decision / High |
| FR-19 | AUDIT_LOG records login outcomes, user/role changes, assignment/consent, schedule/reschedule, status transitions, result entry/correction, report generation/reissue and token rotation/revocation/reuse. | Approved decision / High |
| FR-20 | Access JWT expires after 15 minutes; refresh token expires after 30 days and rotates on use. Reuse/revocation invalidates the token family. | Approved decision / High |
| FR-21 | Bootstrap the initial ADMIN once using protected setup/environment configuration; do not hardcode default credentials. | Approved decision / High |
| FR-22 | Use synthetic patient identities/results in the demo. Production report storage has configurable retention and backup policies. | Approved decision / High |

### Acceptance criteria

The report’s acceptance criteria remain: correct role views, unauthorized actions denied, doctor request, schedule, technician processing, correct lifecycle, valid result/report, authorized report access, invalid input rejected and persistent records.

Additional acceptance criteria: neither assignment alone nor consent alone authorizes doctor access; withdrawing consent or revoking assignment blocks current and historical report access immediately; ADMIN can still retrieve preserved records for authorized administration; technician queues exclude all other assignments; completed-result corrections cannot be applied without ADMIN approval; original results and PDFs remain preserved; rescheduling before processing records old/new values, while a request at IN_PROGRESS or later is rejected; refresh rotation/reuse/revocation behaves as specified; and required events are present in append-only audit history.

## Non-functional requirements

| ID | Requirement | Planning interpretation |
|---|---|---|
| NFR-01 Security | Passwords are never stored in plaintext. | Spring Security PasswordEncoder using BCrypt or Argon2; secrets are not committed. |
| NFR-02 Privacy | Access is limited by role and relationship/assignment. | Check current assignment and consent for every doctor read/download; check technician assignment for every lab operation. |
| NFR-03 Performance | Common login/search/status operations meet an agreed academic-demo target. | The source provides no numeric SLA; measure an agreed target during verification. |
| NFR-04 Availability | Subject to host/network availability. | No uptime or production host is specified. |
| NFR-05 Reliability | Committed records persist accurately. | Use MySQL constraints and transactions for result, audit and report metadata. |
| NFR-06 Usability | Clear labels, validation and status indicators. | Display assignment, consent, correction state and superseded report state clearly. |
| NFR-07 Scalability | Support moderate growth in users/tests/requests/reports. | Modular monolith and indexed MySQL. |
| NFR-08 Maintainability | Focused modules and documented APIs. | Separate controllers, policies, services, repositories and DTOs. |
| NFR-09 Auditability | Important actions are traceable. | Append-only actor/time/action records and safe before/after schedule/role/status fields. |
| NFR-10 Backup | Database and report files have defined backup/restore procedures. | Configurable backup schedule/retention; periodically verify restoration. |
| NFR-11 Token safety | Access tokens are short-lived; refresh tokens rotate and revoke. | Fixed 15-minute/30-day lifetimes, one-time rotation and reuse detection. |
| NFR-12 Result integrity | History is not overwritten. | Append result versions with author, timestamp, reason, approver and approval time. |
| NFR-13 Storage confidentiality | Report files are private. | Local protected storage for development; private persistent storage in production. |
| NFR-14 Demo privacy | No real patient information is stored in the demo. | Seed only synthetic patient, request and result records. |

## Remaining implementation parameters

The architecture decisions above are approved. Before deployment, configure the actual patient-consent text/version, private storage provider, retention duration, backup schedule/retention, audit-log archive/retention, report layout and the secured handling/removal of one-time bootstrap secrets. One workflow detail still needs confirmation before coding: the approved process covers report reissue caused by a completed-result correction; a standalone PDF-only correction when the result itself is unchanged has no separate workflow yet. The other listed settings do not change the approved access rules or workflow.

