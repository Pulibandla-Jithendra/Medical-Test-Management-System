(function () {
  "use strict";
  const configuredBase = window.MEDICORE_CONFIG && window.MEDICORE_CONFIG.apiBaseUrl
    ? window.MEDICORE_CONFIG.apiBaseUrl
    : window.MEDICORE_API_BASE || "";
  const apiBase = String(configuredBase).trim().replace(/\/$/, "");
  const configurationErrorMessage = "The MediCore API URL is not configured. Set apiBaseUrl in frontend/config.js.";
  const connectionErrorMessage = "Cannot reach the configured MediCore API. Confirm the HTTPS backend URL and CORS settings.";
  let csrfToken = null;
  let refreshInFlight = null;

  async function fetchApi(url, options) {
    try {
      return await fetch(url, options);
    } catch (cause) {
      if (cause instanceof TypeError) {
        const error = new Error(connectionErrorMessage);
        error.code = "API_UNREACHABLE";
        throw error;
      }
      throw cause;
    }
  }

  async function readBody(response) {
    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) return response.json();
    if (response.status === 204) return null;
    return response.text();
  }

  async function getCsrfToken() {
    if (!apiBase) throw new Error(configurationErrorMessage);
    if (csrfToken) return csrfToken;
    const response = await fetchApi(apiBase + "/auth/csrf", { credentials: "include", headers: { Accept: "application/json" } });
    if (!response.ok) throw new Error("Could not establish a secure API session. Check that the backend is running.");
    const data = await response.json();
    csrfToken = data.token;
    return csrfToken;
  }

  async function refresh() {
    if (!refreshInFlight) {
      refreshInFlight = (async function () {
        const token = await getCsrfToken();
        const response = await fetchApi(apiBase + "/auth/refresh", {
          method: "POST", credentials: "include", headers: { Accept: "application/json", "X-CSRF-TOKEN": token }
        });
        if (!response.ok) throw new Error("Your session has expired. Please sign in again.");
        return response.json();
      })().finally(function () { refreshInFlight = null; });
    }
    return refreshInFlight;
  }

  async function request(path, options, retried) {
    if (!apiBase) throw new Error(configurationErrorMessage);
    options = options || {};
    const method = (options && options.method) || "GET";
    const headers = Object.assign({ Accept: "application/json" }, (options && options.headers) || {});
    if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
      headers["X-CSRF-TOKEN"] = await getCsrfToken();
      if (options.body && typeof options.body !== "string" && !(options.body instanceof FormData)) {
        headers["Content-Type"] = "application/json";
        options = Object.assign({}, options, { body: JSON.stringify(options.body) });
      }
    }
    const response = await fetchApi(apiBase + path, Object.assign({}, options, { method: method, headers: headers, credentials: "include" }));
    const publicAuthOperation = ["/auth/csrf", "/auth/register", "/auth/login", "/auth/refresh"].includes(path);
    if (response.status === 401 && !retried && !publicAuthOperation) {
      await refresh();
      return request(path, options, true);
    }
    const body = await readBody(response);
    if (!response.ok) {
      const error = new Error((body && body.message) || "The request could not be completed.");
      error.status = response.status;
      error.code = body && body.code;
      error.correlationId = body && body.correlationId;
      throw error;
    }
    return body;
  }

  async function download(path, retried) {
    if (!apiBase) throw new Error(configurationErrorMessage);
    const response = await fetchApi(apiBase + path, { credentials: "include", headers: { Accept: "application/pdf" } });
    if (response.status === 401 && !retried) {
      await refresh();
      return download(path, true);
    }
    if (!response.ok) {
      let message = "The report could not be downloaded.";
      try { message = (await response.json()).message || message; } catch (ignore) { /* Keep safe fallback. */ }
      throw new Error(message);
    }
    return response.blob();
  }

  window.MediCoreApi = {
    get: function (path) { return request(path); },
    post: function (path, body) { return request(path, { method: "POST", body: body || {} }); },
    patch: function (path, body) { return request(path, { method: "PATCH", body: body || {} }); },
    download: download,
    clearCsrfCache: function () { csrfToken = null; }
  };
})();
