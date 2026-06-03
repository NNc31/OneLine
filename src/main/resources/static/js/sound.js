(() => {
    const STORAGE_KEY = 'oneline.muted';
    let muted = localStorage.getItem(STORAGE_KEY) === '1';

    const audio = new Audio('/sfx/snap.mp3');
    audio.preload = 'auto';
    let unlocked = false;

    const unlock = () => {
        if (unlocked) {
            return;
        }
        const volume = audio.volume;
        audio.volume = 0;
        audio.play().then(() => {
            audio.pause();
            audio.currentTime = 0;
            audio.volume = volume;
            unlocked = true;
            document.removeEventListener('pointerdown', unlock);
            document.removeEventListener('keydown', unlock);
        }).catch(() => {
            audio.volume = volume;
        });
    };
    document.addEventListener('pointerdown', unlock);
    document.addEventListener('keydown', unlock);

    const play = () => {
        if (!muted) {
            audio.currentTime = 0;
            audio.play().catch(() => {});
        }
    };

    const render = (btn) => {
        btn.dataset.muted = muted ? 'true' : 'false';
        btn.setAttribute('aria-label', muted ? 'Sound off' : 'Sound on');
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
    };
})();
