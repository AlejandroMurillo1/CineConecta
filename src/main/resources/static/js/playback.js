// Reproductor simulado: no hay archivo de video. La pantalla queda en negro y el avance se calcula
// en el navegador a partir de la posición que entregó el servidor. Cada control (pausar, reanudar,
// ir a un punto y, al llegar al final, finalizar) envía el formulario y el servidor lo guarda en playback_sessions.
const video = document.querySelector("#playback-video");
const controls = document.querySelector("#playback-controls");
const positionLabel = document.querySelector("#playback-position");
const bar = document.querySelector("#playback-bar");
const track = document.querySelector("#playback-track");
const checkpoint = document.querySelector("#playback-checkpoint");
const pauseButton = document.querySelector("#playback-pause");
const resumeButton = document.querySelector("#playback-resume");
const seekButton = document.querySelector("#playback-seek");
const seekPosition = document.querySelector("#playback-seek-position");
const finishButton = document.querySelector("#playback-finish");

/** 3725 → "01:02:05" */
function formatTime(totalSeconds) {
    const seconds = Math.max(0, Math.floor(totalSeconds));
    const parts = [Math.floor(seconds / 3600), Math.floor((seconds % 3600) / 60), seconds % 60];
    return parts.map(part => String(part).padStart(2, "0")).join(":");
}

if (video && controls) {
    const startPosition = Number(video.dataset.position);
    const duration = Number(video.dataset.duration);
    const playing = video.dataset.playing === "true";
    let current = startPosition;

    function render() {
        positionLabel.textContent = formatTime(current);
        if (bar && duration > 0) {
            bar.style.width = `${Math.min(100, (current * 100) / duration)}%`;
        }
        track?.setAttribute("aria-valuenow", String(current));
    }

    /** Pausa o reanuda, según el estado actual. */
    function toggle() {
        const button = playing ? pauseButton : resumeButton;
        if (button) {
            controls.requestSubmit(button);
        }
    }

    /** Ir a una posición (en segundos). */
    function seekTo(seconds) {
        seekPosition.value = String(Math.max(0, Math.min(duration, Math.round(seconds))));
        controls.requestSubmit(seekButton);
    }

    video.addEventListener("click", toggle);

    document.addEventListener("keydown", event => {
        const typing = event.target.closest?.("input, textarea, select, button");
        if (event.code === "Space" && !typing) {
            event.preventDefault();
            toggle();
        }
    });

    if (track && duration > 0) {
        const ratioAt = event => {
            const box = track.getBoundingClientRect();
            return Math.min(1, Math.max(0, (event.clientX - box.left) / box.width));
        };
        track.addEventListener("click", event => seekTo(ratioAt(event) * duration));
        // Al pasar el mouse se ve a qué punto llevaría el clic.
        track.addEventListener("mousemove", event => {
            track.title = `Ir a ${formatTime(ratioAt(event) * duration)}`;
        });
        // Con el teclado: flechas para retroceder o avanzar 10 segundos.
        track.addEventListener("keydown", event => {
            if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
                event.preventDefault();
                seekTo(current + (event.key === "ArrowRight" ? 10 : -10));
            }
        });
    }

    if (playing && pauseButton) {
        const loadedAt = performance.now();

        function updatePosition() {
            const elapsed = Math.floor((performance.now() - loadedAt) / 1000);
            current = Math.min(duration, startPosition + elapsed);
            render();
            if (current >= duration) {
                clearInterval(timer);
                // Al llegar al final se finaliza: el servidor la marca como vista y lleva al detalle.
                controls.requestSubmit(finishButton ?? pauseButton);
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
}
