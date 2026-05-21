(() => {
    const btn = document.getElementById('create-chat');
    const errorEl = document.getElementById('create-error');
    if (!btn) {
        return;
    }

    btn.addEventListener('click', async () => {
        btn.disabled = true;
        if (errorEl) {
            errorEl.hidden = true;
        }
        try {
            const secret = OneLineCrypto.randomSecret();
            const authToken = await OneLineCrypto.deriveAuthToken(secret);
            const resp = await fetch('/api/chats', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ authToken }),
            });
            if (!resp.ok) {
                throw new Error('create failed (' + resp.status + ')');
            }
            const data = await resp.json();
            window.location.href = `/c/${data.publicId}#${secret}`;
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
