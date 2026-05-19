(() => {
    const STORAGE_KEY = 'oneline.muted';
    let muted = localStorage.getItem(STORAGE_KEY) === '1';

    const play = () => {
        if (!muted) {
            new Audio('/sfx/snap.mp3').play().catch(() => {});
        }
    };

    const render = (btn) => {
        btn.textContent = muted ? 'Muted' : 'Sound';
        btn.dataset.muted = muted ? 'true' : 'false';
    };

    document.addEventListener('DOMContentLoaded', () => {
        const toggles = document.querySelectorAll('[data-sound-toggle]');
        toggles.forEach(btn => {
            render(btn);
            btn.addEventListener('click', () => {
                muted = !muted;
                localStorage.setItem(STORAGE_KEY, muted ? '1' : '0');
                toggles.forEach(render);
                if (!muted) {
                    play();
                }
            });
        });
    });

    window.OneLineSound = {
        play: () => play(),
        isMuted: () => muted,
    };
})();
