# MediCore frontend

The existing HTML, CSS and Vanilla JavaScript pages are connected to the MediCore REST API. The frontend remains a static application served locally; it does not connect to MySQL directly and contains no database credentials.

## Run on Windows 11

To try the screens without backend setup, from the project root run:

```powershell
py -m http.server 4173 --directory frontend
```

Open `http://localhost:4173`. On the sign-in page, choose one of the four example role accounts to open its read-only synthetic workspace. No password, MySQL connection, or backend is needed for this preview. On the registration page, **Create local preview profile** opens a patient preview using a fictional `@example.test` email. Preview changes are not saved and no password is stored.

For real API-backed account registration and login, start MySQL and the Spring Boot backend using [the backend setup guide](../backend/medicore/README.md). Then serve the frontend from the project root:

```powershell
py -m http.server 4173 --directory frontend
```

Open `http://localhost:4173`. Configure the API in `config.js`:

```javascript
window.MEDICORE_CONFIG = Object.freeze({
  apiBaseUrl: "http://localhost:8080/api/v1",
  consentTextVersion: "v1"
});
```

For production, replace the local value with the HTTPS backend URL followed by `/api/v1`. CORS must allow the exact frontend origin and authentication requests must use HTTPS cookies.

## Pages and role workflows

- `index.html`: patient/staff sign-in; the role comes from the authenticated backend account.
- `register.html`: creates a PATIENT account; public registration cannot choose a privileged role.
- `patient.html`: own profile, requests, schedules, report files and doctor consent.
- `doctor.html`: patients with current assignment and consent, one-test request creation, requests, reports and catalogue.
- `lab-technician.html`: assigned queue, status transitions, result versions, approved corrections and PDF report generation.
- `admin.html`: accounts, assignments, catalogue, request scheduling/rescheduling, correction approval and audit events.

The direct example-account preview uses static synthetic records and does not call the API. Its dashboard is marked **Read-only demo preview**; actions and report downloads are disabled. Normal sign-in, account creation and dashboards continue to use authenticated API responses. To test those paths, start the backend and use the optional synthetic-only account seed described in its guide. No real patient information should be entered.

## Session and report safety

Access and refresh cookies are HttpOnly; the frontend does not write authentication tokens into browser storage. The tab-only demo preview stores only its synthetic role/profile selector in `sessionStorage` and removes it on sign-out. JavaScript API requests include credentials and a CSRF header. PDF files are downloaded through protected API routes; filesystem paths and public report URLs are not exposed.

The local academic prototype is not for clinical use. Production deployment still needs private persistent report storage and approved retention/backup policies.
