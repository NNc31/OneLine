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

    const readCookie = (name) => {
        const match = document.cookie.split('; ').find(r => r.startsWith(name + '='));
        return match ? decodeURIComponent(match.slice(name.length + 1)) : '';
    };

    const apiHeaders = (extra) => ({
        'X-Chat-Token': authToken,
        'Accept': 'application/json',
        'X-XSRF-TOKEN': readCookie('XSRF-TOKEN'),
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
    };

    const setStatus = (state, text) => {
        statusEl.dataset.state = state;
        statusEl.textContent = text;
    };

    const formatTime = (iso) => {
        const d = new Date(iso);
        return Number.isNaN(d.getTime()) ? '--:--' : TIME_FORMATTER.format(d);
    };

    const seenMessageIds = new Set();
    let meId = null;
    let chatId = null;
    let cryptoKey = null;
    let oldestMessageId = null;
    let loadingOlder = false;
    let noMoreHistory = false;
    let messageTtlMs = null;
    const HISTORY_PAGE = 50;
    const MAX_TIMEOUT_MS = 2147483647;
    const FILE_MARKER = 'file/v1';
    const MAX_FILE_BYTES = 100 * 1024 * 1024;

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

    const parseFilePayload = (text) => {
        try {
            const obj = JSON.parse(text);
            return obj?.k === FILE_MARKER ? obj : null;
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

    const downloadAndDecrypt = async (payload) => {
        const resp = await fetch(`/api/chats/${publicId}/attachments/${payload.id}`, {
            method: 'GET',
            headers: apiHeaders(),
            credentials: 'same-origin',
        });
        if (!resp.ok) {
            throw new Error(`attachment url ${resp.status}`);
        }
        const { downloadUrl } = await resp.json();
        const blobResp = await fetch(downloadUrl);
        if (!blobResp.ok) {
            throw new Error(`attachment fetch ${blobResp.status}`);
        }
        const ciphertext = new Uint8Array(await blobResp.arrayBuffer());
        const fileKey = await OneLineCrypto.importRawKey(OneLineCrypto.base64Decode(payload.key));
        return OneLineCrypto.decryptBytes(fileKey, ciphertext);
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

    const createMessageEl = (m, plaintext) => {
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

        const time = document.createElement('time');
        time.dateTime = m.createdAt;
        time.textContent = formatTime(m.createdAt);

        const body = document.createElement('span');
        body.className = 'body';
        const filePayload = parseFilePayload(plaintext);
        if (filePayload) {
            renderFileBody(li, body, filePayload);
        } else {
            body.textContent = plaintext;
        }

        li.append(author, time, body);
        if (remainingMs != null && remainingMs <= MAX_TIMEOUT_MS) {
            setTimeout(() => {
                revokeUrls(li);
                li.remove();
            }, remainingMs);
        }
        return li;
    };

    const renderMessage = (m, plaintext) => {
        const li = createMessageEl(m, plaintext);
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
                    const li = createMessageEl(m, plaintext);
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
            const isNew = !seenMessageIds.has(m.id);
            renderMessage(m, plaintext);
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
                lastUsed: new Date().toISOString(),
            });
            localStorage.setItem(KEY, JSON.stringify(filtered.slice(0, 50)));
        } catch {

        }
    };

    const loadHistoryAndConnect = async (me) => {
        meId = me.id;
        showChatRoom(me);
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
                    renderMessage(m, plaintext);
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
            connectHeaders: { 'X-Chat-Token': authToken },
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
            let ciphertextBase64;
            try {
                ciphertextBase64 = await OneLineCrypto.encrypt(cryptoKey, plaintext);
            } catch (err) {
                console.error('Encrypt failed', err);
                return;
            }
            const id = crypto.randomUUID();
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

        const uploadFile = async (file) => {
            if (!client.connected || !cryptoKey) {
                return;
            }
            if (file.size > MAX_FILE_BYTES) {
                setStatus('error', `File too large (max ${humanizeSize(MAX_FILE_BYTES)})`);
                return;
            }
            attachBtnEl.disabled = true;
            showProgress(`Encrypting ${file.name}...`);
            try {
                const fileKeyRaw = OneLineCrypto.randomRawKey();
                const fileKey = await OneLineCrypto.importRawKey(fileKeyRaw);
                const ciphertext = await OneLineCrypto.encryptBytes(fileKey, new Uint8Array(await file.arrayBuffer()));

                const prepResp = await fetch(`/api/chats/${publicId}/attachments`, {
                    method: 'POST',
                    headers: apiHeaders({ 'Content-Type': 'application/json' }),
                    credentials: 'same-origin',
                    body: JSON.stringify({ size: ciphertext.length }),
                });
                if (!prepResp.ok) {
                    throw new Error('prepare ' + prepResp.status);
                }
                const { attachmentId, uploadUrl } = await prepResp.json();

                const putHandle = xhrPut(uploadUrl, ciphertext, (r) => setProgress(r, file.name));
                activeUpload = putHandle.xhr;
                try {
                    await putHandle.promise;
                } finally {
                    activeUpload = null;
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

                const payload = JSON.stringify({
                    k: FILE_MARKER,
                    id: attachmentId,
                    name: file.name,
                    mime: file.type || 'application/octet-stream',
                    size: file.size,
                    key: OneLineCrypto.base64Encode(fileKeyRaw),
                });
                const content = await OneLineCrypto.encrypt(cryptoKey, payload);
                const messageId = crypto.randomUUID();
                client.publish({
                    destination: `/app/chat.${chatId}.send`,
                    body: JSON.stringify({ clientMessageId: messageId, content }),
                });
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
