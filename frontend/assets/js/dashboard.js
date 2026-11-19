(function () {
  "use strict";
  const ui = window.MediCoreUI;
  const api = window.MediCoreApi;
  const roleSlug = document.body.dataset.role;
  const root = document.getElementById("main-content");
  const roleRoute = { PATIENT: "patient.html", DOCTOR: "doctor.html", LAB_TECHNICIAN: "lab-technician.html", ADMIN: "admin.html" };
  const roleName = { PATIENT: "Patient", DOCTOR: "Doctor", LAB_TECHNICIAN: "Laboratory Technician", ADMIN: "Administrator" };
  const expectedRole = { patient: "PATIENT", doctor: "DOCTOR", "lab-technician": "LAB_TECHNICIAN", admin: "ADMIN" }[roleSlug];
  const previewSessionKey = "medicore.previewSession";
  let previewSession = null;
  try { previewSession = JSON.parse(sessionStorage.getItem(previewSessionKey) || "null"); } catch (ignore) { previewSession = null; }
  if (!previewSession || !previewSession.role) previewSession = null;
  const esc = ui.escapeHtml;
  const state = { user: null, profile: null, patients: [], requests: [], tests: [], reports: [], assignments: [], users: [], corrections: [], audit: [], requestFilter: {} };

  function initials(name) { return String(name || "MC").trim().split(/\s+/).slice(0, 2).map(function (part) { return part[0] || ""; }).join("").toUpperCase(); }
  function avatar(role) { return role === "DOCTOR" ? "avatar-blue" : role === "LAB_TECHNICIAN" ? "avatar-orange" : role === "ADMIN" ? "avatar-lilac" : ""; }
  function date(value) { return value ? String(value).slice(0, 10) : "—"; }
  function dateTime(value) { return value ? String(value).replace("T", " ").slice(0, 16) : "—"; }
  function idLabel(prefix, id) { return prefix + "-" + String(id).padStart(4, "0"); }
  function button(label, action, id, style) {
    return '<button class="' + (style || "table-action") + '" type="button" data-action="' + esc(action) + '" data-id="' + esc(id) + '">' + esc(label) + '</button>';
  }
  function table(headers, rows, emptyText) {
    const body = rows.length ? rows.join("") : '<tr><td class="table-empty" colspan="' + headers.length + '">' + esc(emptyText || "No records to show.") + '</td></tr>';
    return '<div class="table-wrap"><table class="data-table"><thead><tr>' + headers.map(function (h) { return '<th>' + esc(h) + '</th>'; }).join("") + '</tr></thead><tbody>' + body + '</tbody></table></div>';
  }
  function card(title, subtitle, content, id, action) {
    return '<section class="card section-anchor"' + (id ? ' id="' + esc(id) + '"' : "") + '>' + ui.cardHeader(title, subtitle || "", action || "", action ? "#" + id : "") + content + '</section>';
  }
  function heading(title, description, actions) {
    const freshness = previewSession ? "DEMO PREVIEW · Synthetic sample" : "Live account data";
    return ui.heading(roleSlug, title, description, (actions || "") + '<span class="updated-note">' + freshness + '</span>');
  }
  function metrics(items) { return ui.metricGrid(items); }
  function empty(message) { return '<div class="api-empty"><strong>' + esc(message) + '</strong></div>'; }
  function requestFilterForm(includeTest, tests) {
    const filters = state.requestFilter;
    const selected = function (key, value) { return filters[key] === value ? " selected" : ""; };
    const testFilter = includeTest ? '<label class="data-field">Medical test<select name="testId"><option value="">All tests</option>' +
      (tests || []).map(function (test) { return '<option value="' + esc(test.id) + '"' + selected("testId", String(test.id)) + '>' + esc(test.name) + '</option>'; }).join("") + '</select></label>' : "";
    return '<form class="data-form request-filter-form" data-form="request-filter"><div class="data-form-grid">' +
      '<label class="data-field">Search requests<input name="q" type="search" maxlength="160" value="' + esc(filters.q || "") + '" placeholder="Request ID, patient or test"></label>' +
      '<label class="data-field">Request status<select name="status"><option value=""' + selected("status", "") + '>All statuses</option><option value="REQUESTED"' + selected("status", "REQUESTED") + '>Requested</option><option value="SCHEDULED"' + selected("status", "SCHEDULED") + '>Scheduled</option><option value="IN_PROGRESS"' + selected("status", "IN_PROGRESS") + '>In progress</option><option value="COMPLETED"' + selected("status", "COMPLETED") + '>Completed</option><option value="REPORT_GENERATED"' + selected("status", "REPORT_GENERATED") + '>Report ready</option></select></label>' +
      testFilter + '</div><div class="data-form-grid"><label class="data-field">Requested from<input name="from" type="date" value="' + esc(filters.from || "") + '"></label>' +
      '<label class="data-field">Requested through<input name="to" type="date" value="' + esc(filters.to || "") + '"></label></div>' +
      '<div class="data-form-actions"><button class="button button-secondary button-small" type="submit">Search and filter</button>' +
      '<button class="button button-secondary button-small" type="reset">Clear filters</button></div></form>';
  }
  function profileForm(user) {
    return '<form class="data-form" data-form="profile"><label class="data-field">Full name<input name="name" maxlength="120" value="' + esc(user.name) + '" required></label>' +
      '<div class="data-form-grid"><label class="data-field">Age<input name="age" type="number" min="1" max="120" value="' + esc(user.age || "") + '" required></label><label class="data-field">Phone<input name="phone" maxlength="32" value="' + esc(user.phone || "") + '"></label></div>' +
      '<label class="data-field">Gender<input name="gender" maxlength="64" value="' + esc(user.gender || "") + '"></label><label class="data-field">Address<input name="address" maxlength="255" value="' + esc(user.address || "") + '"></label>' +
      '<div class="data-form-actions"><button class="button button-primary button-small" type="submit">Save profile</button></div></form>';
  }
  function doctorProfileForm(user) {
    return '<form class="data-form" data-form="doctor-profile"><label class="data-field">Full name<input name="name" maxlength="120" value="' + esc(user.name) + '" required></label><label class="data-field">Specialization<input name="specialization" maxlength="160" value="' + esc(user.specialization || "") + '" required></label><div class="data-form-actions"><button class="button button-primary button-small" type="submit">Save profile</button></div></form>';
  }
  function requestRows(requests, includePatient, allowActions) {
    return requests.map(function (r) {
      let actions = button("Results", "view-results", r.id);
      if (allowActions && roleSlug === "lab-technician") {
        if (r.status === "SCHEDULED") actions = button("Start test", "start", r.id, "queue-action");
        if (r.status === "IN_PROGRESS") actions = button("Enter result", "enter-result", r.id, "queue-action");
        if (r.status === "COMPLETED" || r.status === "REPORT_GENERATED") {
          const approved = state.corrections.find(function (c) { return c.requestId === r.id && c.status === "APPROVED"; });
          const pending = state.corrections.some(function (c) { return c.requestId === r.id && c.status === "PENDING"; });
          const reports = state.reports.filter(function (report) { return report.requestId === r.id; });
          const current = reports.some(function (report) { return report.state === "CURRENT"; });
          actions = (approved ? button("Apply approved correction", "apply-correction", approved.id) : (pending ? '<span class="muted">Correction pending</span>' : button("Request correction", "request-correction", r.id))) +
            (current ? "" : button(reports.length ? "Reissue PDF" : "Generate PDF", "generate-report", r.id));
        }
      }
      if (roleSlug === "admin") {
        actions = r.status === "REQUESTED" ? button("Schedule", "schedule", r.id) :
          r.status === "SCHEDULED" ? button("Reschedule", "reschedule", r.id) : button("Results", "view-results", r.id);
      }
      const cells = ['<td><span class="table-id">' + esc(idLabel("TR", r.id)) + '</span></td>'];
      if (includePatient) cells.push('<td><span class="table-primary">' + esc(r.patientName) + '</span></td>');
      cells.push('<td><span class="table-primary">' + esc(r.testName) + '</span></td>');
      cells.push('<td>' + esc(date(r.requestDate)) + '</td>');
      cells.push('<td>' + esc(r.scheduledDate ? date(r.scheduledDate) + " " + String(r.scheduledTime || "").slice(0, 5) : "Not scheduled") + '</td>');
      cells.push('<td>' + ui.badge(r.status) + '</td>');
      cells.push('<td>' + actions + '</td>');
      return '<tr>' + cells.join("") + '</tr>';
    });
  }
  function reportRows(reports) {
    return reports.map(function (report) {
      const request = state.requests.find(function (r) { return r.id === report.requestId; });
      return '<tr><td><span class="table-id">' + esc(idLabel("RP", report.id)) + '</span></td><td>' + esc(request ? request.testName : idLabel("TR", report.requestId)) + '</td><td>Version ' + esc(report.reportVersion) + '</td><td>' + esc(dateTime(report.reportDate)) + '</td><td>' + (report.state === "CURRENT" ? ui.badge("REPORT_GENERATED") : '<span class="status-badge status-neutral">Superseded</span>') + '</td><td>' + button("Download PDF", "download-report", report.id) + '</td></tr>';
    });
  }
  function testRows(tests, admin) {
    return tests.map(function (test) {
      const action = admin ? button(test.active ? "Deactivate" : "Activate", "toggle-test", test.id) : "—";
      return '<tr><td><span class="table-primary">' + esc(test.name) + '</span><span class="table-secondary">' + esc(test.description || "") + '</span></td><td>' + esc(test.referenceRange || "Not specified") + '</td><td>' + esc(test.price) + '</td><td>' + (test.active ? '<span class="status-badge status-ready">Active</span>' : '<span class="status-badge status-neutral">Inactive</span>') + '</td><td>' + action + '</td></tr>';
    });
  }
  function patientView() {
    const profile = state.profile || state.user;
    const upcoming = state.requests.filter(function (r) { return r.status === "SCHEDULED" || r.status === "IN_PROGRESS"; }).slice(0, 4);
    const assignmentRows = state.assignments.map(function (a) {
      let access = a.revokedAt ? '<span class="status-badge status-neutral">Assignment revoked</span>' : a.consentActive ? '<span class="status-badge status-ready">Consent active</span>' : '<span class="status-badge status-review">Consent needed</span>';
      let action = a.revokedAt ? "—" : a.consentActive ? button("Withdraw consent", "withdraw-consent", a.id, "inline-action inline-action-danger") : button("Review and grant consent", "grant-consent", a.id, "inline-action");
      return '<tr><td>' + esc(a.doctorName) + '</td><td>' + access + '</td><td>' + esc(date(a.assignedAt)) + '</td><td>' + action + '</td></tr>';
    });
    const requestTable = requestFilterForm(false) + table(["Request", "Test", "Requested", "Schedule", "Status", "Details"], requestRows(state.requests, false, false), "No test requests have been created for this account.");
    const upcomingRows = upcoming.map(function (r) { return '<tr><td>' + esc(idLabel("TR", r.id)) + '</td><td>' + esc(r.testName) + '</td><td>' + esc(date(r.scheduledDate)) + '</td><td>' + esc(String(r.scheduledTime || "").slice(0, 5)) + '</td><td>' + ui.badge(r.status) + '</td></tr>'; });
    return heading("Welcome, " + profile.name, "Review your requests, schedule, reports and doctor access.") + metrics([
      { label: "My requests", value: state.requests.length, foot: "Visible to your account", icon: "clipboard" },
      { label: "Upcoming tests", value: upcoming.length, foot: "Scheduled or in progress", icon: "calendar" },
      { label: "Reports", value: state.reports.length, foot: "Current and prior versions", icon: "file" }
    ]) + '<div class="content-grid"><div class="stack">' +
      card("My test requests", "Status and schedule returned by MediCore API.", requestTable, "requests") +
      card("Upcoming schedule", "Appointments for your account.", table(["Request", "Test", "Date", "Time", "Status"], upcomingRows, "There are no scheduled tests."), "schedule") +
      card("My reports", "PDF files are downloaded through protected backend access.", table(["Report", "Test", "Version", "Generated", "State", "File"], reportRows(state.reports), "No reports are available yet."), "reports") +
      '</div><div class="stack">' +
      card("Doctor access", "Access requires both an active assignment and your consent.", table(["Doctor", "Access", "Assigned", "Action"], assignmentRows, "No doctor assignments are recorded."), "assignments") +
      card("My profile", "Only your own profile fields can be changed.", '<p class="field-hint">Account email: ' + esc(profile.email) + '</p>' + profileForm(profile), "profile") +
      '</div></div>';
  }
  function doctorView() {
    const patientOptions = state.patients.map(function (p) { return '<option value="' + esc(p.patientId) + '">' + esc(p.name) + ' · #' + esc(p.patientId) + '</option>'; }).join("");
    const testOptions = state.tests.filter(function (t) { return t.active; }).map(function (t) { return '<option value="' + esc(t.id) + '">' + esc(t.name) + '</option>'; }).join("");
    const patients = state.patients.map(function (p) { return '<div class="patient-card-row"><span class="avatar ' + avatar("PATIENT") + '">' + esc(initials(p.name)) + '</span><span class="row-copy"><strong>' + esc(p.name) + '</strong><small>Patient #' + esc(p.patientId) + ' · assignment and consent active</small></span></div>'; });
    const requestForm = '<form class="data-form" data-form="doctor-request"><label class="data-field">Authorized patient<select name="patientId" required><option value="">Choose a patient</option>' + patientOptions + '</select></label><label class="data-field">Medical test<select name="testId" required><option value="">Choose one test</option>' + testOptions + '</select></label><div class="field-hint">Each request contains one medical test.</div><div class="data-form-actions"><button class="button button-primary button-small" type="submit">Create test request</button></div></form>';
    return heading("Welcome, " + state.user.name, "Only patients with active assignment and recorded consent are listed.") + metrics([
      { label: "Authorized patients", value: state.patients.length, foot: "Assignment + consent active", icon: "users" },
      { label: "Requests", value: state.requests.length, foot: "Requests in your scope", icon: "clipboard" },
      { label: "Reports", value: state.reports.length, foot: "Available while access remains active", icon: "file" }
    ]) + '<div class="content-grid"><div class="stack">' +
      card("Create a test request", "Choose one authorized patient and one active test.", requestForm, "new-request") +
      card("My test requests", "Patient data appears only while authorization remains active.", requestFilterForm(true, state.tests) + table(["Request", "Patient", "Test", "Requested", "Schedule", "Status", "Details"], requestRows(state.requests, true, false), "No test requests have been created."), "requests") +
      card("Authorized reports", "Access is checked again for every PDF download.", table(["Report", "Test", "Version", "Generated", "State", "File"], reportRows(state.reports), "No reports are available."), "reports") +
      '</div><div class="stack">' + card("Authorized patients", "Revoked assignment or consent removes future access immediately.", '<div class="patient-card-list">' + (patients.join("") || empty("No authorized patients are available.")) + '</div>', "patients") +
      card("Active test catalogue", "One medical test per request.", table(["Test", "Reference range", "Price", "State"], state.tests.filter(function (t) { return t.active; }).map(function (t) { return '<tr><td>' + esc(t.name) + '</td><td>' + esc(t.referenceRange || "Not specified") + '</td><td>' + esc(t.price) + '</td><td><span class="status-badge status-ready">Active</span></td></tr>'; }), "There are no active tests."), "catalogue") +
      card("My profile", "Your account profile.", '<p class="field-hint">' + esc(state.user.email) + '</p>' + doctorProfileForm(state.user), "profile") + '</div></div>';
  }
  function technicianView() {
    const rows = requestRows(state.requests, true, true);
    const correctionRows = state.corrections.map(function (c) {
      const action = c.status === "APPROVED" ? button("Apply correction", "apply-correction", c.id) : '<span class="status-badge ' + (c.status === "REJECTED" ? "status-neutral" : "status-review") + '">' + esc(c.status) + '</span>';
      return '<tr><td>' + esc(idLabel("TR", c.requestId)) + '</td><td>' + esc(c.reason) + '</td><td>' + esc(dateTime(c.requestedAt)) + '</td><td>' + action + '</td></tr>';
    });
    const scheduleRows = state.requests.filter(function (r) { return r.scheduledDate; }).map(function (r) { return '<tr><td>' + esc(date(r.scheduledDate)) + '</td><td>' + esc(String(r.scheduledTime || "").slice(0, 5)) + '</td><td>' + esc(r.patientName) + '</td><td>' + esc(r.testName) + '</td><td>' + ui.badge(r.status) + '</td></tr>'; });
    return heading("Welcome, " + state.user.name, "Your queue contains only requests assigned to your account.") + metrics([
      { label: "Assigned requests", value: state.requests.length, foot: "Current assignment scope", icon: "clipboard" },
      { label: "Scheduled", value: state.requests.filter(function (r) { return r.status === "SCHEDULED"; }).length, foot: "Ready to start", icon: "calendar" },
      { label: "In progress", value: state.requests.filter(function (r) { return r.status === "IN_PROGRESS"; }).length, foot: "Awaiting result entry", icon: "activity" }
    ]) + '<div class="content-grid"><div class="stack">' +
      card("Assigned work queue", "Start scheduled work, enter results, request corrections and generate PDFs.", requestFilterForm(false) + table(["Request", "Patient", "Test", "Requested", "Schedule", "Status", "Action"], rows, "No requests are assigned to you."), "work-queue") +
      card("My correction requests", "Completed-result changes require an administrator decision.", table(["Request", "Reason", "Requested", "Status / action"], correctionRows, "No correction requests are recorded."), "corrections") +
      '</div><div class="stack">' + card("My schedule", "Schedule details for your assigned work.", table(["Date", "Time", "Patient", "Test", "Status"], scheduleRows, "No tests are scheduled."), "schedule") +
      card("Reports", "Generate or reissue reports from the latest result version.", table(["Report", "Test", "Version", "Generated", "State", "File"], reportRows(state.reports), "No report versions are available."), "reports") + '</div></div>';
  }
  function adminView() {
    const techs = state.users.filter(function (u) { return u.role === "LAB_TECHNICIAN" && u.enabled; });
    const doctors = state.users.filter(function (u) { return u.role === "DOCTOR" && u.enabled && u.doctorId; });
    const patients = state.users.filter(function (u) { return u.role === "PATIENT" && u.enabled && u.patientId; });
    const userRows = state.users.map(function (u) {
      return '<tr><td>' + esc(u.name) + '<span class="table-secondary">' + esc(u.email) + '</span></td><td>' + esc(roleName[u.role] || u.role) + '</td><td>' + (u.enabled ? '<span class="status-badge status-ready">Enabled</span>' : '<span class="status-badge status-neutral">Disabled</span>') + '</td><td>' + button(u.enabled ? "Disable" : "Enable", "toggle-user", u.id) + '</td></tr>';
    });
    const assignmentRows = state.assignments.map(function (a) {
      return '<tr><td>' + esc(a.doctorName) + '</td><td>Patient #' + esc(a.patientId) + '</td><td>' + esc(date(a.assignedAt)) + '</td><td>' + (a.revokedAt ? '<span class="status-badge status-neutral">Revoked</span>' : '<span class="status-badge status-ready">Active</span>') + '</td><td>' + (!a.revokedAt ? button("Revoke", "revoke-assignment", a.id, "inline-action inline-action-danger") : "—") + '</td></tr>';
    });
    const correctionRows = state.corrections.map(function (c) {
      const actions = c.status === "PENDING" ? button("Approve", "approve-correction", c.id) + button("Reject", "reject-correction", c.id, "inline-action inline-action-danger") : '<span class="status-badge ' + (c.status === "APPROVED" ? "status-ready" : "status-neutral") + '">' + esc(c.status) + '</span>';
      return '<tr><td>' + esc(idLabel("TR", c.requestId)) + '</td><td>' + esc(c.reason) + '</td><td>' + esc(dateTime(c.requestedAt)) + '</td><td>' + actions + '</td></tr>';
    });
    const auditRows = state.audit.map(function (a) { return '<tr><td>' + esc(dateTime(a.occurredAt)) + '</td><td>' + esc(a.action) + '</td><td>' + esc(a.entityType) + ' ' + esc(a.entityId || "") + '</td><td>' + esc(a.actorUserId || "System/unknown") + '</td></tr>'; });
    const createUser = '<form class="data-form" data-form="create-user"><div class="data-form-grid"><label class="data-field">Full name<input name="name" maxlength="120" required></label><label class="data-field">Email<input name="email" type="email" maxlength="254" required></label></div><div class="data-form-grid"><label class="data-field">Temporary password<input name="password" type="password" minlength="10" maxlength="72" required></label><label class="data-field">Role<select name="role" required><option value="DOCTOR">Doctor</option><option value="LAB_TECHNICIAN">Laboratory Technician</option><option value="ADMIN">Administrator</option><option value="PATIENT">Patient</option></select></label></div><div class="data-form-grid"><label class="data-field">Doctor specialization<input name="specialization" maxlength="160" required></label><label class="data-field">Patient age<input name="age" type="number" min="1" max="120"></label></div><div class="data-form-grid"><label class="data-field">Phone<input name="phone" maxlength="32"></label><label class="data-field">Address<input name="address" maxlength="255"></label></div><div class="field-hint">Share temporary credentials through a private channel and have the account owner change them. Use synthetic identities.</div><div class="data-form-actions"><button class="button button-primary button-small" type="submit">Create account</button></div></form>';
    const assignmentForm = '<form class="data-form" data-form="assignment"><label class="data-field">Doctor<select name="doctorId" required><option value="">Choose doctor</option>' + doctors.map(function (u) { return '<option value="' + esc(u.doctorId) + '">' + esc(u.name) + '</option>'; }).join("") + '</select></label><label class="data-field">Patient<select name="patientId" required><option value="">Choose patient</option>' + patients.map(function (u) { return '<option value="' + esc(u.patientId) + '">' + esc(u.name) + '</option>'; }).join("") + '</select></label><div class="field-hint">The patient must record consent before the doctor can see protected records.</div><div class="data-form-actions"><button class="button button-primary button-small" type="submit">Assign doctor to patient</button></div></form>';
    const addTest = '<form class="data-form" data-form="create-test"><label class="data-field">Test name<input name="name" maxlength="160" required></label><label class="data-field">Description<textarea name="description" maxlength="4000"></textarea></label><div class="data-form-grid"><label class="data-field">Price<input name="price" type="number" min="0" step="0.01" required></label><label class="data-field">Reference range<input name="referenceRange" maxlength="255"></label></div><div class="data-form-actions"><button class="button button-primary button-small" type="submit">Add test</button></div></form>';
    const requestFilter = requestFilterForm(true, state.tests);
    return heading("Administration", "Manage accounts, doctor access, the catalogue and laboratory workflow.") + metrics([
      { label: "User accounts", value: state.users.length, foot: "All roles", icon: "users" },
      { label: "Test requests", value: state.requests.length, foot: "All lifecycle states", icon: "clipboard" },
      { label: "Active tests", value: state.tests.filter(function (t) { return t.active; }).length, foot: "Catalogue entries", icon: "flask" },
      { label: "Pending corrections", value: state.corrections.filter(function (c) { return c.status === "PENDING"; }).length, foot: "Administrator decision required", icon: "shield" }
    ]) + '<div class="content-grid"><div class="stack">' +
      card("Test request monitoring", "Search, schedule or reschedule a request before processing starts.", requestFilter + '<div class="section-spacer">' + table(["Request", "Patient", "Test", "Requested", "Schedule", "Status", "Action"], requestRows(state.requests, true, false).map(function (row) { return row.replace('</td></tr>', '</td></tr>'); }), "No requests match the current filter.") + '</div>', "requests") +
      card("User accounts", "Provision or disable accounts. Disabling revokes sessions.", createUser + '<div class="section-spacer">' + table(["Account", "Role", "State", "Action"], userRows, "No accounts exist.") + '</div>', "accounts") +
      card("Doctor-patient assignments", "Assignment alone does not grant doctor access; patient consent is also required.", assignmentForm + '<div class="section-spacer">' + table(["Doctor", "Patient", "Assigned", "State", "Action"], assignmentRows, "No assignments exist.") + '</div>', "assignments") +
      '</div><div class="stack">' +
      card("Medical-test catalogue", "Deactivate tests that have existing request history.", addTest + '<div class="section-spacer">' + table(["Test", "Reference range", "Price", "State", "Action"], testRows(state.tests, true), "No tests exist.") + '</div>', "catalogue") +
      card("Completed-result corrections", "Approve or reject a technician’s reasoned correction request.", table(["Request", "Reason", "Requested", "Decision"], correctionRows, "No correction requests are pending."), "corrections") +
      card("Audit activity", "Append-only workflow and security events; sensitive result contents are excluded.", table(["Time", "Action", "Target", "Actor"], auditRows, "No audit entries are available."), "audit-log") +
      '</div></div>';
  }

  function render() {
    const currentRole = state.user.role;
    const displayUser = { id: state.user.id, name: state.user.name, initials: initials(state.user.name), avatar: avatar(currentRole) };
    ui.mountDashboard(roleSlug, displayUser);
    const html = currentRole === "PATIENT" ? patientView() : currentRole === "DOCTOR" ? doctorView() : currentRole === "LAB_TECHNICIAN" ? technicianView() : adminView();
    const previewNotice = previewSession ? '<div class="demo-preview-notice" role="status"><strong>Read-only demo preview</strong><span>These synthetic examples are stored in this page only. Changes and report downloads are disabled.</span></div>' : "";
    const dataLabel = previewSession ? "Synthetic preview data · Not connected to backend" : "Live API data · Synthetic information only";
    root.innerHTML = previewNotice + html + '<footer class="dashboard-footer"><span>MediCore MTMS · Local academic prototype</span><span>' + dataLabel + '</span></footer>';
    if (!document.getElementById("action-dialog")) document.body.insertAdjacentHTML("beforeend", '<dialog id="action-dialog" class="data-modal"></dialog>');
  }

  function loadPreviewData() {
    const defaultUsers = {
      PATIENT: { id: 2048, patientId: 2048, role: "PATIENT", name: "Nora Patel", email: "nora.patel@example.test", age: 28, gender: "Not specified", phone: "555-0101", address: "12 Sample Lane" },
      DOCTOR: { id: 14, doctorId: 14, role: "DOCTOR", name: "Dr. Mina Shah", email: "mina.shah@example.test", specialization: "Family Medicine" },
      LAB_TECHNICIAN: { id: 22, role: "LAB_TECHNICIAN", name: "Jordan Lee", email: "jordan.lee@example.test" },
      ADMIN: { id: 3, role: "ADMIN", name: "Avery Morgan", email: "avery.morgan@example.test" }
    };
    state.user = Object.assign({}, defaultUsers[expectedRole]);
    if (expectedRole === "PATIENT" && previewSession.profile) state.user = Object.assign(state.user, previewSession.profile, { role: "PATIENT", id: 2099, patientId: 2099 });
    state.profile = state.user;
    const allUsers = [
      defaultUsers.PATIENT,
      { id: 1935, patientId: 1935, role: "PATIENT", name: "Theo Brooks", email: "theo.brooks@example.test", age: 34, enabled: true },
      { id: 2071, patientId: 2071, role: "PATIENT", name: "Elena Cruz", email: "elena.cruz@example.test", age: 42, enabled: true },
      Object.assign({}, defaultUsers.DOCTOR, { enabled: true }),
      Object.assign({}, defaultUsers.LAB_TECHNICIAN, { enabled: true }),
      Object.assign({}, defaultUsers.ADMIN, { enabled: true })
    ];
    const tests = [
      { id: 1, name: "Complete Blood Count", description: "Counts of common blood cells", price: 24.00, referenceRange: "See individual measurements", active: true },
      { id: 2, name: "Lipid Panel", description: "Cholesterol and triglycerides", price: 32.00, referenceRange: "Total cholesterol < 200 mg/dL", active: true },
      { id: 3, name: "Vitamin D", description: "25-hydroxy vitamin D", price: 28.00, referenceRange: "30–100 ng/mL", active: true },
      { id: 4, name: "Metabolic Panel", description: "Common metabolic markers", price: 36.00, referenceRange: "See individual measurements", active: true },
      { id: 5, name: "Thyroid Panel", description: "Thyroid function markers", price: 30.00, referenceRange: "See individual measurements", active: true }
    ];
    const allRequests = [
      { id: 4291, patientId: 2048, patientName: "Nora Patel", testId: 1, testName: "Complete Blood Count", requestDate: "2026-11-18", scheduledDate: "2026-11-20", scheduledTime: "09:30:00", status: "IN_PROGRESS", technicianId: 22 },
      { id: 4278, patientId: 2048, patientName: "Nora Patel", testId: 2, testName: "Lipid Panel", requestDate: "2026-11-16", scheduledDate: "2026-11-21", scheduledTime: "09:30:00", status: "SCHEDULED", technicianId: 22 },
      { id: 4183, patientId: 2048, patientName: "Nora Patel", testId: 3, testName: "Vitamin D", requestDate: "2026-11-04", scheduledDate: null, scheduledTime: null, status: "REPORT_GENERATED", technicianId: 22 },
      { id: 4139, patientId: 1935, patientName: "Theo Brooks", testId: 5, testName: "Thyroid Panel", requestDate: "2026-10-20", scheduledDate: null, scheduledTime: null, status: "REPORT_GENERATED", technicianId: 22 },
      { id: 4284, patientId: 1935, patientName: "Theo Brooks", testId: 4, testName: "Metabolic Panel", requestDate: "2026-11-17", scheduledDate: "2026-11-19", scheduledTime: "11:00:00", status: "COMPLETED", technicianId: 22 },
      { id: 4302, patientId: 2071, patientName: "Elena Cruz", testId: 5, testName: "Thyroid Panel", requestDate: "2026-11-19", scheduledDate: "2026-11-20", scheduledTime: "10:15:00", status: "SCHEDULED", technicianId: 22 },
      { id: 4307, patientId: 1935, patientName: "Theo Brooks", testId: 4, testName: "Metabolic Panel", requestDate: "2026-11-19", scheduledDate: "2026-11-20", scheduledTime: "11:00:00", status: "SCHEDULED", technicianId: 22 },
      { id: 4310, patientId: 2071, patientName: "Elena Cruz", testId: 2, testName: "Lipid Panel", requestDate: "2026-11-19", scheduledDate: "2026-11-20", scheduledTime: "13:30:00", status: "SCHEDULED", technicianId: 22 },
      { id: 4312, patientId: 2048, patientName: "Nora Patel", testId: 3, testName: "Vitamin D", requestDate: "2026-11-19", scheduledDate: null, scheduledTime: null, status: "REQUESTED", technicianId: null }
    ];
    if (expectedRole === "PATIENT" && previewSession.profile) {
      allRequests.forEach(function (request) {
        if (request.patientId === 2048) { request.patientId = state.user.patientId; request.patientName = state.user.name; }
      });
      allUsers[0] = state.user;
    }
    const patientIds = expectedRole === "PATIENT" ? [state.user.patientId] : expectedRole === "DOCTOR" ? [2048, 1935, 2071] : null;
    let visibleRequests = expectedRole === "LAB_TECHNICIAN" ? allRequests.filter(function (request) { return request.technicianId === 22; }) :
      expectedRole === "ADMIN" ? allRequests : allRequests.filter(function (request) { return patientIds.indexOf(request.patientId) >= 0; });
    const filters = state.requestFilter;
    visibleRequests = visibleRequests.filter(function (request) {
      const query = String(filters.q || "").trim().toLowerCase();
      const matchesQuery = !query || String(request.id).includes(query.replace(/^tr-/, "")) || request.patientName.toLowerCase().includes(query) || request.testName.toLowerCase().includes(query);
      return matchesQuery && (!filters.status || request.status === filters.status) && (!filters.testId || String(request.testId) === String(filters.testId)) && (!filters.from || request.requestDate >= filters.from) && (!filters.to || request.requestDate <= filters.to);
    });
    state.requests = visibleRequests;
    state.tests = tests;
    const reportRecords = [
      { id: 1084, requestId: 4183, reportVersion: 1, reportDate: "2026-11-04T11:20:00", state: "CURRENT" },
      { id: 1041, requestId: 4139, reportVersion: 1, reportDate: "2026-10-20T14:10:00", state: "CURRENT" }
    ];
    const permittedPatientIds = expectedRole === "PATIENT" ? [state.user.patientId] : expectedRole === "DOCTOR" ? [2048, 1935, 2071] : null;
    state.reports = reportRecords.filter(function (report) {
      if (expectedRole === "ADMIN" || expectedRole === "LAB_TECHNICIAN") return true;
      const request = allRequests.find(function (item) { return item.id === report.requestId; });
      return request && permittedPatientIds.indexOf(request.patientId) >= 0;
    });
    state.patients = expectedRole === "DOCTOR" ? allUsers.filter(function (user) { return user.role === "PATIENT"; }).map(function (user) { return { patientId: user.patientId, name: user.name }; }) : [];
    const assignmentRecords = [
      { id: 301, doctorId: 14, doctorName: "Dr. Mina Shah", patientId: 2048, assignedAt: "2026-09-01", revokedAt: null, consentActive: true },
      { id: 302, doctorId: 14, doctorName: "Dr. Mina Shah", patientId: 1935, assignedAt: "2026-09-12", revokedAt: null, consentActive: true },
      { id: 303, doctorId: 14, doctorName: "Dr. Mina Shah", patientId: 2071, assignedAt: "2026-10-03", revokedAt: null, consentActive: true }
    ];
    if (expectedRole === "PATIENT" && previewSession.profile) assignmentRecords[0].patientId = state.user.patientId;
    state.assignments = expectedRole === "PATIENT" ? assignmentRecords.filter(function (item) { return item.patientId === state.user.patientId; }) : assignmentRecords;
    state.users = allUsers.map(function (user) { return Object.assign({ enabled: true }, user); });
    state.corrections = [{ id: 7101, requestId: 4284, reason: "Synthetic correction request awaiting review", requestedAt: "2026-11-19T15:20:00", status: "PENDING" }];
    state.audit = [
      { occurredAt: "2026-11-20T08:12:00", action: "TEST_SCHEDULED", entityType: "TEST_REQUEST", entityId: "4278", actorUserId: "3" },
      { occurredAt: "2026-11-20T08:03:00", action: "RESULT_ENTERED", entityType: "TEST_RESULT", entityId: "4284", actorUserId: "22" },
      { occurredAt: "2026-11-19T16:44:00", action: "REPORT_GENERATED", entityType: "REPORT", entityId: "1084", actorUserId: "22" }
    ];
  }

  async function loadData() {
    if (previewSession) { loadPreviewData(); return; }
    const requestsPath = expectedRole === "ADMIN" ? "/admin/test-requests" : expectedRole === "LAB_TECHNICIAN" ? "/lab/work-queue" : "/test-requests";
    const requestParams = new URLSearchParams(state.requestFilter);
    requestParams.set("page", "0"); requestParams.set("size", "100");
    const requestData = await api.get(requestsPath + "?" + requestParams.toString());
    state.requests = requestData.content || [];
    if (expectedRole === "PATIENT") {
      const data = await Promise.all([api.get("/patients/me"), api.get("/reports"), api.get("/patients/me/doctor-assignments")]);
      state.profile = data[0]; state.reports = data[1] || []; state.assignments = data[2] || [];
    } else if (expectedRole === "DOCTOR") {
      const data = await Promise.all([api.get("/doctors/me"), api.get("/doctors/me/patients"), api.get("/reports"), api.get("/medical-tests")]);
      state.profile = data[0]; state.patients = data[1] || []; state.reports = data[2] || []; state.tests = data[3] || [];
    } else if (expectedRole === "LAB_TECHNICIAN") {
      const data = await Promise.all([api.get("/reports"), api.get("/lab/result-correction-requests")]);
      state.reports = data[0] || []; state.corrections = data[1] || [];
    } else {
      const data = await Promise.all([api.get("/admin/users"), api.get("/medical-tests"), api.get("/admin/doctor-patient-assignments"), api.get("/admin/result-correction-requests"), api.get("/admin/audit-logs")]);
      state.users = data[0] || []; state.tests = data[1] || []; state.assignments = data[2] || []; state.corrections = data[3] || []; state.audit = data[4] || [];
    }
  }

  function openModal(title, description, formHtml) {
    const dialog = document.getElementById("action-dialog");
    dialog.innerHTML = '<div class="data-modal-inner"><div class="data-modal-header"><div><h2>' + esc(title) + '</h2><p>' + esc(description || "") + '</p></div><button type="button" class="icon-button" data-action="close-modal" aria-label="Close dialog">×</button></div>' + formHtml + '</div>';
    if (typeof dialog.showModal === "function") dialog.showModal(); else dialog.setAttribute("open", "open");
  }
  function closeModal() {
    const dialog = document.getElementById("action-dialog");
    if (dialog && dialog.open) dialog.close();
  }
  function showResults(requestId, results) {
    const content = results.length ? results.map(function (r) {
      return '<div class="result-detail"><strong>Result version ' + esc(r.versionNo) + ' · ' + esc(date(r.resultDate)) + '</strong><p>' + esc(r.resultValue) + '</p><p>Remarks: ' + esc(r.remarks || "None") + '</p><small>Entered by ' + esc(r.enteredByName) + ' · ' + esc(dateTime(r.enteredAt)) + (r.correctionReason ? ' · Correction: ' + esc(r.correctionReason) : "") + '</small></div>';
    }).join("") : empty("No result has been entered.");
    openModal("Result history · " + idLabel("TR", requestId), "All result versions are preserved.", content);
  }
  async function requestResults(requestId) {
    const results = await api.get("/test-requests/" + requestId + "/results");
    showResults(requestId, results || []);
  }
  async function showRequestFilter(form) {
    const data = new FormData(form);
    state.requestFilter = {};
    ["q", "status", "testId", "from", "to"].forEach(function (key) { if (data.get(key)) state.requestFilter[key] = data.get(key); });
    await loadData();
    render();
  }
  function confirmationForConsent(assignment) {
    const text = "Consent text v1: I authorize " + assignment.doctorName + " to access my medical test requests, results and reports while this assignment remains active. I understand I can withdraw consent at any time; withdrawal immediately blocks future doctor access, including prior reports. Continue?";
    return window.confirm(text);
  }

  document.addEventListener("click", async function (event) {
    const target = event.target.closest("[data-action]");
    if (!target) return;
    if (previewSession) { ui.toast("Demo preview is read-only. Changes and downloads are not saved."); return; }
    const id = target.dataset.id;
    try {
      switch (target.dataset.action) {
        case "close-modal": closeModal(); return;
        case "view-results": await requestResults(id); return;
        case "download-report": {
          const blob = await api.download("/reports/" + id + "/file");
          const url = URL.createObjectURL(blob); const anchor = document.createElement("a");
          anchor.href = url; anchor.download = "MediCore-report-" + id + ".pdf"; anchor.click(); window.setTimeout(function () { URL.revokeObjectURL(url); }, 1000); return;
        }
        case "grant-consent": {
          const assignment = state.assignments.find(function (a) { return String(a.id) === String(id); });
          if (assignment && confirmationForConsent(assignment)) await api.post("/patients/me/doctor-assignments/" + id + "/consent", { consentTextVersion: window.MEDICORE_CONSENT_TEXT_VERSION || "v1" });
          break;
        }
        case "withdraw-consent":
          if (window.confirm("Withdraw consent? The doctor will immediately lose access to current and historical reports. Your records will remain stored.")) await api.post("/patients/me/doctor-assignments/" + id + "/withdraw-consent");
          break;
        case "start": await api.post("/test-requests/" + id + "/start"); break;
        case "enter-result": openResultForm(id); return;
        case "generate-report": await api.post("/test-requests/" + id + "/reports"); break;
        case "request-correction": await openCorrectionRequest(id); return;
        case "apply-correction": openCorrectionApplyForm(id); return;
        case "schedule": openScheduleForm(id, false); return;
        case "reschedule": openScheduleForm(id, true); return;
        case "approve-correction": {
          const note = window.prompt("Approval note (optional):", "Approved by administrator.");
          if (note === null) return;
          await api.post("/admin/result-correction-requests/" + id + "/approve", { note: note.trim() || null });
          break;
        }
        case "reject-correction": {
          const note = window.prompt("Reason for rejecting this correction request:");
          if (note && note.trim()) await api.post("/admin/result-correction-requests/" + id + "/reject", { note: note.trim() });
          break;
        }
        case "toggle-user": {
          const account = state.users.find(function (u) { return String(u.id) === String(id); });
          if (account && window.confirm((account.enabled ? "Disable" : "Enable") + " " + account.name + "?")) await api.patch("/admin/users/" + id, { enabled: !account.enabled });
          break;
        }
        case "toggle-test": {
          const test = state.tests.find(function (t) { return String(t.id) === String(id); });
          if (test) await api.patch("/medical-tests/" + id, { name: test.name, description: test.description, price: test.price, referenceRange: test.referenceRange, active: !test.active });
          break;
        }
        case "revoke-assignment":
          if (window.confirm("Revoke this doctor-patient assignment? Doctor access to protected records and reports will end immediately.")) await api.post("/admin/doctor-patient-assignments/" + id + "/revoke");
          break;
        default: return;
      }
      await loadData(); render(); ui.toast("Changes saved.");
    } catch (error) { ui.toast(error.message || "The action failed."); }
  });

  function field(label, name, type, value, required, attrs) {
    return '<label class="data-field">' + esc(label) + '<input name="' + esc(name) + '" type="' + esc(type || "text") + '" value="' + esc(value || "") + '"' + (required ? " required" : "") + ' ' + (attrs || "") + '></label>';
  }
  function openResultForm(requestId) {
    openModal("Enter laboratory result", "The result will be stored as a new result version.", '<form class="data-form" data-form="initial-result" data-id="' + esc(requestId) + '">' +
      '<label class="data-field">Result value<textarea name="resultValue" maxlength="10000" required></textarea></label><label class="data-field">Remarks<textarea name="remarks" maxlength="10000"></textarea></label>' + field("Result date", "resultDate", "date", new Date().toISOString().slice(0, 10), true) + '<div class="data-form-actions"><button class="button button-primary button-small" type="submit">Save result</button></div></form>');
  }
  async function openCorrectionRequest(requestId) {
    const results = await api.get("/test-requests/" + requestId + "/results");
    const latest = results[results.length - 1];
    if (!latest) { ui.toast("There is no completed result to correct."); return; }
    const report = state.reports.filter(function (r) { return r.requestId === Number(requestId) && r.state === "CURRENT"; })[0];
    openModal("Request result correction", "Administrator approval is required. The original result remains in history.", '<form class="data-form" data-form="correction-request" data-id="' + esc(requestId) + '"><input type="hidden" name="targetResultId" value="' + esc(latest.id) + '"><input type="hidden" name="targetReportId" value="' + esc(report ? report.id : "") + '"><label class="data-field">Correction reason<textarea name="reason" maxlength="1000" required></textarea></label><div class="data-form-actions"><button class="button button-primary button-small" type="submit">Send for approval</button></div></form>');
  }
  function openCorrectionApplyForm(correctionId) {
    openModal("Apply approved correction", "A new result version will be appended. Existing result and report history is preserved.", '<form class="data-form" data-form="apply-correction" data-id="' + esc(correctionId) + '"><label class="data-field">Corrected result value<textarea name="resultValue" maxlength="10000" required></textarea></label><label class="data-field">Remarks<textarea name="remarks" maxlength="10000"></textarea></label>' + field("Result date", "resultDate", "date", new Date().toISOString().slice(0, 10), true) + '<div class="data-form-actions"><button class="button button-primary button-small" type="submit">Append corrected result</button></div></form>');
  }
  function openScheduleForm(requestId, reschedule) {
    const technicians = state.users.filter(function (u) { return u.role === "LAB_TECHNICIAN" && u.enabled; });
    const technicianOptions = technicians.map(function (u) { return '<option value="' + esc(u.id) + '">' + esc(u.name) + ' · #' + esc(u.id) + '</option>'; }).join("");
    const request = state.requests.find(function (r) { return String(r.id) === String(requestId); });
    const form = '<form class="data-form" data-form="' + (reschedule ? "reschedule" : "schedule") + '" data-id="' + esc(requestId) + '">' +
      field(reschedule ? "New date" : "Schedule date", "scheduledDate", "date", reschedule && request ? request.scheduledDate : "", true) +
      field(reschedule ? "New time" : "Schedule time", "scheduledTime", "time", reschedule && request ? String(request.scheduledTime).slice(0, 5) : "", true) +
      '<label class="data-field">Assigned laboratory technician<select name="technicianId" required><option value="">Choose a technician</option>' + technicianOptions + '</select></label><div class="data-form-actions"><button class="button button-primary button-small" type="submit">' + (reschedule ? "Save new schedule" : "Schedule request") + '</button></div></form>';
    openModal(reschedule ? "Reschedule test request" : "Schedule test request", "Scheduling and technician assignment are recorded together.", form);
  }

  document.addEventListener("submit", async function (event) {
    const form = event.target.closest("form[data-form]");
    if (!form) return;
    event.preventDefault();
    if (previewSession && form.dataset.form !== "request-filter") { ui.toast("Demo preview is read-only. Changes are not saved."); return; }
    if (!form.reportValidity()) return;
    const data = new FormData(form);
    const values = Object.fromEntries(data.entries());
    const int = function (value) { return value ? Number(value) : null; };
    const id = form.dataset.id;
    const submit = form.querySelector('button[type="submit"]');
    const submitLabel = submit ? submit.textContent : "";
    if (submit) { submit.disabled = true; submit.textContent = "Saving…"; }
    try {
      switch (form.dataset.form) {
        case "profile": await api.patch("/patients/me", { name: values.name, age: Number(values.age), gender: values.gender || null, phone: values.phone || null, address: values.address || null }); break;
        case "doctor-profile": await api.patch("/doctors/me", { name: values.name, specialization: values.specialization }); break;
        case "doctor-request": await api.post("/test-requests", { patientId: Number(values.patientId), testId: Number(values.testId) }); break;
        case "initial-result": await api.post("/test-requests/" + id + "/results", { resultValue: values.resultValue, remarks: values.remarks || null, resultDate: values.resultDate }); closeModal(); break;
        case "apply-correction": await api.post("/result-correction-requests/" + id + "/apply", { resultValue: values.resultValue, remarks: values.remarks || null, resultDate: values.resultDate }); closeModal(); break;
        case "correction-request": await api.post("/test-requests/" + id + "/correction-requests", { targetResultId: Number(values.targetResultId), targetReportId: int(values.targetReportId), reason: values.reason }); closeModal(); break;
        case "schedule": await api.post("/admin/test-requests/" + id + "/schedule", { scheduledDate: values.scheduledDate, scheduledTime: values.scheduledTime, technicianId: Number(values.technicianId) }); closeModal(); break;
        case "reschedule": await api.post("/admin/test-requests/" + id + "/reschedule", { scheduledDate: values.scheduledDate, scheduledTime: values.scheduledTime, technicianId: Number(values.technicianId) }); closeModal(); break;
        case "create-user": await api.post("/admin/users", { name: values.name, email: values.email, password: values.password, role: values.role, specialization: values.specialization || null, age: int(values.age), phone: values.phone || null, address: values.address || null }); form.reset(); break;
        case "assignment": await api.post("/admin/doctor-patient-assignments", { doctorId: Number(values.doctorId), patientId: Number(values.patientId) }); form.reset(); break;
        case "create-test": await api.post("/medical-tests", { name: values.name, description: values.description || null, price: Number(values.price), referenceRange: values.referenceRange || null, active: true }); form.reset(); break;
        case "request-filter": await showRequestFilter(form); return;
        default: return;
      }
      await loadData(); render(); ui.toast("Changes saved.");
    } catch (error) { ui.toast(error.message || "The request could not be completed."); }
    finally { if (submit) { submit.disabled = false; submit.textContent = submitLabel; } }
  });

  document.addEventListener("change", function (event) {
    if (!event.target.matches('select[name="role"]') || !event.target.form || event.target.form.dataset.form !== "create-user") return;
    const specialization = event.target.form.querySelector('[name="specialization"]');
    if (specialization) specialization.required = event.target.value === "DOCTOR";
  });

  document.addEventListener("reset", function (event) {
    if (event.target.matches('form[data-form="request-filter"]')) {
      state.requestFilter = {};
      window.setTimeout(function () { loadData().then(render); }, 0);
    }
  });

  window.MediCoreAppLogout = async function () {
    if (previewSession) {
      sessionStorage.removeItem(previewSessionKey);
      window.location.assign("index.html");
      return;
    }
    try { await api.post("/auth/logout", {}); } catch (error) { /* Clearing the browser session still leads to sign-in. */ }
    window.location.assign("index.html");
  };

  async function init() {
    if (!expectedRole) { window.location.assign("index.html"); return; }
    root.innerHTML = '<section class="card api-loading">Loading your authorized MediCore workspace…</section>';
    if (previewSession) {
      if (previewSession.role !== expectedRole) { window.location.assign(roleRoute[previewSession.role] || "index.html"); return; }
      await loadData();
      render();
      return;
    }
    try {
      state.user = await api.get("/auth/me");
      if (state.user.role !== expectedRole) { window.location.assign(roleRoute[state.user.role] || "index.html"); return; }
      await loadData();
      render();
    } catch (error) {
      root.innerHTML = '<section class="card api-error"><strong>Could not load your workspace</strong><p>' + esc(error.message || "Check that the local backend and MySQL are running.") + '</p><a class="button button-primary button-small" href="index.html">Return to sign in</a></section>';
      if (error.status === 401) window.setTimeout(function () { window.location.assign("index.html"); }, 1200);
    }
  }

  init();
})();
