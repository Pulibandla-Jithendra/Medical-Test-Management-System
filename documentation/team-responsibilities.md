# MediCore Team Responsibilities

## Team structure

Five developers share one modular Spring Boot application and one Vanilla JavaScript frontend. All new access, consent and correction rules are part of implementation ownership, not optional UI behavior.

| Member | Primary ownership | Planned deliverables |
|---|---|---|
| Member 1 - Architecture, security and audit | Application skeleton, Maven configuration, Spring Security/JWT, refresh rotation/revocation, CSRF, one-time ADMIN bootstrap, common API patterns, append-only audit module and integration | Auth/bootstrap design; event schema/writer; error and validation conventions; common authorization policy; build/branch workflow |
| Member 2 - Identity, assignments and consent | USER/PATIENT/DOCTOR profiles, role provisioning and DOCTOR_PATIENT_ASSIGNMENT/PATIENT_CONSENT workflows | Patient registration/profile; doctor profile; admin assignment/revocation screens; patient consent/withdrawal; doctor active-patient list; immediate access revocation checks |
| Member 3 - Catalogue and doctor requests | MEDICAL_TEST and TEST_REQUEST creation/search, relationship/consent checks | Catalogue API/UI; one-test request form/list; request-to-assignment integrity and scope-safe patient/request access |
| Member 4 - Admin scheduling and technician workflow | ADMIN schedule/technician assignment/rescheduling; assigned-only queue and status transitions | Schedule/reassign forms; pre-processing reschedule rules; previous/new schedule audit values; technician work queue isolation; start-processing action |
| Member 5 - Results, corrections, reports and storage | Append-only TEST_RESULT versions; correction request/approval/application integration; PDF REPORT versions; private file storage/download | Result entry/history; correction request/application; report generation/reissue/supersession; protected local storage adapter and production storage handoff; report authorization |

## Shared ownership and handoffs

- **Authorization:** Member 1 defines common security policy. Feature owners enforce assignment+consent (Member 2/3), technician assignment (Member 4/5), and admin-only actions in service/query code. Another member reviews each sensitive access path.
- **Consent lifecycle:** Member 2 owns assignment/consent endpoints and screens. Member 3 consumes the authorization check before request creation. Member 5 uses the same live check before doctor report metadata/file delivery.
- **Database changes:** Member 1 coordinates schema migration ordering. Feature owners document columns, FKs, indexes, state constraints and audit events; a peer reviews before merge.
- **Workflow handoff:** Members 2-3 review assignment/consent -> doctor request; Members 3-4 review request -> ADMIN schedule/assignment -> technician queue; Members 4-5 review assigned processing -> result -> report; Member 1 coordinates end-to-end integration.
- **Corrections:** Member 5 owns result/report versions and endpoints; Member 1 owns common authorization/audit; ADMIN approval screen and decision audit are coordinated with Member 2. Original results and report files remain available as history.
- **Audit:** all feature owners emit events for their sensitive changes in the same database transaction. Member 1 owns the append-only writer and ADMIN-only query path. Do not log credentials, tokens, result contents or report contents.
- **Frontend:** Member 1 maintains shared auth/CSRF/API helpers; Member 2 builds profiles/assignment/consent views; Member 3 catalogue/request; Member 4 admin scheduling and technician queue; Member 5 result/correction/report views.
- **Synthetic demo:** Member 2 prepares synthetic identities/consents, Member 3 catalogue/requests, Member 4 schedules/assignments, Member 5 result versions/PDFs, Member 1 builds the runbook. Never seed real patient data into the demo.

## Suggested cadence and Git workflow

Use the report’s four two-week sprint example within its indicative 12-week schedule:

1. Foundation, database migrations, admin bootstrap, login/refresh, basic role views.
2. Profiles, assignment and consent, catalogue and doctor-created requests.
3. ADMIN scheduling/assignment/rescheduling, assigned technician work and initial result entry.
4. ADMIN-approved corrections, result/report history, PDF reissue, audit search, regression and demo.

Use main for reviewed stable work, develop for integration and short branches such as feature/auth-audit, feature/assignment-consent, feature/catalogue-requests, feature/admin-scheduling and feature/results-reports. Pull requests include affected endpoints/schema, authorization changes and verification status. Do not commit secrets, real patient data, local DB files or report PDFs.

## Working agreements

- Preserve the original report as requirements baseline and keep one medical test per request.
- Maintain the five required request statuses; corrections/report reissue do not move status backward.
- Doctor access always requires active assignment and patient consent. Either revocation immediately blocks current and historical reports; records are retained for ADMIN.
- ADMIN alone schedules/assigns, reschedules before IN_PROGRESS and approves completed-result corrections.
- Append result/report versions; never overwrite prior medical content or report files.
- Audit important security/workflow actions without storing secrets or medical payloads.
- Use synthetic demo records only. Resolve remaining deployment parameters before release.

