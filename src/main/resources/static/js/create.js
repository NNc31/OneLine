(() => {
    const btn = document.getElementById('create-chat');
    const errorEl = document.getElementById('create-error');
    const ttlValueEl = document.getElementById('ttl-value');
    const ttlUnitEl = document.getElementById('ttl-unit');
    if (!btn) {
        return;
    }

    const readMeta = (name) => document.querySelector(`meta[name="${name}"]`)?.content || '';
    const csrfHeader = readMeta('csrf-header') || 'X-XSRF-TOKEN';
    const csrfToken = readMeta('csrf-token');

    const readTtlSeconds = () => {
        const n = Number.parseInt(ttlValueEl?.value, 10);
        if (!Number.isFinite(n) || n <= 0) {
            return null;
        }
        return n * Number.parseInt(ttlUnitEl.value, 10);
    };

    btn.addEventListener('click', async () => {
        btn.disabled = true;
        if (errorEl) {
            errorEl.hidden = true;
        }
        try {
            const secret = OneLineCrypto.randomSecret();
            const authToken = await OneLineCrypto.deriveAuthToken(secret);
            const messageTtlSeconds = readTtlSeconds();
            const payload = messageTtlSeconds ? { authToken, messageTtlSeconds } : { authToken };
            const resp = await fetch('/api/chats', {
                method: 'POST',
                headers: {'Content-Type': 'application/json', 'Accept': 'application/json', [csrfHeader]: csrfToken},
                credentials: 'same-origin',
                body: JSON.stringify(payload),
            });
            if (!resp.ok) {
                throw new Error('create failed (' + resp.status + ')');
            }
            const data = await resp.json();
            globalThis.location.href = `/c/${data.publicId}#${secret}`;
        } catch (e) {
            console.error(e);
            btn.disabled = false;
            if (errorEl) {
                errorEl.textContent = 'Could not create chat. Please try again.';
                errorEl.hidden = false;
            }
        }
    });
})();
