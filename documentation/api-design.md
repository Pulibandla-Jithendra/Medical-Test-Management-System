# MediCore REST API Design

## Conventions and authentication

- Base path /api/v1. REST requests and metadata responses use JSON; report files download as application/pdf.
- Use DTOs and server-side validation; never expose JPA entities.
- Spring Security authenticates protected routes. Services repeat resource checks; frontend navigation is not authorization.
- Access token: JWT in HttpOnly, SameSite=Lax cookie, Secure in HTTPS deployments, expires exactly 15 minutes after issue.
- Refresh token: high-entropy opaque value in a separate HttpOnly, SameSite=Lax cookie scoped to /api/v1/auth; Secure in HTTPS. Expires 30 days absolute from the login family, not extended by rotation.
- Store only refresh token hashes. Rotate atomically on each successful refresh. Detect reuse and revoke the family. Each access JWT carries the family id; protected-request filter checks family and USER.security_version to make revocation immediate.
- Use CSRF protection for cookie-authenticated state changes, including login, refresh and logout. GET /auth/csrf initializes/returns the CSRF token. Use same-origin frontend/API where practical. JavaScript does not store tokens in localStorage/sessionStorage.
- Logout revokes current family. Logout-all revokes all user families and increments security_version. Role/account disable also revokes all user families and increments security_version.
- Never trust client-supplied actor, role, doctor or technician IDs. HTTPS is required outside localhost.

## Authorization matrix

| Capability | PATIENT | DOCTOR | LAB_TECHNICIAN | ADMIN |
|---|---|---|---|---|
| Register/login | Patient register/login | Login | Login | Login |
| Own profile | Read/update permitted fields | Read/update permitted fields | Own account | Own account |
| Patient protected data | Own records | Only active assignment AND active patient consent | Only data needed for requests assigned to self | Administrative scope |
| Create test request | No | Yes, for an actively assigned and consenting patient | No | No |
| Schedule/assign/reschedule | No | No | No | Yes; reschedule before IN_PROGRESS |
| Technician queue/process | No | No | Assigned requests only | Monitor all |
| Enter initial result | No | No | Assigned request only | No |
| Correct completed result | No | No | Request correction and apply only after ADMIN approval | Approve/reject |
| Generate/reissue PDF | No | No | Assigned request only | Administrative report access |
| Reports | Own current/history | Only while assignment and consent both active | Assigned workflow as needed | Authorized administrative access |
| Manage roles/assignments/catalogue/audit | No | No | No | Yes |

If either patient consent or doctor assignment is revoked, doctor report metadata and file endpoints deny both current and historical reports immediately. Patient and ADMIN access remains subject to their own authorization. Records are preserved.

## Authentication and bootstrap endpoints

| Method and path | Purpose | Access |
|---|---|---|
| GET /auth/csrf | Initialize/return CSRF token cookie/header. | Anonymous or authenticated |
| POST /auth/register | Create a PATIENT account/profile; reject privileged role fields. | Anonymous + CSRF |
| POST /auth/login | Verify hash, create refresh family, set access/refresh cookies, record login outcome. | Anonymous + CSRF |
| POST /auth/refresh | Validate and rotate refresh cookie; issue replacement. Reuse revokes family. | Valid refresh cookie + CSRF |
| POST /auth/logout | Revoke current refresh family and clear cookies; family check invalidates access JWT too. | Authenticated + CSRF |
| POST /auth/logout-all | Revoke all user families, increment security_version and clear cookies. | Authenticated + CSRF |
| GET /auth/me | Return safe identity/profile and current role. | Authenticated |

The first ADMIN is not created through a public API. Use a one-time protected setup command enabled only when no ADMIN exists. It reads ADMIN credentials from protected environment variables or secure interactive input, hashes the password immediately, emits no secret to logs and refuses to run once an ADMIN exists. Remove bootstrap secrets/disable the command after success. Never use hardcoded defaults.

Log success/failure and timestamp; identify the account when known. Never log submitted passwords or token values.

## Doctor-patient assignment and consent endpoints

| Method and path | Purpose | Access |
|---|---|---|
| POST /admin/doctor-patient-assignments | Create assignment episode for doctorId and patientId. Access remains blocked until patient consent is recorded. | ADMIN |
| GET /admin/doctor-patient-assignments | List active/revoked assignment episodes and consent status. | ADMIN |
| POST /admin/doctor-patient-assignments/{assignmentId}/revoke | Revoke assignment and immediately deny doctor access. Preserve request/report records. | ADMIN |
| GET /patients/me/doctor-assignments | Patient views assigned doctors and consent status. | PATIENT |
| POST /patients/me/doctor-assignments/{assignmentId}/consent | Record consent for the presented consent text version; patient identity comes from principal. | PATIENT, assignment must be active |
| POST /patients/me/doctor-assignments/{assignmentId}/withdraw-consent | Revoke consent and immediately deny doctor access to current/history data. | PATIENT |
| GET /doctors/me/patients | List/search only patients with active assignment and active consent. | DOCTOR |

Each consent record includes patient account, assignment episode, consent text/version and timestamp. Reassignment creates a new episode and requires fresh consent. The API does not return patient data or allow request creation until both checks pass.

## Profiles and catalogue

| Method and path | Purpose | Access |
|---|---|---|
| GET/PATCH /patients/me | Read/update permitted own profile fields. | PATIENT |
| GET/PATCH /doctors/me | Read/update own profile; sensitive fields may be ADMIN-controlled. | DOCTOR / ADMIN |
| GET /medical-tests and GET /medical-tests/{testId} | Read active catalogue definitions. | Authenticated |
| POST /medical-tests | Create medical test. | ADMIN |
| PATCH /medical-tests/{testId} | Update/deactivate definition. | ADMIN |

