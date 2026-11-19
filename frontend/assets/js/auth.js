(function () {
  "use strict";
  const ui = window.MediCoreUI;
  const api = window.MediCoreApi;
  const roleUrl = { PATIENT: "patient.html", DOCTOR: "doctor.html", LAB_TECHNICIAN: "lab-technician.html", ADMIN: "admin.html" };
  const previewSessionKey = "medicore.previewSession";
  const loginForm = document.getElementById("login-form");
  const registerForm = document.getElementById("register-form");

  function showMessage(element, message, isError) {
    if (!element) return;
    element.textContent = message;
    element.hidden = false;
    element.classList.toggle("inline-message-error", Boolean(isError));
  }
  function pending(button, value) {
    button.disabled = value;
    if (value) button.dataset.originalLabel = button.textContent.trim();
    button.textContent = value ? "Please wait…" : (button.dataset.originalLabel || button.textContent);
  }

  document.querySelectorAll("[data-demo-role]").forEach(function (button) {
    button.addEventListener("click", function () {
      const role = button.getAttribute("data-demo-role");
      if (!roleUrl[role]) return;
      sessionStorage.setItem(previewSessionKey, JSON.stringify({ role: role }));
      window.location.assign(roleUrl[role]);
    });
  });

  const previewRegistration = document.getElementById("preview-registration");
  if (previewRegistration && registerForm) {
    previewRegistration.addEventListener("click", function () {
      const message = document.getElementById("registration-message");
      const name = registerForm.elements.namedItem("name");
      const email = registerForm.elements.namedItem("email");
      const age = registerForm.elements.namedItem("age");
      if (!name.reportValidity() || !email.reportValidity() || !age.reportValidity()) return;
      const demoEmail = String(email.value || "").trim().toLowerCase();
      if (!demoEmail.endsWith("@example.test")) {
        showMessage(message, "For this local preview, use a fictional email ending in @example.test, such as you@example.test.", true);
        return;
      }
      sessionStorage.setItem(previewSessionKey, JSON.stringify({
        role: "PATIENT",
        profile: {
          name: String(name.value).trim(), email: demoEmail, age: Number(age.value),
          gender: registerForm.elements.namedItem("gender").value || null,
          phone: registerForm.elements.namedItem("phone").value || null,
          address: registerForm.elements.namedItem("address").value || null
        }
      }));
      window.location.assign(roleUrl.PATIENT);
    });
  }

  const passwordToggle = document.getElementById("toggle-password");
  const passwordInput = document.getElementById("password");
  if (passwordToggle && passwordInput) {
    passwordToggle.addEventListener("click", function () {
      const show = passwordInput.type === "password";
      passwordInput.type = show ? "text" : "password";
      passwordToggle.textContent = show ? "Hide" : "Show";
      passwordToggle.setAttribute("aria-label", show ? "Hide password" : "Show password");
    });
  }

  if (loginForm) {
    loginForm.addEventListener("submit", async function (event) {
      event.preventDefault();
      if (!loginForm.reportValidity()) return;
      const button = loginForm.querySelector('button[type="submit"]');
      const message = document.getElementById("login-message");
      if (message) message.hidden = true;
      pending(button, true);
      try {
        sessionStorage.removeItem(previewSessionKey);
        const form = new FormData(loginForm);
        const user = await api.post("/auth/login", { email: form.get("email"), password: form.get("password") });
        window.location.assign(roleUrl[user.role] || "index.html");
      } catch (error) {
        showMessage(message, error.message || "Sign in failed. Check your details and try again.", true);
      } finally {
        pending(button, false);
      }
    });
  }

  if (registerForm) {
    registerForm.addEventListener("submit", async function (event) {
      event.preventDefault();
      if (!registerForm.reportValidity()) return;
      const button = registerForm.querySelector('button[type="submit"]');
      const message = document.getElementById("registration-message");
      if (message) message.hidden = true;
      pending(button, true);
      try {
        sessionStorage.removeItem(previewSessionKey);
        const form = new FormData(registerForm);
        await api.post("/auth/register", {
          name: form.get("name"), email: form.get("email"), password: form.get("password"),
          age: Number(form.get("age")), gender: form.get("gender") || null,
          phone: form.get("phone") || null, address: form.get("address") || null
        });
        showMessage(message, "Your patient account was created. You can now sign in with the email and password you chose.", false);
        registerForm.reset();
        ui.toast("Patient registration completed.");
      } catch (error) {
        showMessage(message, error.message || "Registration failed. Please review the form and try again.", true);
      } finally {
        pending(button, false);
      }
    });
  }
})();
