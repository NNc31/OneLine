const initChat = async (root) => {
    const publicId = root.dataset.publicId;
    const secret = (globalThis.location.hash || '').replace(/^#/, '');
    let authToken = null;

    const missingSecretEl = document.getElementById('missing-secret');
    const joinCardEl = document.getElementById('join-card');
    const joinFormEl = document.getElementById('join-form');
    const joinNameEl = document.getElementById('join-display-name');
    const joinErrorEl = document.getElementById('join-error');
    const chatRoomEl = document.getElementById('chat-room');
    const messagesEl = document.getElementById('messages');
    const sendFormEl = document.getElementById('send-form');
    const sendInputEl = document.getElementById('send-input');
    const statusEl = document.getElementById('conn-status');
    const meBadgeEl = document.getElementById('me-badge');
    const onlineCountEl = document.getElementById('online-count');
    const typingEl = document.getElementById('typing-indicator');
    const ttlNoteEl = document.getElementById('ttl-note');
    const attachBtnEl = document.getElementById('attach-btn');
    const attachInputEl = document.getElementById('attach-input');
    const uploadProgressEl = document.getElementById('upload-progress');
    const uploadProgressLabelEl = document.getElementById('upload-progress-label');
    const uploadProgressFillEl = document.getElementById('upload-progress-fill');
    const uploadCancelEl = document.getElementById('upload-cancel');
    const lightboxEl = document.getElementById('lightbox');
    const lightboxImgEl = lightboxEl ? lightboxEl.querySelector('img') : null;

    const TIME_FORMATTER = new Intl.DateTimeFormat(undefined, {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    });

    if (!secret) {
        missingSecretEl.hidden = false;
        return;
    }

    try {
        authToken = await OneLineCrypto.deriveAuthToken(secret);
    } catch (e) {
        console.error('Auth token derivation failed', e);
        missingSecretEl.hidden = false;
        return;
    }

    const readMeta = (name) => document.querySelector(`meta[name="${name}"]`)?.content || '';
    const csrfHeader = readMeta('csrf-header') || 'X-XSRF-TOKEN';
    const csrfToken = readMeta('csrf-token');

    const apiHeaders = (extra) => ({
        'X-Chat-Token': authToken,
        'Accept': 'application/json',
        [csrfHeader]: csrfToken,
        ...(sessionToken ? { 'X-Session-Token': sessionToken } : {}),
        ...extra,
    });

    const showJoinCard = () => {
        joinCardEl.hidden = false;
        chatRoomEl.hidden = true;
        sendFormEl.hidden = true;
        statusEl.hidden = true;
    };

    const showChatRoom = (me) => {
        joinCardEl.hidden = true;
        chatRoomEl.hidden = false;
        sendFormEl.hidden = false;
        statusEl.hidden = false;
        meBadgeEl.textContent = me.displayName;
        meBadgeEl.hidden = false;
        sendInputEl.focus();
    };

    const setStatus = (state, text) => {
        statusEl.dataset.state = state;
        statusEl.textContent = text;
    };

    const DAY_MS = 24 * 60 * 60 * 1000;

    const formatDate = (d) => {
        const pad = (n) => String(n).padStart(2, '0');
        return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${pad(d.getFullYear() % 100)}`;
    };

    const formatTime = (iso) => {
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) {
            return '--:--';
        }
        const time = TIME_FORMATTER.format(d);
        return Date.now() - d.getTime() >= DAY_MS ? `${formatDate(d)} ${time}` : time;
    };

    const isEditing = () => {
        const el = document.activeElement;
        return !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable);
    };

    document.addEventListener('keydown', (e) => {
        if (e.ctrlKey && e.key === 'Backspace' && !isEditing()) {
            e.preventDefault();
            globalThis.location.assign('/me');
            return;
        }
        if (chatRoomEl.hidden || lightboxEl?.open) {
            return;
        }
        if (!isEditing() && !e.ctrlKey && !e.metaKey && !e.altKey && e.key.length === 1) {
            sendInputEl.focus();
        }
    });

    const seenMessageIds = new Set();
    let meId = null;
    let chatId = null;
    let cryptoKey = null;
    let oldestMessageId = null;
    let loadingOlder = false;
    let noMoreHistory = false;
    let messageTtlMs = null;
    let attachmentsEnabled = true;
    let sessionToken = null;
    let signPriv = null;
    let signPub = null;
    try {
        const stored = JSON.parse(localStorage.getItem('oneline.sessionChats') || '[]').find(c => c.publicId === publicId);
        sessionToken = stored?.sessionToken || null;
        signPriv = stored?.signPriv || null;
        signPub = stored?.signPub || null;
    } catch {
    }

    const PINS_KEY = `oneline.identity.${publicId}`;
    const identityPins = (() => {
        try {
            return JSON.parse(localStorage.getItem(PINS_KEY) || '{}');
        } catch {
            return {};
        }
    })();
    const persistPins = () => {
        try {
            localStorage.setItem(PINS_KEY, JSON.stringify(identityPins));
        } catch {

        }
    };
    const SIGN_PREFIX = 'oneline.msg.v1';
    const signingInput = (cmid, body) => `${SIGN_PREFIX}:${cmid}:${body}`;

    const ensureSigningKeys = async () => {
        if (signPriv && signPub) {
            return;
        }
        try {
            const keys = await OneLineCrypto.generateSigningKeys();
            signPriv = keys.privateKeyB64;
            signPub = keys.publicKeyB64;
        } catch (e) {
            console.warn('Ed25519 unavailable. Messages will be sent unsigned', e);
        }
    };

    const buildSignedEnvelope = async (cmid, body) => {
        const sig = await OneLineCrypto.sign(signPriv, signingInput(cmid, body));
        return JSON.stringify({ v: 1, body, cmid, pk: signPub, sig });
    };

    const resolveMessage = async (m, plaintext) => {
        let env = null;
        try {
            const obj = JSON.parse(plaintext);
            if (obj?.v === 1 && typeof obj.body === 'string' && typeof obj.pk === 'string' && typeof obj.sig === 'string') {
                env = obj;
            }
        } catch {
        }
        if (!env) {
            return { body: plaintext, status: 'unsigned' };
        }
        const ok = await OneLineCrypto.verify(env.pk, env.sig, signingInput(env.cmid, env.body));
        if (!ok) {
            return { body: env.body, status: 'unverified' };
        }
        const pid = String(m.participantId);
        const pinned = identityPins[pid];
        if (!pinned) {
            identityPins[pid] = env.pk;
            persistPins();
            return { body: env.body, status: 'verified' };
        }
        return { body: env.body, status: pinned === env.pk ? 'verified' : 'mismatch' };
    };

    const HISTORY_PAGE = 50;
    const MAX_TIMEOUT_MS = 2147483647;
    const FILE_MARKER_V1 = 'file/v1';
    const FILE_MARKER_V2 = 'file/v2';
    const MAX_FILE_BYTES = 1024 * 1024 * 1024;
    const CHUNK_PLAIN_BYTES = 4 * 1024 * 1024;
    const CHUNK_OVERHEAD_BYTES = 1 + 12 + 16;
    const UPLOAD_CONCURRENCY = 4;

    const humanizeTtl = (sec) => {
        if (sec % 86400 === 0) {
            return (sec / 86400) + 'd';
        }
        if (sec % 3600 === 0) {
            return (sec / 3600) + 'h';
        }
        return Math.round(sec / 60) + 'm';
    };

    const applyTtl = (seconds) => {
        messageTtlMs = seconds ? seconds * 1000 : null;
        if (ttlNoteEl) {
            ttlNoteEl.hidden = !seconds;
            if (seconds) {
                ttlNoteEl.textContent = 'self-destruct: ' + humanizeTtl(seconds);
            }
        }
    };

    const applyAttachmentsAvailability = () => {
        if (attachBtnEl) {
            attachBtnEl.hidden = !attachmentsEnabled;
        }
    };

    const parseFilePayload = (text) => {
        try {
            const obj = JSON.parse(text);
            if (obj?.k === FILE_MARKER_V1 || obj?.k === FILE_MARKER_V2) {
                return obj;
            }
            return null;
        } catch {
            return null;
        }
    };

    const humanizeSize = (bytes) => {
        if (bytes < 1024) {
            return bytes + ' B';
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024).toFixed(1) + ' KB';
        }
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    };

    const trackUrl = (li, url) => {
        (li._objectUrls || (li._objectUrls = [])).push(url);
    };
    const revokeUrls = (li) => {
        (li._objectUrls || []).forEach(URL.revokeObjectURL);
        li._objectUrls = [];
    };

    const openLightbox = (src, alt) => {
        if (!lightboxEl || !lightboxImgEl) {
            return;
        }
        lightboxImgEl.src = src;
        lightboxImgEl.alt = alt || '';
        lightboxEl.showModal();
    };
    const closeLightbox = () => {
        if (!lightboxEl || !lightboxImgEl) {
            return;
        }
        lightboxEl.close();
        lightboxImgEl.removeAttribute('src');
    };
    if (lightboxEl) {
        lightboxEl.addEventListener('click', closeLightbox);
        lightboxEl.addEventListener('close', () => lightboxImgEl?.removeAttribute('src'));
    }

    const xhrPut = (url, body, onProgress) => {
        const xhr = new XMLHttpRequest();
        const promise = new Promise((resolve, reject) => {
            xhr.open('PUT', url);
            xhr.upload.onprogress = (e) => {
                if (e.lengthComputable && onProgress) {
                    onProgress(e.loaded / e.total);
                }
            };
            xhr.onload = () => {
                if (xhr.status >= 200 && xhr.status < 300) {
                    resolve();
                } else {
                    reject(new Error('upload ' + xhr.status));
                }
            };
            xhr.onerror = () => reject(new Error('upload network error'));
            xhr.onabort = () => reject(Object.assign(new Error('upload aborted'), { aborted: true }));
            xhr.send(body);
        });
        return { xhr, promise };
    };

    const fetchChunk = async (url) => {
        const resp = await fetch(url);
        if (!resp.ok) {
            throw new Error(`attachment fetch ${resp.status}`);
        }
        return new Uint8Array(await resp.arrayBuffer());
    };

    const downloadAndDecrypt = async (payload) => {
        const resp = await fetch(`/api/chats/${publicId}/attachments/${payload.id}`, {
            method: 'GET',
            headers: apiHeaders(),
            credentials: 'same-origin',
        });
        if (!resp.ok) {
            throw new Error(`attachment url ${resp.status}`);
        }
        const { chunks } = await resp.json();
        if (!Array.isArray(chunks) || chunks.length === 0) {
            throw new Error('attachment chunks missing');
        }
        const ordered = chunks.slice().sort((a, b) => a.index - b.index);
        const fileKey = await OneLineCrypto.importRawKey(OneLineCrypto.base64Decode(payload.key));

        if (payload.k === FILE_MARKER_V1) {
            const cipher = await fetchChunk(ordered[0].downloadUrl);
            return OneLineCrypto.decryptBytes(fileKey, cipher);
        }

        const parts = new Array(ordered.length);
        let total = 0;
        for (let i = 0; i < ordered.length; i++) {
            const cipher = await fetchChunk(ordered[i].downloadUrl);
            const plain = await OneLineCrypto.decryptBytes(fileKey, cipher);
            parts[i] = plain;
            total += plain.byteLength;
        }
        const assembled = new Uint8Array(total);
        let offset = 0;
        for (const p of parts) {
            assembled.set(p, offset);
            offset += p.byteLength;
        }
        return assembled;
    };

    const renderFileBody = (li, body, payload) => {
        body.classList.add('attachment');
        const isImage = typeof payload.mime === 'string' && payload.mime.startsWith('image/');
        if (isImage) {
            const img = document.createElement('img');
            img.className = 'attachment-image';
            img.alt = payload.name || 'image';
            body.appendChild(img);
            downloadAndDecrypt(payload).then((plain) => {
                const url = URL.createObjectURL(new Blob([plain], { type: payload.mime }));
                trackUrl(li, url);
                img.src = url;
                img.addEventListener('click', () => openLightbox(url, payload.name));
            }).catch((e) => {
                console.error('Attachment load failed', e);
                body.textContent = '⚠ attachment unavailable';
            });
            return;
        }
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'attachment-file';
        btn.textContent = `⬇ ${payload.name} (${humanizeSize(payload.size)})`;
        btn.addEventListener('click', async () => {
            btn.disabled = true;
            try {
                const plain = await downloadAndDecrypt(payload);
                const url = URL.createObjectURL(new Blob([plain], { type: payload.mime || 'application/octet-stream' }));
                const a = document.createElement('a');
                a.href = url;
                a.download = payload.name || 'download';
                document.body.appendChild(a);
                a.click();
                a.remove();
                setTimeout(() => URL.revokeObjectURL(url), 10000);
            } catch (e) {
                console.error('Attachment download failed', e);
                btn.textContent = 'attachment unavailable';
            } finally {
                btn.disabled = false;
            }
        });
        body.appendChild(btn);
    };

    const buildAuthorshipBadge = (status) => {
        if (status === 'unsigned') {
            return null;
        }
        const badge = document.createElement('span');
        badge.className = 'authorship ' + (status === 'verified' ? 'verified' : 'warn');
        if (status === 'verified') {
            badge.textContent = '+';
            badge.title = 'Author verified';
        } else if (status === 'mismatch') {
            badge.textContent = '!';
            badge.title = 'Signing key changed';
        } else {
            badge.textContent = '!';
            badge.title = 'Author not verified';
        }
        return badge;
    };

    const createMessageEl = (m, body, status) => {
        if (seenMessageIds.has(m.id)) {
            return null;
        }
        seenMessageIds.add(m.id);
        let remainingMs = null;
        if (messageTtlMs != null) {
            remainingMs = new Date(m.createdAt).getTime() + messageTtlMs - Date.now();
            if (remainingMs <= 0) {
                return null;
            }
        }
        const li = document.createElement('li');
        li.className = 'message' + (m.participantId === meId ? ' mine' : '');
        li.dataset.messageId = String(m.id);

        const author = document.createElement('span');
        author.className = 'author';
        author.textContent = m.displayName;
        const badge = buildAuthorshipBadge(status);
        if (badge) {
            author.appendChild(badge);
        }

        const time = document.createElement('time');
        time.dateTime = m.createdAt;
        time.textContent = formatTime(m.createdAt);

        const bodyEl = document.createElement('span');
        bodyEl.className = 'body';
        const filePayload = parseFilePayload(body);
        if (filePayload) {
            renderFileBody(li, bodyEl, filePayload);
        } else {
            bodyEl.textContent = body;
        }

        li.append(author, time, bodyEl);
        if (remainingMs != null && remainingMs <= MAX_TIMEOUT_MS) {
            setTimeout(() => {
                revokeUrls(li);
                li.remove();
            }, remainingMs);
        }
        return li;
    };

    const renderMessage = (m, body, status) => {
        const li = createMessageEl(m, body, status);
        if (li) {
            messagesEl.appendChild(li);
        }
    };

    const loadOlder = async () => {
        if (loadingOlder || noMoreHistory || oldestMessageId == null || !cryptoKey) {
            return;
        }
        loadingOlder = true;
        try {
            const resp = await fetch(`/api/chats/${publicId}/messages?before=${oldestMessageId}&limit=${HISTORY_PAGE}`, {
                method: 'GET',
                headers: apiHeaders(),
                credentials: 'same-origin',
            });
            if (!resp.ok) {
                throw new Error(`history ${resp.status}`);
            }
            const batch = await resp.json();
            if (batch.length === 0) {
                noMoreHistory = true;
                return;
            }
            const prevHeight = messagesEl.scrollHeight;
            const prevTop = messagesEl.scrollTop;
            const fragment = document.createDocumentFragment();
            for (const m of batch.slice().reverse()) {
                try {
                    const plaintext = await OneLineCrypto.decrypt(cryptoKey, m.content);
                    const resolved = await resolveMessage(m, plaintext);
                    const li = createMessageEl(m, resolved.body, resolved.status);
                    if (li) {
                        fragment.appendChild(li);
                    }
                } catch (e) {
                    console.error('Skipping undecryptable message', m.id, e);
                }
            }
            messagesEl.insertBefore(fragment, messagesEl.firstChild);
            messagesEl.scrollTop = prevTop + (messagesEl.scrollHeight - prevHeight);
            oldestMessageId = Math.min(oldestMessageId, ...batch.map(m => m.id));
            if (batch.length < HISTORY_PAGE) {
                noMoreHistory = true;
            }
        } catch (e) {
            console.error(e);
        } finally {
            loadingOlder = false;
        }
    };

    messagesEl.addEventListener('scroll', () => {
        if (messagesEl.scrollTop <= 40) {
            loadOlder();
        }
    });

    const decryptAndRender = async (m) => {
        try {
            const plaintext = await OneLineCrypto.decrypt(cryptoKey, m.content);
            const resolved = await resolveMessage(m, plaintext);
            const isNew = !seenMessageIds.has(m.id);
            renderMessage(m, resolved.body, resolved.status);
            messagesEl.scrollTop = messagesEl.scrollHeight;
            if (isNew && m.participantId !== meId && globalThis.OneLineSound) {
                globalThis.OneLineSound.play();
            }
        } catch (e) {
            console.error('Decrypt failed for message', m.id, e);
        }
    };

    const rememberChat = (displayName) => {
        try {
            const KEY = 'oneline.sessionChats';
            const raw = localStorage.getItem(KEY);
            const list = raw ? JSON.parse(raw) : [];
            const filtered = list.filter(c => c.publicId !== publicId);
            filtered.unshift({
                publicId,
                secret: secret,
                displayName: displayName || null,
                sessionToken: sessionToken || null,
                signPriv: signPriv || null,
                signPub: signPub || null,
                lastUsed: new Date().toISOString(),
            });
            localStorage.setItem(KEY, JSON.stringify(filtered.slice(0, 50)));
        } catch {

        }
    };

    const loadHistoryAndConnect = async (me) => {
        meId = me.id;
        showChatRoom(me);
        await ensureSigningKeys();
        rememberChat(me.displayName);

        try {
            cryptoKey = await OneLineCrypto.deriveKey(secret, chatId);
        } catch (e) {
            console.error('Key derivation failed', e);
            setStatus('error', 'Cannot derive encryption key');
            return;
        }

        try {
            const resp = await fetch(`/api/chats/${publicId}/messages`, {
                method: 'GET',
                headers: apiHeaders(),
                credentials: 'same-origin',
            });
            if (!resp.ok) {
                throw new Error(`history ${resp.status}`);
            }
            const history = await resp.json();
            for (const m of history.slice().reverse()) {
                try {
                    const plaintext = await OneLineCrypto.decrypt(cryptoKey, m.content);
                    const resolved = await resolveMessage(m, plaintext);
                    renderMessage(m, resolved.body, resolved.status);
                } catch (e) {
                    console.error('Skipping undecryptable message', m.id, e);
                }
            }
            if (history.length > 0) {
                oldestMessageId = Math.min(...history.map(m => m.id));
            }
            noMoreHistory = history.length < HISTORY_PAGE;
            messagesEl.scrollTop = messagesEl.scrollHeight;
        } catch (err) {
            console.error(err);
            setStatus('error', 'Failed to load history');
            return;
        }

        connectWebSocket();
    };

    const updatePresence = (online) => {
        if (!onlineCountEl) {
            return;
        }
        const count = Array.isArray(online) ? online.length : 0;
        onlineCountEl.textContent = count + ' online';
        onlineCountEl.hidden = count === 0;
    };

    const typingNames = new Map();
    const typingTimers = new Map();

    const renderTyping = () => {
        if (!typingEl) {
            return;
        }
        const names = [...typingNames.values()];
        if (names.length === 0) {
            typingEl.hidden = true;
            typingEl.textContent = '';
            return;
        }
        if (names.length === 1) {
            typingEl.textContent = `${names[0]} is typing...`;
        } else if (names.length === 2) {
            typingEl.textContent = `${names[0]} and ${names[1]} are typing...`;
        } else {
            typingEl.textContent = 'Several people are typing...';
        }
        typingEl.hidden = false;
    };

    const handleTyping = (participant, typing) => {
        if (!participant || participant.id === meId) {
            return;
        }
        const id = participant.id;
        if (typingTimers.has(id)) {
            clearTimeout(typingTimers.get(id));
            typingTimers.delete(id);
        }
        if (typing) {
            typingNames.set(id, participant.displayName);
            typingTimers.set(id, setTimeout(() => {
                typingNames.delete(id);
                typingTimers.delete(id);
                renderTyping();
            }, 6000));
        } else {
            typingNames.delete(id);
        }
        renderTyping();
    };

    const handleEvent = (ev) => {
        if (!ev?.type) {
            return;
        }
        if (ev.type === 'presence') {
            updatePresence(ev.online || []);
        } else if (ev.type === 'typing') {
            handleTyping(ev.participant, ev.typing);
        }
    };

    const fetchPresence = async () => {
        try {
            const resp = await fetch(`/api/chats/${publicId}/presence`, {
                method: 'GET',
                headers: apiHeaders(),
                credentials: 'same-origin',
            });
            if (resp.ok) {
                updatePresence(await resp.json());
            }
        } catch (e) {
            console.error('Presence fetch failed', e);
        }
    };

    const connectWebSocket = () => {
        const wsUrl = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws';
        const client = new StompJs.Client({
            brokerURL: wsUrl,
            connectHeaders: { 'X-Chat-Token': authToken, 'X-Session-Token': sessionToken },
            reconnectDelay: 2000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
        });

        let serverShutdownAnnounced = false;

        client.onConnect = () => {
            serverShutdownAnnounced = false;
            setStatus('online', 'Online');
            client.subscribe(`/topic/chat.${chatId}`, (frame) => {
                try {
                    const m = JSON.parse(frame.body);
                    decryptAndRender(m);
                } catch (e) {
                    console.error('Bad message frame', e);
                }
            });
            client.subscribe(`/topic/chat.${chatId}.events`, (frame) => {
                try {
                    handleEvent(JSON.parse(frame.body));
                } catch (e) {
                    console.error('Bad event frame', e);
                }
            });
            client.subscribe('/topic/system.events', (frame) => {
                try {
                    const ev = JSON.parse(frame.body);
                    if (ev?.type === 'shutdown') {
                        serverShutdownAnnounced = true;
                        setStatus('offline', 'Server is restarting...');
                    }
                } catch (e) {
                    console.error('System event', e);
                }
            });
            fetchPresence();
        };
        client.onWebSocketClose = () => {
            if (!serverShutdownAnnounced) {
                setStatus('offline', 'Offline. Reconnecting...');
            }
        };
        client.onStompError = (frame) => {
            let reason = frame.headers.message || 'Disconnected';
            try {
                const parsed = JSON.parse(frame.body);
                if (parsed?.error) {
                    reason = parsed.error;
                }
            } catch {

            }
            setStatus('error', reason);
        };

        client.activate();

        setInterval(() => {
            if (client.connected) {
                client.publish({ destination: `/app/chat.${chatId}.heartbeat`, body: '' });
            }
        }, 15000);

        const TYPING_RESEND_MS = 3000;
        const TYPING_IDLE_MS = 5000;
        let amTyping = false;
        let typingIdleTimer = null;
        let lastTypingSentAt = 0;
        const sendTyping = (state) => {
            if (client.connected) {
                client.publish({
                    destination: `/app/chat.${chatId}.typing`,
                    body: JSON.stringify({ typing: state }),
                });
            }
        };
        const stopTyping = () => {
            if (typingIdleTimer) {
                clearTimeout(typingIdleTimer);
                typingIdleTimer = null;
            }
            if (amTyping) {
                amTyping = false;
                sendTyping(false);
            }
        };

        sendInputEl.addEventListener('input', () => {
            if (!client.connected) {
                return;
            }
            const now = Date.now();
            if (!amTyping || now - lastTypingSentAt >= TYPING_RESEND_MS) {
                amTyping = true;
                lastTypingSentAt = now;
                sendTyping(true);
            }
            if (typingIdleTimer) {
                clearTimeout(typingIdleTimer);
            }
            typingIdleTimer = setTimeout(stopTyping, TYPING_IDLE_MS);
        });

        sendFormEl.addEventListener('submit', async (e) => {
            e.preventDefault();
            const plaintext = sendInputEl.value.trim();
            if (!plaintext || !client.connected || !cryptoKey) {
                return;
            }
            const id = crypto.randomUUID();
            let ciphertextBase64;
            try {
                const envelope = signPriv ? await buildSignedEnvelope(id, plaintext) : plaintext;
                ciphertextBase64 = await OneLineCrypto.encrypt(cryptoKey, envelope);
            } catch (err) {
                console.error('Encrypt failed', err);
                return;
            }
            client.publish({
                destination: `/app/chat.${chatId}.send`,
                body: JSON.stringify({ clientMessageId: id, content: ciphertextBase64 }),
            });
            stopTyping();
            sendInputEl.value = '';
            sendInputEl.focus();
        });

        let activeUpload = null;
        const showProgress = (label) => {
            if (!uploadProgressEl) {
                return;
            }
            uploadProgressLabelEl.textContent = label;
            uploadProgressFillEl.style.width = '0%';
            uploadProgressEl.hidden = false;
        };
        const setProgress = (ratio, name) => {
            if (!uploadProgressEl) {
                return;
            }
            const pct = Math.round(ratio * 100);
            uploadProgressFillEl.style.width = pct + '%';
            uploadProgressLabelEl.textContent = `Uploading ${name} — ${pct}%`;
        };
        const hideProgress = () => {
            if (uploadProgressEl) {
                uploadProgressEl.hidden = true;
            }
        };

        if (uploadCancelEl) {
            uploadCancelEl.addEventListener('click', () => {
                if (activeUpload) {
                    activeUpload.abort();
                }
            });
        }

        const computeCipherSizes = (file, chunkCount) => {
            const sizes = [];
            for (let i = 0; i < chunkCount; i++) {
                const plainSize = Math.min(CHUNK_PLAIN_BYTES, file.size - i * CHUNK_PLAIN_BYTES);
                sizes.push(plainSize + CHUNK_OVERHEAD_BYTES);
            }
            return sizes;
        };

        const requestUploadSlots = async (cipherSizes) => {
            const resp = await fetch(`/api/chats/${publicId}/attachments`, {
                method: 'POST',
                headers: apiHeaders({ 'Content-Type': 'application/json' }),
                credentials: 'same-origin',
                body: JSON.stringify({ chunks: cipherSizes }),
            });
            if (resp.ok) {
                return resp.json();
            }
            let reason = `prepare ${resp.status}`;
            try {
                const parsed = await resp.json();
                if (parsed?.error) {
                    reason = parsed.error;
                }
            } catch {
                // body wasn't JSON
            }
            throw new Error(reason);
        };

        const runUploadPool = async (chunkCount, uploadOne) => {
            const queue = [];
            for (let i = 0; i < chunkCount; i++) {
                queue.push(i);
            }
            const worker = async () => {
                while (queue.length > 0) {
                    const i = queue.shift();
                    await uploadOne(i);
                }
            };
            const workers = [];
            for (let i = 0; i < Math.min(UPLOAD_CONCURRENCY, chunkCount); i++) {
                workers.push(worker());
            }
            await Promise.all(workers);
        };

        const publishAttachmentMessage = async (attachmentId, file, fileKeyRaw, chunkCount) => {
            const payload = JSON.stringify({
                k: FILE_MARKER_V2,
                id: attachmentId,
                name: file.name,
                mime: file.type || 'application/octet-stream',
                size: file.size,
                key: OneLineCrypto.base64Encode(fileKeyRaw),
                chunkCount,
            });
            const messageId = crypto.randomUUID();
            const envelope = signPriv ? await buildSignedEnvelope(messageId, payload) : payload;
            const content = await OneLineCrypto.encrypt(cryptoKey, envelope);
            client.publish({
                destination: `/app/chat.${chatId}.send`,
                body: JSON.stringify({ clientMessageId: messageId, content }),
            });
        };

        const uploadFile = async (file) => {
            if (!client.connected || !cryptoKey) {
                return;
            }
            if (!attachmentsEnabled) {
                setStatus('error', 'Attachments are temporarily disabled');
                return;
            }
            if (file.size > MAX_FILE_BYTES) {
                setStatus('error', `File too large (max ${humanizeSize(MAX_FILE_BYTES)})`);
                return;
            }

            const chunkCount = Math.max(1, Math.ceil(file.size / CHUNK_PLAIN_BYTES));
            const cipherSizes = computeCipherSizes(file, chunkCount);

            attachBtnEl.disabled = true;
            showProgress(`Preparing ${file.name}...`);
            const activeXhrs = new Set();
            const cancellation = { cancelled: false };
            const cancelAll = () => {
                cancellation.cancelled = true;
                for (const xhr of activeXhrs) {
                    xhr.abort();
                }
            };
            activeUpload = { abort: cancelAll };

            try {
                const fileKeyRaw = OneLineCrypto.randomRawKey();
                const fileKey = await OneLineCrypto.importRawKey(fileKeyRaw);
                const { attachmentId, chunks: chunkUploads } = await requestUploadSlots(cipherSizes);
                const uploadsByIndex = new Map(chunkUploads.map(c => [c.index, c.uploadUrl]));

                let uploadedBytes = 0;
                const totalPlainBytes = file.size || 1;
                const uploadOne = async (i) => {
                    if (cancellation.cancelled) {
                        return;
                    }
                    const start = i * CHUNK_PLAIN_BYTES;
                    const end = Math.min(file.size, start + CHUNK_PLAIN_BYTES);
                    const slice = await file.slice(start, end).arrayBuffer();
                    const cipher = await OneLineCrypto.encryptBytes(fileKey, new Uint8Array(slice));
                    if (cancellation.cancelled) {
                        return;
                    }
                    const uploadUrl = uploadsByIndex.get(i);
                    if (!uploadUrl) {
                        throw new Error(`missing upload url for chunk ${i}`);
                    }
                    const putHandle = xhrPut(uploadUrl, cipher, null);
                    activeXhrs.add(putHandle.xhr);
                    try {
                        await putHandle.promise;
                    } finally {
                        activeXhrs.delete(putHandle.xhr);
                    }
                    uploadedBytes += (end - start);
                    setProgress(uploadedBytes / totalPlainBytes, file.name);
                };

                await runUploadPool(chunkCount, uploadOne);
                if (cancellation.cancelled) {
                    const err = new Error('upload cancelled');
                    err.aborted = true;
                    throw err;
                }

                uploadProgressLabelEl.textContent = `Finalizing ${file.name}...`;
                const confirmResp = await fetch(`/api/chats/${publicId}/attachments/${attachmentId}/confirm`, {
                    method: 'POST',
                    headers: apiHeaders(),
                    credentials: 'same-origin',
                });
                if (!confirmResp.ok) {
                    throw new Error('confirm ' + confirmResp.status);
                }

                await publishAttachmentMessage(attachmentId, file, fileKeyRaw, chunkCount);
            } catch (e) {
                if (e?.aborted) {
                    setStatus(statusEl.dataset.state || 'online', 'Upload cancelled');
                } else {
                    console.error('Attachment upload failed', e);
                    setStatus('error', `Upload failed: ${e.message || 'unknown error'}`);
                }
            } finally {
                activeUpload = null;
                hideProgress();
                attachBtnEl.disabled = false;
            }
        };

        attachBtnEl.addEventListener('click', () => attachInputEl.click());
        attachInputEl.addEventListener('change', async () => {
            const file = attachInputEl.files?.[0];
            attachInputEl.value = '';
            if (file) {
                await uploadFile(file);
            }
        });

        const hasFiles = (dt) => Array.from(dt?.types || []).includes('Files');
        let dragDepth = 0;
        const clearDragOver = () => {
            dragDepth = 0;
            chatRoomEl.classList.remove('drag-over');
        };
        document.body.addEventListener('dragenter', (e) => {
            if (chatRoomEl.hidden || !hasFiles(e.dataTransfer)) {
                return;
            }
            dragDepth++;
            chatRoomEl.classList.add('drag-over');
        });
        document.body.addEventListener('dragover', (e) => {
            if (!chatRoomEl.hidden && hasFiles(e.dataTransfer)) {
                e.preventDefault();
            }
        });
        document.body.addEventListener('dragleave', () => {
            if (dragDepth > 0) {
                dragDepth--;
                if (dragDepth === 0) {
                    chatRoomEl.classList.remove('drag-over');
                }
            }
        });
        document.body.addEventListener('drop', async (e) => {
            if (chatRoomEl.hidden || !hasFiles(e.dataTransfer)) {
                clearDragOver();
                return;
            }
            e.preventDefault();
            clearDragOver();
            const file = e.dataTransfer.files?.[0];
            if (file) {
                await uploadFile(file);
            }
        });

        document.addEventListener('paste', async (e) => {
            if (chatRoomEl.hidden) {
                return;
            }
            const items = e.clipboardData?.items || [];
            for (const item of items) {
                if (item.kind === 'file') {
                    const file = item.getAsFile();
                    if (file) {
                        e.preventDefault();
                        await uploadFile(file);
                        return;
                    }
                }
            }
        });
    };

    fetch(`/api/chats/${publicId}`, {
        method: 'GET',
        headers: apiHeaders(),
        credentials: 'same-origin',
    })
        .then(r => r.ok ? r.json() : Promise.reject(new Error(`meta ${r.status}`)))
        .then(meta => {
            chatId = meta.chatId;
            applyTtl(meta.messageTtlSeconds);
            attachmentsEnabled = meta.attachmentsEnabled !== false;
            applyAttachmentsAvailability();
            if (meta.me) {
                return loadHistoryAndConnect(meta.me);
            }
            showJoinCard();
            return null;
        })
        .catch(err => {
            console.error(err);
            missingSecretEl.hidden = false;
        });

    joinFormEl.addEventListener('submit', (e) => {
        e.preventDefault();
        joinErrorEl.hidden = true;
        const displayName = joinNameEl.value.trim();
        if (!displayName) {
            return;
        }
        fetch(`/api/chats/${publicId}/join`, {
            method: 'POST',
            headers: apiHeaders({ 'Content-Type': 'application/json' }),
            credentials: 'same-origin',
            body: JSON.stringify({ displayName }),
        })
            .then(async r => {
                if (r.ok) {
                    return r.json();
                }
                const body = await r.text();
                let message = `Join failed (${r.status})`;
                try {
                    const parsed = JSON.parse(body);
                    if (parsed?.error) {
                        message = parsed.error;
                    }
                } catch {

                }
                throw new Error(message);
            })
            .then(joined => {
                chatId = joined.chatId;
                if (joined.sessionToken) {
                    sessionToken = joined.sessionToken;
                }
                return loadHistoryAndConnect(joined.me);
            })
            .catch(err => {
                joinErrorEl.textContent = err.message;
                joinErrorEl.hidden = false;
            });
    });
};

const chatRoot = document.querySelector('.chat-main .inner[data-public-id]');
if (chatRoot) {
    await initChat(chatRoot);
}
