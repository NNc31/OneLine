(() => {
    const room = document.querySelector('.chat-room');
    if (!room) {
        return;
    }

    const chatId = room.dataset.chatId;
    const chatToken = room.dataset.chatToken;
    const meId = Number(room.dataset.meId);

    const messagesEl = document.getElementById('messages');
    const sendForm = document.getElementById('send-form');
    const sendInput = document.getElementById('send-input');
    const statusEl = document.getElementById('conn-status');

    const seenMessageIds = new Set(
        Array.from(messagesEl.querySelectorAll('.message[data-message-id]')).map(el => Number(el.dataset.messageId))
    );

    const setStatus = (state, text) => {
        statusEl.dataset.state = state;
        statusEl.textContent = text;
    };

    const formatTime = (iso) => {
        const d = new Date(iso);
        const hh = String(d.getHours()).padStart(2, '0');
        const mm = String(d.getMinutes()).padStart(2, '0');
        return `${hh}:${mm}`;
    };

    const renderMessage = (m) => {
        if (seenMessageIds.has(m.id)) {
            return;
        }
        seenMessageIds.add(m.id);
        const li = document.createElement('li');
        li.className = 'message' + (m.participantId === meId ? ' mine' : '');
        li.dataset.messageId = String(m.id);

        const author = document.createElement('span');
        author.className = 'author';
        author.textContent = m.displayName;

        const body = document.createElement('span');
        body.className = 'body';
        body.textContent = m.content;

        const time = document.createElement('time');
        time.dateTime = m.createdAt;
        time.textContent = formatTime(m.createdAt);

        li.append(author, body, time);
        messagesEl.appendChild(li);
        messagesEl.scrollTop = messagesEl.scrollHeight;
    };

    messagesEl.scrollTop = messagesEl.scrollHeight;

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
                renderMessage(JSON.parse(frame.body));
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

    const newClientMessageId = () => (crypto.randomUUID ? crypto.randomUUID() : Date.now() + '-' + Math.random());

    sendForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const content = sendInput.value.trim();
        if (!content || !client.connected) {
            return;
        }
        client.publish({
            destination: `/app/chat.${chatId}.send`,
            body: JSON.stringify({ clientMessageId: newClientMessageId(), content }),
        });
        sendInput.value = '';
        sendInput.focus();
    });
})();
