globalThis.OneLineVoice = (() => {
    const MIME_CANDIDATES = [
        'audio/webm;codecs=opus',
        'audio/ogg;codecs=opus',
        'audio/mp4',
        'audio/webm',
    ];
    const bitrateFor = (mimeType) => (mimeType?.includes('opus') ? 32000 : 64000);
    const BARS = 44;
    const MIN_BAR_HEIGHT = 0.06;
    const TICK_MS = 100;

    let sharedContext = null;
    let activePlayer = null;

    const audioContext = () => {
        if (!sharedContext) {
            const Ctor = globalThis.AudioContext ?? globalThis.webkitAudioContext;
            sharedContext = Ctor ? new Ctor() : null;
        }
        return sharedContext;
    };

    const pickMimeType = () => {
        if (typeof MediaRecorder === 'undefined' || !MediaRecorder.isTypeSupported) {
            return null;
        }
        return MIME_CANDIDATES.find((type) => MediaRecorder.isTypeSupported(type)) ?? null;
    };

    const isSupported = () => Boolean(navigator.mediaDevices?.getUserMedia) && pickMimeType() !== null;

    const formatTime = (ms) => {
        const total = Math.max(0, Math.round(ms / 1000));
        return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
    };

    const failure = (stage, cause) =>
        Object.assign(new Error(stage), { stage, reason: cause?.name || 'unknown', cause });

    const readAudioSession = () => {
        try {
            return navigator.audioSession?.type ?? null;
        } catch {
            return null;
        }
    };

    const writeAudioSession = (type) => {
        try {
            if (navigator.audioSession && type) {
                navigator.audioSession.type = type;
            }
        } catch {
        }
    };

    const newRecorder = (stream, mimeType) => {
        const attempts = [{ mimeType, audioBitsPerSecond: bitrateFor(mimeType) }, { mimeType }, undefined];
        let lastError = null;
        for (const options of attempts) {
            try {
                return new MediaRecorder(stream, options);
            } catch (e) {
                lastError = e;
            }
        }
        throw failure('recorder', lastError);
    };

    const MIC_CONSTRAINTS = {
        audio: {
            channelCount: { ideal: 1 },
            sampleRate: { ideal: 48000 },
            echoCancellation: false,
            noiseSuppression: false,
            autoGainControl: false,
        },
    };

    const openMicrophone = async () => {
        const previous = readAudioSession();
        if (previous === null) {
            return { stream: await navigator.mediaDevices.getUserMedia(MIC_CONSTRAINTS), previous };
        }
        let lastError = null;
        for (const session of ['auto', 'play-and-record']) {
            writeAudioSession(session);
            try {
                return { stream: await navigator.mediaDevices.getUserMedia(MIC_CONSTRAINTS), previous };
            } catch (e) {
                lastError = e;
            }
        }
        writeAudioSession(previous);
        throw lastError;
    };

    const record = async ({ maxMs, onTick, onLimit }) => {
        let stream;
        let previousSession = null;
        try {
            ({ stream, previous: previousSession } = await openMicrophone());
        } catch (e) {
            throw failure('microphone', e);
        }

        const requested = pickMimeType();
        let recorder;
        try {
            recorder = newRecorder(stream, requested);
        } catch (e) {
            stream.getTracks().forEach((track) => track.stop());
            writeAudioSession(previousSession);
            throw e.stage ? e : failure('recorder', e);
        }

        const mimeType = recorder.mimeType || requested || 'audio/webm';
        const track = stream.getAudioTracks()[0];
        const parts = [];
        const startedAt = performance.now();
        let stoppedAt = null;
        let ticker = null;
        let limit = null;

        const clearTimers = () => {
            clearInterval(ticker);
            clearTimeout(limit);
        };

        recorder.addEventListener('dataavailable', (e) => {
            if (e.data && e.data.size > 0) {
                parts.push(e.data);
            }
        });

        const finished = new Promise((resolve) => {
            recorder.addEventListener('stop', () => {
                clearTimers();
                stream.getTracks().forEach((track) => track.stop());
                writeAudioSession(previousSession);
                resolve({
                    blob: new Blob(parts, { type: mimeType }),
                    durationMs: Math.round((stoppedAt ?? performance.now()) - startedAt),
                    mimeType,
                    // What the microphone actually gave us, as opposed to what was asked for.
                    settings: track?.getSettings?.() ?? {},
                });
            });
        });

        const halt = () => {
            if (recorder.state !== 'inactive') {
                stoppedAt = performance.now();
                recorder.stop();
            }
        };

        ticker = setInterval(() => onTick?.(performance.now() - startedAt), TICK_MS);
        limit = setTimeout(() => {
            halt();
            onLimit?.();
        }, maxMs);
        recorder.start();

        return {
            stop: () => {
                halt();
                return finished;
            },
            cancel: () => {
                halt();
                return finished.then(() => null);
            },
        };
    };

    const peaksOf = (buffer) => {
        const samples = buffer.getChannelData(0);
        const perBar = Math.max(1, Math.floor(samples.length / BARS));
        const peaks = new Float32Array(BARS);
        let loudest = 0;
        for (let bar = 0; bar < BARS; bar++) {
            const start = bar * perBar;
            const end = Math.min(samples.length, start + perBar);
            let peak = 0;
            for (let i = start; i < end; i++) {
                const value = Math.abs(samples[i]);
                if (value > peak) {
                    peak = value;
                }
            }
            peaks[bar] = peak;
            if (peak > loudest) {
                loudest = peak;
            }
        }
        if (loudest > 0) {
            for (let bar = 0; bar < BARS; bar++) {
                peaks[bar] /= loudest;
            }
        }
        return peaks;
    };

    const encodePeaks = (peaks) => {
        let binary = '';
        for (const peak of peaks) {
            binary += String.fromCodePoint(Math.round(Math.min(1, Math.max(0, peak)) * 255));
        }
        return btoa(binary);
    };

    const decodePeaks = (encoded) => {
        try {
            const binary = atob(encoded);
            const peaks = new Float32Array(binary.length);
            for (let i = 0; i < binary.length; i++) {
                peaks[i] = binary.codePointAt(i) / 255;
            }
            return peaks.length > 0 ? peaks : null;
        } catch {
            return null;
        }
    };

    const analyze = async (blob) => {
        const ctx = audioContext();
        if (!ctx) {
            return null;
        }
        const bytes = new Uint8Array(await blob.arrayBuffer());
        const buffer = await ctx.decodeAudioData(bytes.buffer);
        return {
            peaks: encodePeaks(peaksOf(buffer)),
            durationMs: Math.round(buffer.duration * 1000),
        };
    };

    const readCssColor = (el, name, fallback) =>
        getComputedStyle(el).getPropertyValue(name).trim() || fallback;

    const createPlayer = ({ durationMs, peaks: encodedPeaks, load }) => {
        const root = document.createElement('div');
        root.className = 'voice';

        const playBtn = document.createElement('button');
        playBtn.type = 'button';
        playBtn.className = 'voice-play';
        playBtn.setAttribute('aria-label', 'Play voice message');

        const canvas = document.createElement('canvas');
        canvas.className = 'voice-wave';

        const timeEl = document.createElement('span');
        timeEl.className = 'voice-time';
        timeEl.textContent = formatTime(durationMs);

        root.append(playBtn, canvas, timeEl);

        let buffer = null;
        let peaks = encodedPeaks ? decodePeaks(encodedPeaks) : null;
        let source = null;
        let offsetSeconds = 0;
        let startedAtContextTime = 0;
        let playing = false;
        let loading = false;
        let frame = null;

        const totalSeconds = () => buffer?.duration ?? Math.max(0.1, durationMs / 1000);

        const positionSeconds = () => {
            if (!playing) {
                return offsetSeconds;
            }
            const ctx = audioContext();
            return Math.min(totalSeconds(), offsetSeconds + (ctx.currentTime - startedAtContextTime));
        };

        const draw = () => {
            const width = canvas.clientWidth;
            const height = canvas.clientHeight;
            if (width === 0 || height === 0) {
                return;
            }
            const ratio = globalThis.devicePixelRatio || 1;
            if (canvas.width !== Math.round(width * ratio) || canvas.height !== Math.round(height * ratio)) {
                canvas.width = Math.round(width * ratio);
                canvas.height = Math.round(height * ratio);
            }
            const ctx2d = canvas.getContext('2d');
            ctx2d.setTransform(ratio, 0, 0, ratio, 0, 0);
            ctx2d.clearRect(0, 0, width, height);

            const played = readCssColor(root, '--accent', '#3b6fd4');
            const idle = readCssColor(root, '--border', '#9aa3b2');
            const progress = positionSeconds() / totalSeconds();
            const slot = width / BARS;
            const barWidth = Math.max(1.5, slot * 0.55);

            for (let bar = 0; bar < BARS; bar++) {
                const sampled = peaks ? peaks[Math.floor(bar * peaks.length / BARS)] : null;
                const level = sampled === null ? 0.35 : Math.max(MIN_BAR_HEIGHT, sampled);
                const barHeight = Math.max(2, level * height);
                const x = bar * slot + (slot - barWidth) / 2;
                const y = (height - barHeight) / 2;
                ctx2d.fillStyle = (bar + 0.5) / BARS <= progress ? played : idle;
                ctx2d.beginPath();
                ctx2d.roundRect(x, y, barWidth, barHeight, barWidth / 2);
                ctx2d.fill();
            }
        };

        const renderTime = () => {
            const seconds = playing || offsetSeconds > 0 ? positionSeconds() : totalSeconds();
            timeEl.textContent = formatTime(seconds * 1000);
        };

        const tick = () => {
            draw();
            renderTime();
            if (playing) {
                frame = requestAnimationFrame(tick);
            }
        };

        const stopSource = () => {
            if (source) {
                source.onended = null;
                try {
                    source.stop();
                } catch {

                }
                source = null;
            }
        };

        const pause = () => {
            if (!playing) {
                return;
            }
            offsetSeconds = positionSeconds();
            playing = false;
            stopSource();
            cancelAnimationFrame(frame);
            playBtn.classList.remove('playing');
            playBtn.setAttribute('aria-label', 'Play voice message');
            tick();
        };

        const reachedEnd = () => {
            playing = false;
            offsetSeconds = 0;
            source = null;
            cancelAnimationFrame(frame);
            playBtn.classList.remove('playing');
            playBtn.setAttribute('aria-label', 'Play voice message');
            tick();
        };

        const ensureBuffer = async () => {
            if (buffer || loading) {
                return buffer;
            }
            loading = true;
            root.classList.add('loading');
            try {
                const bytes = await load();
                const ctx = audioContext();
                if (!ctx) {
                    throw new Error('Web Audio unavailable');
                }
                buffer = await ctx.decodeAudioData(bytes.slice().buffer);
                peaks ??= peaksOf(buffer);
                return buffer;
            } finally {
                loading = false;
                root.classList.remove('loading');
            }
        };

        const play = async () => {
            const ctx = audioContext();
            if (ctx.state === 'suspended') {
                await ctx.resume();
            }
            if (!await ensureBuffer()) {
                return;
            }
            if (activePlayer && activePlayer !== api) {
                activePlayer.pause();
            }
            activePlayer = api;
            if (offsetSeconds >= buffer.duration - 0.05) {
                offsetSeconds = 0;
            }
            source = ctx.createBufferSource();
            source.buffer = buffer;
            source.connect(ctx.destination);
            source.onended = () => {
                if (playing) {
                    reachedEnd();
                }
            };
            startedAtContextTime = ctx.currentTime;
            source.start(0, offsetSeconds);
            playing = true;
            playBtn.classList.add('playing');
            playBtn.setAttribute('aria-label', 'Pause voice message');
            tick();
        };

        playBtn.addEventListener('click', async () => {
            if (playing) {
                pause();
                return;
            }
            try {
                await play();
            } catch (e) {
                console.error('Voice playback failed', e);
                root.classList.add('failed');
                timeEl.textContent = 'unavailable';
            }
        });

        canvas.addEventListener('click', async (e) => {
            const rect = canvas.getBoundingClientRect();
            const ratio = Math.min(1, Math.max(0, (e.clientX - rect.left) / rect.width));
            const wasPlaying = playing;
            if (wasPlaying) {
                pause();
            }
            try {
                await ensureBuffer();
            } catch (e2) {
                console.error('Voice seek failed', e2);
                return;
            }
            offsetSeconds = ratio * totalSeconds();
            if (wasPlaying) {
                await play();
            } else {
                tick();
            }
        });

        const api = { element: root, pause };
        if (globalThis.ResizeObserver) {
            new ResizeObserver(() => draw()).observe(canvas);
        }
        setTimeout(draw, 0);
        tick();
        return api;
    };

    return { isSupported, pickMimeType, record, analyze, createPlayer, formatTime };
})();
