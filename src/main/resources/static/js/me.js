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
        } catch (_) {
            return [];
        }
    };

    const save = (list) => {
        localStorage.setItem(KEY, JSON.stringify(list));
    };

    const render = () => {
        const list = load();
        listEl.innerHTML = '';

        if (list.length === 0) {
            emptyEl.hidden = false;
            return;
        }
        emptyEl.hidden = true;

        list.forEach(chat => {
            const li = document.createElement('li');
            li.className = 'session-chats-item';

            const href = `/c/${chat.publicId}#${chat.secret}`;
            const link = document.createElement('a');
            link.href = href;
            link.className = 'session-chats-link';
            link.textContent = chat.displayName ? `as ${chat.displayName}` : 'untitled visit';

            const time = document.createElement('time');
            time.dateTime = chat.lastUsed;
            time.className = 'session-chats-when';
            time.textContent = chat.lastUsed
                ? TIME_FORMATTER.format(new Date(chat.lastUsed))
                : '';

            const id = document.createElement('span');
            id.className = 'session-chats-id';
            id.textContent = chat.publicId.slice(0, 8) + '…';

            const removeBtn = document.createElement('button');
            removeBtn.type = 'button';
            removeBtn.className = 'session-chats-remove';
            removeBtn.textContent = 'Exit';
            removeBtn.addEventListener('click', () => {
                save(load().filter(c => c.publicId !== chat.publicId));
                render();
            });

            li.append(id, link, time, removeBtn);
            listEl.appendChild(li);
        });
    };

    render();
})();
