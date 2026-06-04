(() => {
    const STORAGE_KEY = 'oneline.theme';
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'light' || saved === 'dark') {
        document.documentElement.dataset.theme = saved;
    }

    const TIME_FORMATTER = new Intl.DateTimeFormat(undefined, {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    });

    const formatAllTimes = (root) => {
        root.querySelectorAll('time[datetime]').forEach(el => {
            const iso = el.getAttribute('datetime');
            if (!iso) {
                return;
            }
            const d = new Date(iso);
            if (!Number.isNaN(d.getTime())) {
                el.textContent = TIME_FORMATTER.format(d);
            }
        });
    };

    document.addEventListener('DOMContentLoaded', () => {
        const toggles = document.querySelectorAll('[data-theme-toggle]');
        toggles.forEach(btn => btn.addEventListener('click', () => {
            const current = document.documentElement.dataset.theme;
            const next = current === 'light' ? 'dark' : 'light';
            document.documentElement.dataset.theme = next;
            localStorage.setItem(STORAGE_KEY, next);
        }));

        formatAllTimes(document);
    });

    globalThis.OneLine = globalThis.OneLine || {};
    globalThis.OneLine.formatTimes = formatAllTimes;
})();
