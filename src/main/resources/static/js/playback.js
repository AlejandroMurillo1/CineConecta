const progress = document.querySelector("#playback-progress");
const position = document.querySelector("#playback-position");
const checkpoint = document.querySelector("#playback-checkpoint");
const pauseButton = document.querySelector("#playback-pause");

if (progress?.dataset.playing === "true" && pauseButton) {
    const startPosition = progress.value;
    const loadedAt = performance.now();

    function updatePosition() {
        const seconds = Math.floor((performance.now() - loadedAt) / 1000);
        progress.value = Math.min(progress.max, startPosition + seconds);
        position.textContent = progress.value;
        if (progress.value >= progress.max) {
            clearInterval(timer);
            pauseButton.form.requestSubmit(pauseButton);
        }
    }

    const timer = setInterval(updatePosition, 1000);
    updatePosition();

    if (checkpoint) {
        setInterval(async () => {
            if (document.hidden || progress.value >= progress.max) {
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