## Requests, schedule and laboratory work

| Method and path | Purpose | Access |
|---|---|---|
| POST /test-requests | Create REQUESTED for exactly one active test. Server derives doctor and assignment; requires active consent and assignment. | DOCTOR |
| GET /test-requests | Search by allowed patient/date/test/status after applying role scope. | Authenticated |
| GET /test-requests/{requestId} | Read a request within caller’s scope. | Patient owner, authorized doctor, assigned technician, ADMIN |
| POST /admin/test-requests/{requestId}/schedule | Set schedule and assign enabled LAB_TECHNICIAN; transition REQUESTED to SCHEDULED. | ADMIN |
| POST /admin/test-requests/{requestId}/reschedule | Change date/time and optionally assigned technician before processing starts; log old/new values. | ADMIN |
| POST /test-requests/{requestId}/start | Transition SCHEDULED to IN_PROGRESS. | Assigned LAB_TECHNICIAN |
| GET /lab/work-queue | List only requests assigned to authenticated technician. | LAB_TECHNICIAN |
| GET /admin/test-requests | Monitor/search all requests. | ADMIN |

Initial scheduling payload: scheduledDate, scheduledTime and technicianId. Rescheduling payload: newScheduledDate, newScheduledTime and optionally newTechnicianId. Server records old/new date, time and technician in AUDIT_LOG in the same transaction. If status is IN_PROGRESS or later, return conflict; no normal reschedule or implicit lifecycle rollback is allowed.

## Results, corrections and reports

| Method and path | Purpose | Access |
|---|---|---|
| POST /test-requests/{requestId}/results | Create the initial result version while IN_PROGRESS and transition to COMPLETED. No overwrite. | Assigned LAB_TECHNICIAN |
| GET /test-requests/{requestId}/results | Read current result and version history. | Patient owner, authorized doctor, assigned technician, ADMIN |
| POST /test-requests/{requestId}/correction-requests | Request correction to a COMPLETED result; include targetResultId, required reason and targetReportId when a report already exists. | Assigned LAB_TECHNICIAN |
| GET /admin/result-correction-requests | List pending/decided correction requests. | ADMIN |
| POST /admin/result-correction-requests/{id}/approve | Approve and audit approver/time. | ADMIN |
| POST /admin/result-correction-requests/{id}/reject | Reject and audit approver/time/reason. | ADMIN |
| POST /result-correction-requests/{id}/apply | Insert new immutable result version after approval; preserve original and record technician/time/reason. | Assigned LAB_TECHNICIAN |
| POST /test-requests/{requestId}/reports | Generate the initial PDF after completion or reissue after an approved correction. | Assigned LAB_TECHNICIAN |
| GET /reports and GET /reports/{reportId} | List/read current and superseded metadata. Doctor calls require current assignment and consent. | Patient owner, authorized doctor, assigned technician, ADMIN |
| GET /reports/{reportId}/file | Stream PDF from private storage after fresh ownership/assignment/consent/role check. | Authorized caller |

Every correction to a completed result requires ADMIN approval, even before a PDF exists. A correction request identifies the current result; if a report exists it also identifies the report version being corrected. Only the assigned technician applies an approved change, as a new TEST_RESULT version with actor/time/reason. If a report existed, preserve/mark the previous PDF SUPERSEDED and create a new REPORT version from the corrected result. No result or report file is overwritten. Keep request status COMPLETED if no report exists, or REPORT_GENERATED if one has already been issued; do not move status backward.

Report PDFs contain patient identity, test, request/result dates, result value, remarks, reference range, report date and version. Database metadata stores an opaque file key; clients never receive public URLs or filesystem paths.

## Administration and audit endpoints

| Method and path | Purpose | Access |
|---|---|---|
| GET /admin/users | Paginated safe user list. | ADMIN |
| POST /admin/users | Provision DOCTOR, LAB_TECHNICIAN or ADMIN account. | ADMIN |
| PATCH /admin/users/{userId} and PATCH /admin/users/{userId}/role | Update/disable account or role; revoke sessions/increment security_version as needed; audit before/after. | ADMIN |
| GET /admin/patients and GET /admin/doctors | Manage profile records. | ADMIN |
| GET /admin/audit-logs | Search append-only events by date/action/actor/target. | ADMIN |

No API hard-deletes users, assignments/consents, requests, result versions, reports or audit entries. Revoke/disable and preserve records.

## Transactions, storage and errors

- Audit important mutations in the same database transaction. If audit persistence fails, fail the mutation rather than commit an untraceable schedule, result, consent or report change.
- Generate/store PDFs through a storage adapter. Development uses a configurable protected local directory outside static resources. Production uses private persistent storage, not public URLs. Retention/backup are configurable; report metadata remains in MySQL. PDF responses use private, no-store cache headers.
- On file/database failure, avoid publishing dangling metadata and clean up orphaned files where possible.
- Standard status meanings: 200 read/update, 201 create, 204 no content, 400 malformed request, 401 missing/invalid auth, 403 forbidden, 404 missing or intentionally hidden data, 409 stale/invalid transition/duplicate/replayed token, 422 business validation.
- Error bodies contain safe code/message/field details/correlation ID; never return SQL, stack trace, secrets, other patients’ data or storage paths.
- Demo data is synthetic only.

## Remaining API/configuration parameters

Configure the patient-consent text/version, report storage provider, report retention/backup values, audit archive/retention, PDF template, and bootstrap secret delivery/removal in deployment. These parameters do not relax the approved authorization requirements.

