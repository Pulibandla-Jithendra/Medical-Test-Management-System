# MediCore Database Design

## Design basis and cardinalities

The original report’s seven core entities remain USER, PATIENT, DOCTOR, MEDICAL_TEST, TEST_REQUEST, TEST_RESULT and REPORT. Approved decisions add DOCTOR_PATIENT_ASSIGNMENT, PATIENT_CONSENT, REFRESH_TOKEN, RESULT_CORRECTION_REQUEST and AUDIT_LOG.

The source report’s one-test-per-request structure is retained. Its simpler zero-or-one result/report cardinality is extended: TEST_REQUEST has versioned result history and report history so corrections and reissues preserve prior records. These are approved extensions, not replacements for the source requirements.

Proposed for MySQL 8.4, InnoDB, utf8mb4, UTC timestamps and Spring Data JPA/Hibernate. No schema has been implemented.

## Relationship summary

- USER 1 to 0..1 PATIENT and USER 1 to 0..1 DOCTOR; user roles remain PATIENT, DOCTOR, LAB_TECHNICIAN and ADMIN.
- DOCTOR 1 to many DOCTOR_PATIENT_ASSIGNMENT; PATIENT 1 to many DOCTOR_PATIENT_ASSIGNMENT. Each row is an assignment episode, created/revoked by ADMIN.
- DOCTOR_PATIENT_ASSIGNMENT 1 to many PATIENT_CONSENT rows. Each consent grant is patient-recorded for that exact assignment; revocation is retained.
- USER (the assigned patient account) 1 to many PATIENT_CONSENT rows as consented_by_user_id; USER also records revocation through revoked_by_user_id.
- PATIENT 1 to many TEST_REQUEST; DOCTOR 1 to many TEST_REQUEST; MEDICAL_TEST 1 to many TEST_REQUEST.
- Each TEST_REQUEST stores the assignment episode authorizing creation and the currently assigned technician. It has exactly one medical test.
- USER with LAB_TECHNICIAN role 1 to many TEST_REQUEST through assigned_technician_user_id; an unassigned request is allowed only while REQUESTED.
- TEST_REQUEST 1 to many TEST_RESULT versions; each version records one entering USER, timestamp and optional predecessor.
- TEST_REQUEST 1 to many REPORT versions; each report links to the exact TEST_RESULT version rendered into its PDF.
- TEST_REQUEST 1 to many RESULT_CORRECTION_REQUEST rows. Each row identifies the targeted result and, when present, the target report; approval/application actors are USER rows.
- USER 1 to many REFRESH_TOKEN rows and AUDIT_LOG rows as actor/subject.

## Core entities

### USER

| Field | Proposed type | Constraints / meaning |
|---|---|---|
| user_id | BIGINT | Primary key |
| name | VARCHAR(120) | Required |
| email | VARCHAR(254) | Required, normalized and unique |
| password_hash | VARCHAR(255) | Required; BCrypt/Argon2 encoded value only |
| role | VARCHAR(32) or enum | PATIENT, DOCTOR, LAB_TECHNICIAN or ADMIN |
| created_at | DATETIME(6) | Server-generated UTC |
| enabled | BOOLEAN | Required |
| security_version | INT | Default 0; increment on role/account-wide security changes to invalidate access JWTs |

No plaintext passwords, access JWTs or refresh tokens are stored in USER.

### PATIENT and DOCTOR

PATIENT retains patient_id PK, user_id unique FK, age, gender, phone and address. DOCTOR retains doctor_id PK, user_id unique FK and specialization. Keep only fields approved for the prototype; do not infer additional clinical history or a limited gender set.

### DOCTOR_PATIENT_ASSIGNMENT

| Field | Proposed type | Constraints / meaning |
|---|---|---|
| assignment_id | BIGINT | Primary key; identifies one assignment episode |
| doctor_id | BIGINT | Required FK to DOCTOR |
| patient_id | BIGINT | Required FK to PATIENT |
| assigned_by_user_id | BIGINT | Required FK to USER; service requires ADMIN role |
| assigned_at | DATETIME(6) | Required server timestamp |
| revoked_by_user_id | BIGINT | Nullable FK to USER; service requires ADMIN |
| revoked_at | DATETIME(6) | Null means assignment is active |

Keep revoked episodes. A new assignment after revocation gets a new assignment_id and cannot reuse consent from the old episode. Serialize grant operations by locking the related patient and doctor rows, then check for an existing active episode. Add UNIQUE(assignment_id, doctor_id, patient_id) for request composite foreign-key consistency.

### PATIENT_CONSENT

| Field | Proposed type | Constraints / meaning |
|---|---|---|
| consent_id | BIGINT | Primary key |
| assignment_id | BIGINT | Required FK to DOCTOR_PATIENT_ASSIGNMENT |
| consented_by_user_id | BIGINT | Required FK to USER; service verifies this is the assigned patient’s account |
| consent_text_version | VARCHAR(32) | Required version of the consent wording presented |
| consented_at | DATETIME(6) | Required server timestamp |
| revoked_at | DATETIME(6) | Null means this consent grant is active |
| revoked_by_user_id | BIGINT | Nullable FK to USER; service verifies patient owner |

