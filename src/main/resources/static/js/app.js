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
        showError(status === 403
            ? "No tienes permiso para realizar esta acción."
            : "No se pudo completar la operación. Intenta de nuevo.");
    });

    document.addEventListener("htmx:sendError", function () {
        showError("No hay conexión con el servidor. Revisa tu red e intenta de nuevo.");
    });
})();

/*
 * Afiches: si la imagen de un contenido no carga (URL rota o sin conexión), se reemplaza por un
 * afiche genérico en lugar de dejar el recuadro vacío.
 */
(function () {
    "use strict";

    var FALLBACK = "/images/posters/placeholder.svg";

    function isPoster(img) {
        return img.closest(".cat-poster-frame") || img.classList.contains("cat-detail-poster");
    }

    function useFallback(img) {
        if (img.dataset.posterFallback) {
            return;
        }
        img.dataset.posterFallback = "1";
        img.src = FALLBACK;
    }

    // Fase de captura: los errores de carga de <img> no burbujean.
    document.addEventListener("error", function (event) {
        var img = event.target;
        if (img instanceof HTMLImageElement && isPoster(img)) {
            useFallback(img);
        }
    }, true);

    // Imágenes que fallaron antes de que este script cargara (o que llegan con htmx).
    function checkLoaded(root) {
        root.querySelectorAll(".cat-poster-frame img, img.cat-detail-poster").forEach(function (img) {
            if (img.complete && img.naturalWidth === 0) {
                useFallback(img);
            }
        });
    }

    document.addEventListener("DOMContentLoaded", function () { checkLoaded(document); });
    document.addEventListener("htmx:afterSwap", function (event) { checkLoaded(event.target); });
})();
