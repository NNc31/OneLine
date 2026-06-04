(() => {
    const KEY = 'oneline.sessionChats';
    const listEl = document.getElementById('session-chats-list');
    const emptyEl = document.getElementById('session-chats-empty');

    const TIME_FORMATTER = new Intl.DateTimeFormat(undefined, {
        year: 'numeric',
        month: 'short',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    });

    const load = () => {
        try {
            const raw = localStorage.getItem(KEY);
            return raw ? JSON.parse(raw) : [];
        } catch {
            return [];
        }
    };

    const save = (list) => {
        localStorage.setItem(KEY, JSON.stringify(list));
    };

    const removeChat = (publicId) => {
        save(load().filter(c => c.publicId !== publicId));
        render();
    };

    const buildRemoveButton = (publicId) => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'session-chats-remove';
        btn.setAttribute('aria-label', 'Forget this chat');
        btn.title = 'Forget this chat';
        btn.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">'
            + '<polyline points="3 6 5 6 21 6"></polyline>'
            + '<path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>'
            + '<line x1="10" y1="11" x2="10" y2="17"></line>'
            + '<line x1="14" y1="11" x2="14" y2="17"></line>'
            + '</svg>';
        btn.addEventListener('click', () => removeChat(publicId));
        return btn;
    };

    const buildChatItem = (chat) => {
        const li = document.createElement('li');
        li.className = 'session-chats-item';

        const link = document.createElement('a');
        link.href = `/c/${chat.publicId}#${chat.secret}`;
        link.className = 'session-chats-link';
        link.textContent = chat.displayName ? `as ${chat.displayName}` : 'untitled visit';

        const time = document.createElement('time');
        time.dateTime = chat.lastUsed;
        time.className = 'session-chats-when';
        time.textContent = chat.lastUsed ? TIME_FORMATTER.format(new Date(chat.lastUsed)) : '';

        const id = document.createElement('span');
        id.className = 'session-chats-id';
        id.textContent = chat.publicId.slice(0, 8) + '…';

        li.append(id, link, time, buildRemoveButton(chat.publicId));
        return li;
    };

    function render() {
        const list = load();
        listEl.innerHTML = '';
        if (list.length === 0) {
            emptyEl.hidden = false;
            return;
        }
        emptyEl.hidden = true;
        list.forEach(chat => listEl.appendChild(buildChatItem(chat)));
    }

    render();
})();