One row records a consent grant. Revocation updates revoked_at/revoked_by_user_id; a later consent is a new row. Preserve all rows. Serialize consent/revoke operations by locking the assignment row and allow only one unrevoked consent for that assignment. A usable authorization requires both this active consent and a non-revoked assignment.

### MEDICAL_TEST

Retain test_id PK, test_name, description, price, reference_range and active. Proposed types are BIGINT, VARCHAR(160), TEXT, DECIMAL(10,2), VARCHAR(255) and BOOLEAN. Enforce price >= 0. Price is display/catalogue data only; payment is out of scope. Deactivate referenced tests instead of deleting them.

### TEST_REQUEST

| Field | Proposed type | Constraints / meaning |
|---|---|---|
| request_id | BIGINT | Primary key |
| patient_id | BIGINT | Required FK to PATIENT |
| doctor_id | BIGINT | Required FK to DOCTOR |
| doctor_patient_assignment_id | BIGINT | Required FK to assignment episode; composite FK validates patient/doctor match |
| test_id | BIGINT | Required FK to active MEDICAL_TEST at creation |
| assigned_technician_user_id | BIGINT | Null while REQUESTED; required from SCHEDULED onward; FK to USER |
| request_date | DATETIME(6) | Required server timestamp |
| scheduled_date | DATE | Null while REQUESTED; paired with scheduled_time |
| scheduled_time | TIME | Null while REQUESTED; paired with scheduled_date |
| status | VARCHAR(32) or enum | REQUESTED, SCHEDULED, IN_PROGRESS, COMPLETED or REPORT_GENERATED |

Keep one test_id per request. The assignment_id is the episode authorizing this request; at creation the service must verify it is active and has active consent. After revocation, the row remains for history but doctor access is denied. Scheduling and technician assignment are atomic. ADMIN rescheduling is permitted before IN_PROGRESS only; AUDIT_LOG records before/after date, time and technician.

### TEST_RESULT

| Field | Proposed type | Constraints / meaning |
|---|---|---|
| result_id | BIGINT | Primary key |
| request_id | BIGINT | Required FK to TEST_REQUEST |
| version_no | INT | Required; starts at 1; unique per request |
| supersedes_result_id | BIGINT | Nullable self-FK to prior version |
| result_value | TEXT | Required |
| remarks | TEXT | Optional pending approved field policy |
| result_date | DATE | Required |
| entered_by_user_id | BIGINT | Required FK to USER; must be assigned LAB_TECHNICIAN |
| entered_at | DATETIME(6) | Required server timestamp |
| correction_reason | VARCHAR(1000) | Required for corrected versions |

Use UNIQUE(request_id, version_no), UNIQUE(request_id, result_id), and UNIQUE(supersedes_result_id) to prevent branches. Lock the request while allocating the next version. The latest version is current. Result versions are append-only; do not update/delete prior result content. Any correction after status COMPLETED requires an approved RESULT_CORRECTION_REQUEST, whether or not a PDF exists. The correction row links the approved new result, avoiding a cyclic foreign key.

### RESULT_CORRECTION_REQUEST

| Field | Proposed type | Constraints / meaning |
|---|---|---|
| correction_request_id | BIGINT | Primary key |
| request_id | BIGINT | Required FK to TEST_REQUEST |
| target_result_id | BIGINT | Required composite FK with request_id to the current TEST_RESULT |
| target_report_id | BIGINT | Nullable composite FK with request_id to current REPORT, if a report already exists |
| requested_by_user_id | BIGINT | Required FK to USER; must be assigned technician |
| reason | VARCHAR(1000) | Required correction reason |
| requested_at | DATETIME(6) | Required server timestamp |
| status | VARCHAR(16) | PENDING, APPROVED, REJECTED or APPLIED |
| reviewed_by_user_id | BIGINT | Null until decided; ADMIN only |
| reviewed_at | DATETIME(6) | Null until decided |
| decision_note | VARCHAR(1000) | Optional |
| applied_result_id | BIGINT | Null until applied; composite FK to the new result version |

Permit only one pending correction for the current result at a time. The approved technician applies a corrected result version; the correction row stores its applied_result_id and becomes APPLIED. If a report existed, preserve it as SUPERSEDED and require a new report version. Keep the original result and report. The approval record identifies the administrator and decision timestamp.

### REPORT

