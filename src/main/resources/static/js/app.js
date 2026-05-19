(() => {
    const root = document.querySelector('.chat-main .inner[data-public-id]');
    if (!root) {
        return;
    }

    const publicId = root.dataset.publicId;
    const chatToken = (window.location.hash || '').replace(/^#/, '');

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

    const TIME_FORMATTER = new Intl.DateTimeFormat(undefined, {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    });

    if (!chatToken) {
        missingSecretEl.hidden = false;
        return;
    }

    const apiHeaders = (extra) => ({
        'X-Chat-Token': chatToken,
        'Accept': 'application/json',
        ...(extra || {}),
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
    const HISTORY_PAGE = 50;

    const createMessageEl = (m, plaintext) => {
        if (seenMessageIds.has(m.id)) {
            return null;
        }
        seenMessageIds.add(m.id);
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
        body.textContent = plaintext;

        li.append(author, time, body);
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
            if (isNew && m.participantId !== meId && window.OneLineSound) {
                window.OneLineSound.play();
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
                secret: chatToken,
                displayName: displayName || null,
                lastUsed: new Date().toISOString(),
            });
            localStorage.setItem(KEY, JSON.stringify(filtered.slice(0, 50)));
        } catch (_) { /* localStorage unavailable, ignore */ }
    };

    const loadHistoryAndConnect = async (me) => {
        meId = me.id;
        showChatRoom(me);
        rememberChat(me.displayName);

        try {
            cryptoKey = await OneLineCrypto.deriveKey(chatToken, chatId);
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

    const connectWebSocket = () => {
        const wsUrl = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws';
        const client = new StompJs.Client({
            brokerURL: wsUrl,
            connectHeaders: { 'X-Chat-Token': chatToken },
            reconnectDelay: 2000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
        });

        client.onConnect = () => {
            setStatus('online', 'Online');
            client.subscribe(`/topic/chat.${chatId}`, (frame) => {
                try {
                    const m = JSON.parse(frame.body);
                    decryptAndRender(m);
                } catch (e) {
                    console.error('Bad message frame', e);
                }
            });
        };
        client.onWebSocketClose = () => setStatus('offline', 'Offline. Reconnecting...');
        client.onStompError = (frame) => {
            let reason = frame.headers.message || 'Disconnected';
            try {
                const parsed = JSON.parse(frame.body);
                if (parsed && parsed.error) {
                    reason = parsed.error;
                }
            } catch (_) { /* ignore */ }
            setStatus('error', reason);
        };

        client.activate();

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
            const id = (crypto.randomUUID ? crypto.randomUUID() : Date.now() + '-' + Math.random());
            client.publish({
                destination: `/app/chat.${chatId}.send`,
                body: JSON.stringify({ clientMessageId: id, content: ciphertextBase64 }),
            });
            sendInputEl.value = '';
            sendInputEl.focus();
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
                    if (parsed && parsed.error) {
                        message = parsed.error;
                    }
                } catch (_) { /* ignore */ }
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
})();
