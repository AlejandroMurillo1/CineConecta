/*
 * Comportamiento común (módulo shared).
 * - Envía el token CSRF en cada petición de htmx (Spring Security lo exige con cookies).
 * - Muestra un aviso cuando una petición de htmx falla, en lugar de no hacer nada.
 */
(function () {
    "use strict";

    function meta(name) {
        var el = document.querySelector('meta[name="' + name + '"]');
        return el ? el.getAttribute("content") : null;
    }

    document.addEventListener("htmx:configRequest", function (event) {
        var token = meta("_csrf");
        var header = meta("_csrf_header");
        if (token && header) {
            event.detail.headers[header] = token;
        }
    });

    function showError(message) {
        var region = document.getElementById("cc-flash");
        if (!region) {
            return;
        }
        var alert = document.createElement("div");
        alert.className = "cc-alert cc-alert-danger cc-fade-in";
        alert.setAttribute("role", "alert");
        alert.textContent = message;
        region.replaceChildren(alert);
    }

    document.addEventListener("htmx:responseError", function (event) {
        var status = event.detail.xhr ? event.detail.xhr.status : 0;
        if (status === 401) {
            window.location.href = "/login";
            return;
        }
        if (status === 503) {
            showError("El servicio no está disponible en este momento. Intenta de nuevo en unos minutos.");
            return;
        }
        showError(status === 403
            ? "No tienes permiso para realizar esta acción."
            : "No se pudo completar la operación. Intenta de nuevo.");
    });

    document.addEventListener("htmx:sendError", function () {
        showError("No hay conexión con el servidor. Revisa tu red e intenta de nuevo.");
    });
})();