| Field | Proposed type | Constraints / meaning |
|---|---|---|
| report_id | BIGINT | Primary key |
| request_id | BIGINT | Required FK to TEST_REQUEST |
| result_id | BIGINT | Required composite FK to exact result version for same request |
| report_version | INT | Required; unique per request |
| generated_by_user_id | BIGINT | Required FK to USER; assigned technician only |
| report_date | DATETIME(6) | Required server timestamp |
| report_file_key | VARCHAR(255) | Required opaque storage key; never a public URL |
| report_state | VARCHAR(16) | CURRENT or SUPERSEDED |
| supersedes_report_id | BIGINT | Nullable self-FK |

Use UNIQUE(request_id, report_version) and UNIQUE(request_id, report_id), with composite result FK to TEST_RESULT(request_id, result_id). Keep PDFs and metadata for all versions. After an approved correction to a reported result is applied, the previous report is superseded; the technician generates a new report from the corrected result. Keep status REPORT_GENERATED; do not roll back the required lifecycle. Enforce at most one CURRENT report for a request in a transaction; zero current reports are allowed between correction application and successful reissue.

PDF contents follow the source report’s proposed view: patient identity, test name, request/result dates, result value, remarks, reference range, report date and report version.

### REFRESH_TOKEN

Store token_id UUID, user_id FK, family_id UUID, unique token_hash, issued_at, expires_at, used_at, revoked_at and replaced_by_token_id self-FK. Use random high-entropy opaque refresh values; only their hash is persisted. A family has at most one active, unused, unrevoked token. Access JWTs include family id; protected-request security checks USER.security_version and active family status.

Rotate in one transaction with a row lock and conditional consume. A reused token revokes its family and creates an audit event. Expiry is 30 days absolute from the login family; rotation does not extend the absolute expiry. Access JWT expiry is 15 minutes. Logout revokes the current family; logout-all and role/account changes revoke all user families and increment security_version.

### AUDIT_LOG

| Field | Proposed type | Constraints / meaning |
|---|---|---|
| audit_id | BIGINT | Primary key |
| actor_user_id | BIGINT | Nullable for unknown/anonymous login failure |
| subject_user_id | BIGINT | Nullable affected account |
| action | VARCHAR(64) | Controlled event |
| entity_type | VARCHAR(64) | Target type |
| entity_id | VARCHAR(64) | Polymorphic target identifier |
| occurred_at | DATETIME(6) | Required server timestamp |
| before_data | JSON | Nullable, allowlisted old values |
| after_data | JSON | Nullable, allowlisted new values |
| correlation_id | CHAR(36) | Optional request correlation ID |

Record login outcomes; user/role/account changes; assignments and consent; scheduling/rescheduling; status transitions; result entry and approved corrections; report generation/reissue; refresh rotation, revocation and reuse detection; and bootstrap completion. Schedule before/after data includes date, time and technician. Correction audit events reference original/corrected result IDs, reason, approving ADMIN and timestamps. Never store passwords, tokens, full result/report contents or unnecessary patient information. The application DB role has INSERT/SELECT only on AUDIT_LOG; no update/delete API. Restrict reads to ADMIN. Archive according to approved policy without editing archived events.

## Integrity, authorization and retention

- A request has one patient, doctor, assignment episode and medical test. Composite FK ensures the assignment matches patient/doctor.
- Request creation requires active assignment AND active patient consent; checks are repeated on every doctor data/report endpoint.
- Assignment revocation or consent withdrawal immediately denies doctor access to all current/historical request, result and report data. It never deletes those rows/files. ADMIN retains authorized administrative access.
- The patient account that records/revokes consent must match the patient on the assignment. The authorizing assignment creator/revoker must have ADMIN role.
- A scheduled request requires both schedule fields and an enabled LAB_TECHNICIAN assignment. Only ADMIN may schedule/reschedule; reschedule only before IN_PROGRESS.
- Result author must be the assigned technician. Corrections after COMPLETED require an approved admin decision; result history is append-only.
- Every REPORT links to an exact result version. A previously generated PDF is retained and marked SUPERSEDED after correction; it is not overwritten.
- Use RESTRICT/NO ACTION for medical-history FKs. Disable users/revoke assignments rather than cascading deletion.
- Index USER.email; assignment doctor/patient/revocation; consent assignment/revocation; TEST_REQUEST patient/doctor/assignment/technician/status/schedule; TEST_RESULT request/version; correction request status/target; REPORT request/version; REFRESH_TOKEN hash/family/user/expiry; AUDIT_LOG time/action/actor/subject/target.
- Patient/doctor/request/result/report search is paginated and role-scoped.

## Protected files, retention and backups

Use a storage adapter so development can use a configurable local directory outside the static web root and production can use private persistent storage. Storage must deny public access; downloads resolve opaque keys server-side after current authorization checks. MySQL stores metadata and version links, not public URLs.

Make report retention and backup schedules configurable. Revoke/withdrawal never deletes records. Any retention-driven archive or deletion follows the approved policy and preserves administrative access where required. Back up MySQL and private report files as a consistent set and document restore verification. Demo data is synthetic only; do not store real patient information in the demo.

