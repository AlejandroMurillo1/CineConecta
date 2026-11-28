// Reproductor simulado: no hay archivo de video. La pantalla queda en negro y el avance se
// calcula en el navegador a partir de la posición que entregó el servidor.
const video = document.querySelector("#playback-video");
const positionLabel = document.querySelector("#playback-position");
const bar = document.querySelector("#playback-bar");
const checkpoint = document.querySelector("#playback-checkpoint");
const pauseButton = document.querySelector("#playback-pause");

/** 3725 → "01:02:05" */
function formatTime(totalSeconds) {
    const seconds = Math.max(0, Math.floor(totalSeconds));
    const parts = [Math.floor(seconds / 3600), Math.floor((seconds % 3600) / 60), seconds % 60];
    return parts.map(part => String(part).padStart(2, "0")).join(":");
}

if (video?.dataset.playing === "true" && pauseButton) {
    const startPosition = Number(video.dataset.position);
    const duration = Number(video.dataset.duration);
    const loadedAt = performance.now();
    let current = startPosition;

    function render() {
        positionLabel.textContent = formatTime(current);
        if (bar && duration > 0) {
            bar.style.width = `${Math.min(100, (current * 100) / duration)}%`;
        }
    }

    function updatePosition() {
        const elapsed = Math.floor((performance.now() - loadedAt) / 1000);
        current = Math.min(duration, startPosition + elapsed);
        render();
        if (current >= duration) {
            clearInterval(timer);
            // Al llegar al final se pausa en el servidor, que guarda la posición.
            pauseButton.form.requestSubmit(pauseButton);
        }
    }

    const timer = setInterval(updatePosition, 1000);
    updatePosition();

    if (checkpoint) {
        setInterval(async () => {
            if (document.hidden || current >= duration) {
                return;
            }
            try {
                const response = await fetch(checkpoint.action, {
                    method: "POST",
                    body: new URLSearchParams(new FormData(checkpoint))
                });
                if (response.status === 410) {
                    window.location.reload();
                }
            } catch {
                // El siguiente aviso puede recuperar la conexión.
            }
        }, 10000);
    }
}
