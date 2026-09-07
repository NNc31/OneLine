(() => {
    const STORAGE_KEY = 'oneline.muted';
    const SOUND_URL = '/sfx/snap.mp3';
    let muted = localStorage.getItem(STORAGE_KEY) === '1';

    try {
        if (navigator.audioSession) {
            navigator.audioSession.type = 'ambient';
        }
    } catch {

    }

    const AudioContextCtor = globalThis.AudioContext || globalThis.webkitAudioContext;
    let ctx = null;
    let buffer = null;
    let encoded = null;

    const decodeIfPossible = () => {
        if (!ctx || buffer || !encoded) {
            return;
        }
        ctx.decodeAudioData(encoded.slice(0))
            .then(decoded => {
                buffer = decoded;
            })
            .catch(() => {

            });
    };

    const ensureContext = () => {
        if (!ctx && AudioContextCtor) {
            try {
                ctx = new AudioContextCtor({ latencyHint: 'interactive' });
            } catch {
                return null;
            }
            decodeIfPossible();
        }
        return ctx;
    };

    fetch(SOUND_URL)
        .then(r => r.arrayBuffer())
        .then(data => {
            encoded = data;
            decodeIfPossible();
        })
        .catch(() => {

        });

    const unlock = () => {
        const context = ensureContext();
        if (!context) {
            return;
        }
        if (context.state === 'suspended') {
            context.resume().catch(() => {});
        }
        document.removeEventListener('pointerdown', unlock);
        document.removeEventListener('keydown', unlock);
    };
    document.addEventListener('pointerdown', unlock);
    document.addEventListener('keydown', unlock);

    const play = () => {
        if (muted) {
            return;
        }
        const context = ensureContext();
        if (!context || !buffer) {
            return;
        }
        if (context.state === 'suspended') {
            context.resume().catch(() => {});
        }
        try {
            const source = context.createBufferSource();
            source.buffer = buffer;
            source.connect(context.destination);
            source.start();
        } catch {

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

    globalThis.OneLineSound = {
        play: () => play(),
    };
})();
