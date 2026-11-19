(function () {
  "use strict";

  const iconPaths = {
    grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
    clipboard: '<rect x="5" y="5" width="14" height="16" rx="2"/><path d="M9 5.5h6M9 11h6M9 15h6"/><path d="M9 2.5h6v4H9z"/>',
    calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4M17 3v4M3 10h18M8 14h2M14 14h2M8 17h2"/>',
    file: '<path d="M6 2.8h8l4 4V21a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 4 21V4.3a1.5 1.5 0 0 1 1.5-1.5Z"/><path d="M14 3v5h5M8 13h8M8 17h8"/>',
    person: '<circle cx="12" cy="8" r="3.5"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/>',
    users: '<path d="M16 21v-1.5a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4V21"/><circle cx="9.5" cy="8" r="3.5"/><path d="M20 21v-1.5a4 4 0 0 0-3-3.87M16.5 4.65a3.5 3.5 0 0 1 0 6.7"/>',
    flask: '<path d="M9 3h6M10 3v6l-5.3 8.7A2.2 2.2 0 0 0 6.6 21h10.8a2.2 2.2 0 0 0 1.9-3.3L14 9V3"/><path d="M8 15h8"/>',
    activity: '<path d="M3 12h4l2.5-6 4.5 12 2.5-6H21"/>',
    check: '<path d="m5 12 4.2 4.2L19.5 6"/>',
    shield: '<path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11Z"/><path d="m9 12 2 2 4-4"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="m19.4 15 .1.1 1.3 1-.9 1.6-1.6-.5a7.7 7.7 0 0 1-1.7 1l-.3 1.7h-1.9l-.3-1.7a7.7 7.7 0 0 1-1.8-1l-1.5.5-.9-1.6 1.3-1a7.3 7.3 0 0 1 0-2l-1.3-1 .9-1.6 1.5.5a7.7 7.7 0 0 1 1.8-1l.3-1.7h1.9l.3 1.7a7.7 7.7 0 0 1 1.7 1l1.6-.5.9 1.6-1.3 1a7.3 7.3 0 0 1 0 2Z"/>',
    search: '<circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4.5 4.5"/>',
    bell: '<path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
    menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
    help: '<circle cx="12" cy="12" r="9"/><path d="M9.6 9a2.5 2.5 0 1 1 4.3 1.7c-1.2 1.2-1.9 1.5-1.9 3M12 17.5h.01"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>'
  };
  const navByRole = {
    patient: [
      ["Overview", "#overview", "grid"],
      ["My requests", "#requests", "clipboard"],
      ["Schedule", "#schedule", "calendar"],
      ["Reports", "#reports", "file"],
      ["Doctor access", "#assignments", "shield"],
      ["My profile", "#profile", "person"]
    ],
    doctor: [
      ["Overview", "#overview", "grid"],
      ["Authorized patients", "#patients", "users"],
      ["Test requests", "#requests", "clipboard"],
      ["Reports", "#reports", "file"],
      ["Test catalogue", "#catalogue", "flask"]
    ],
    "lab-technician": [
      ["Overview", "#overview", "grid"],
      ["Assigned work", "#work-queue", "clipboard", "5"],
      ["Results", "#results", "activity"],
      ["Reports", "#reports", "file"],
      ["Corrections", "#corrections", "activity"]
    ],
    admin: [
      ["Overview", "#overview", "grid"],
      ["User accounts", "#accounts", "users"],
      ["Doctor assignments", "#assignments", "users"],
      ["Test requests", "#requests", "clipboard"],
      ["Test catalogue", "#catalogue", "flask"],
      ["Result corrections", "#corrections", "activity"],
      ["Audit activity", "#audit-log", "shield"]
    ]
  };
  const roleLabels = { patient: "Patient", doctor: "Doctor", "lab-technician": "Lab Technician", admin: "Administrator" };
  const roleUrls = { patient: "patient.html", doctor: "doctor.html", "lab-technician": "lab-technician.html", admin: "admin.html" };

  function svg(name, className) {
    return '<svg class="' + (className || "") + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (iconPaths[name] || iconPaths.grid) + '</svg>';
  }
  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (char) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char];
    });
  }
  function toast(message) {
    const region = document.getElementById("toast-region");
    if (!region) return;
    const item = document.createElement("div");
    item.className = "toast-message";
    item.textContent = message;
    region.appendChild(item);
    window.setTimeout(function () {
      item.style.opacity = "0";
      item.style.transform = "translateY(6px)";
      window.setTimeout(function () { item.remove(); }, 220);
    }, 3100);
  }
  function badge(status) {
    const classes = {
      IN_PROGRESS: "status-in-progress",
      SCHEDULED: "status-scheduled",
      COMPLETED: "status-completed",
      REQUESTED: "status-requested",
      REPORT_GENERATED: "status-ready",
      NEEDS_REVIEW: "status-review"
    };
    const labels = {
      IN_PROGRESS: "In progress",
      SCHEDULED: "Scheduled",
      COMPLETED: "Completed",
      REQUESTED: "Requested",
      REPORT_GENERATED: "Report ready",
      NEEDS_REVIEW: "Needs review"
    };
    return '<span class="status-badge ' + (classes[status] || "status-neutral") + '">' + (labels[status] || escapeHtml(status)) + '</span>';
  }
  function metricCard(metric, index) {
    const iconClass = index % 4 === 1 ? "avatar-blue" : index % 4 === 2 ? "avatar-orange" : index % 4 === 3 ? "avatar-lilac" : "";
    return '<article class="metric-card"><div class="metric-top"><span class="metric-label">' + escapeHtml(metric.label) + '</span><span class="metric-icon ' + iconClass + '">' + svg(metric.icon) + '</span></div><div class="metric-value">' + escapeHtml(metric.value) + '</div><div class="metric-foot"><span class="metric-trend">' + escapeHtml(metric.trend || "") + '</span><span>' + escapeHtml(metric.foot) + '</span></div></article>';
  }
  function metricGrid(metrics) {
    return '<section class="metric-grid" aria-label="Summary metrics">' + metrics.map(metricCard).join("") + '</section>';
  }
  function cardHeader(title, subtitle, action, anchor) {
    return '<div class="card-header"><div><h2 class="card-title">' + escapeHtml(title) + '</h2>' + (subtitle ? '<p class="card-subtitle">' + escapeHtml(subtitle) + '</p>' : "") + '</div>' + (action ? '<a class="card-link" href="' + (anchor || "#overview") + '">' + escapeHtml(action) + ' <span aria-hidden="true">→</span></a>' : "") + '</div>';
  }
  function heading(role, title, description, actions) {
    return '<div class="page-heading" id="overview"><div class="page-heading-copy"><p class="breadcrumb"><span>MediCore</span><span class="breadcrumb-arrow">/</span><span>' + escapeHtml(roleLabels[role]) + ' workspace</span></p><h1 class="page-title">' + escapeHtml(title) + '</h1><p class="page-description">' + escapeHtml(description) + '</p></div><div class="heading-actions"><span class="updated-note">' + svg("clock") + ' Updated just now</span>' + (actions || "") + '</div></div>';
  }
  function actionButton(label, message, kind) {
    return '<button type="button" class="button ' + (kind || "button-secondary") + ' button-small" data-toast="' + escapeHtml(message) + '">' + escapeHtml(label) + '</button>';
  }
  function table(rows, headers, renderRow, extraClass) {
    return '<div class="table-wrap"><table class="data-table ' + (extraClass || "") + '"><thead><tr>' + headers.map(function (h) { return "<th>" + escapeHtml(h) + "</th>"; }).join("") + '</tr></thead><tbody>' + rows.map(renderRow).join("") + '</tbody></table></div>';
  }
  function navHtml(role) {
    return (navByRole[role] || []).map(function (item, index) {
      return '<a class="sidebar-link ' + (index === 0 ? "active" : "") + '" href="' + item[1] + '" data-nav-link>' + svg(item[2]) + '<span>' + item[0] + '</span>' + (item[3] ? '<span class="nav-count">' + item[3] + '</span>' : "") + '</a>';
    }).join("");
  }
  function headerHtml(role, user) {
    return '<div class="header-left"><button type="button" class="icon-button mobile-menu-button" id="mobile-menu-toggle" aria-label="Open navigation">' + svg("menu") + '</button><a class="brand header-brand" href="index.html" aria-label="MediCore home"><span class="brand-mark"><span></span><span></span></span><span class="brand-name">MediCore<span class="brand-dot">.</span></span></a><div class="header-context"><span>' + svg("grid") + '</span><span><span class="context-overline">SECURE WORKSPACE</span><span class="context-role">' + escapeHtml(roleLabels[role]) + '</span></span></div></div><div class="header-right"><div class="header-user"><span class="avatar ' + user.avatar + '">' + escapeHtml(user.initials) + '</span><span class="header-user-name"><strong>' + escapeHtml(user.name) + '</strong><span>' + escapeHtml(roleLabels[role]) + '</span></span></div><button type="button" class="button button-secondary button-small" id="logout-button">Sign out</button></div>';
  }
  function sidebarHtml(role, user) {
    return '<a class="brand sidebar-brand" href="index.html"><span class="brand-mark"><span></span><span></span></span><span class="brand-name">MediCore<span class="brand-dot">.</span></span></a><p class="sidebar-section-label">Workspace</p><nav class="sidebar-nav" aria-label="' + escapeHtml(roleLabels[role]) + ' navigation">' + navHtml(role) + '</nav><div class="sidebar-bottom"><div class="sidebar-help"><span class="sidebar-help-mark">?</span><span><strong>Need help?</strong><small>Use synthetic information in this academic prototype.</small></span></div><div class="sidebar-user"><span class="avatar avatar-small ' + user.avatar + '">' + escapeHtml(user.initials) + '</span><span class="sidebar-user-copy"><strong>' + escapeHtml(user.name) + '</strong><span>Account #' + escapeHtml(user.id) + '</span></span></div></div>';
  }
  function bindSharedEvents(role) {
    const logout = document.getElementById("logout-button");
    if (logout) logout.addEventListener("click", function () {
      if (window.MediCoreAppLogout) window.MediCoreAppLogout();
    });
    const button = document.getElementById("mobile-menu-toggle");
    const sidebar = document.getElementById("app-sidebar");
    const scrim = document.getElementById("sidebar-scrim");
    function closeMenu() {
      if (!sidebar || !scrim) return;
      sidebar.classList.remove("open");
      scrim.classList.remove("visible");
      scrim.setAttribute("aria-hidden", "true");
      if (button) button.setAttribute("aria-expanded", "false");
    }
    if (button && sidebar && scrim) {
      button.setAttribute("aria-expanded", "false");
      button.addEventListener("click", function () {
        const open = sidebar.classList.toggle("open");
        scrim.classList.toggle("visible", open);
        scrim.setAttribute("aria-hidden", String(!open));
        button.setAttribute("aria-expanded", String(open));
      });
      scrim.addEventListener("click", closeMenu);
      sidebar.querySelectorAll("[data-nav-link]").forEach(function (link) { link.addEventListener("click", closeMenu); });
    }
    document.querySelectorAll("[data-nav-link]").forEach(function (link) {
      link.addEventListener("click", function () {
        document.querySelectorAll("[data-nav-link]").forEach(function (item) { item.classList.remove("active"); });
        link.classList.add("active");
      });
    });
    window.addEventListener("hashchange", function () {
      const hash = window.location.hash || "#overview";
      document.querySelectorAll("[data-nav-link]").forEach(function (item) {
        item.classList.toggle("active", item.getAttribute("href") === hash);
      });
    });
    const current = window.location.hash || "#overview";
    const active = document.querySelector('.sidebar-link[href="' + current + '"]');
    if (active) {
      document.querySelectorAll("[data-nav-link]").forEach(function (item) { item.classList.remove("active"); });
      active.classList.add("active");
    }
    if (!navByRole[role]) return;
  }
  function mountDashboard(role, user) {
    const header = document.getElementById("app-header");
    const sidebar = document.getElementById("app-sidebar");
    if (!header || !sidebar) return;
    header.innerHTML = headerHtml(role, user);
    sidebar.innerHTML = sidebarHtml(role, user);
    bindSharedEvents(role);
  }

  document.addEventListener("click", function (event) {
    const target = event.target.closest("[data-toast]");
    if (target && !target.dataset.boundToast) {
      target.dataset.boundToast = "true";
      toast(target.getAttribute("data-toast") || "Preview only.");
    }
  });
  window.MediCoreUI = {
    svg: svg,
    escapeHtml: escapeHtml,
    toast: toast,
    badge: badge,
    metricCard: metricCard,
    metricGrid: metricGrid,
    cardHeader: cardHeader,
    heading: heading,
    actionButton: actionButton,
    table: table,
    mountDashboard: mountDashboard
  };
})();

