const progress = document.querySelector("#playback-progress");
const position = document.querySelector("#playback-position");
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
}
