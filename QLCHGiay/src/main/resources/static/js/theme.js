(() => {
    const STORAGE_KEY = 'theme';
    const DEFAULT_MODE = 'light';
    const AUTO_LIGHT_START_HOUR = 6;
    const AUTO_DARK_START_HOUR = 18;
    const root = document.documentElement;
    let autoRefreshTimer;

    function normalizeMode(value) {
        return ['light', 'dark', 'auto'].includes(value) ? value : DEFAULT_MODE;
    }

    function getMode() {
        try {
            return normalizeMode(localStorage.getItem(STORAGE_KEY));
        } catch (error) {
            return DEFAULT_MODE;
        }
    }

    function resolveTheme(mode, now = new Date()) {
        if (mode !== 'auto') {
            return mode;
        }
        const hour = now.getHours();
        return hour >= AUTO_LIGHT_START_HOUR && hour < AUTO_DARK_START_HOUR
            ? 'light'
            : 'dark';
    }

    function scheduleAutoRefresh(mode) {
        window.clearTimeout(autoRefreshTimer);
        if (mode !== 'auto') {
            return;
        }

        const now = new Date();
        const nextChange = new Date(now);
        if (now.getHours() < AUTO_LIGHT_START_HOUR) {
            nextChange.setHours(AUTO_LIGHT_START_HOUR, 0, 0, 0);
        } else if (now.getHours() < AUTO_DARK_START_HOUR) {
            nextChange.setHours(AUTO_DARK_START_HOUR, 0, 0, 0);
        } else {
            nextChange.setDate(nextChange.getDate() + 1);
            nextChange.setHours(AUTO_LIGHT_START_HOUR, 0, 0, 0);
        }

        autoRefreshTimer = window.setTimeout(() => {
            applyMode('auto');
        }, Math.max(1000, nextChange.getTime() - now.getTime()));
    }

    function applyMode(value, persist = false) {
        const mode = normalizeMode(value);
        const theme = resolveTheme(mode);
        const isLight = theme === 'light';

        root.classList.toggle('light-mode', isLight);
        root.classList.remove('light');
        root.dataset.theme = theme;
        root.dataset.themeMode = mode;
        root.style.colorScheme = theme;

        if (persist) {
            try {
                localStorage.setItem(STORAGE_KEY, mode);
            } catch (error) {
                // The selected theme still works for this page when storage is unavailable.
            }
        }

        scheduleAutoRefresh(mode);
        window.dispatchEvent(new CustomEvent('app-theme-change', {
            detail: { theme, mode }
        }));

        return theme;
    }

    window.appTheme = {
        get() {
            return resolveTheme(getMode());
        },
        getMode,
        set(value) {
            return applyMode(value, true);
        },
        refresh() {
            return applyMode(getMode());
        }
    };

    applyMode(getMode());

    window.addEventListener('storage', (event) => {
        if (event.key === STORAGE_KEY) {
            applyMode(event.newValue);
        }
    });

    document.addEventListener('visibilitychange', () => {
        if (!document.hidden && getMode() === 'auto') {
            applyMode('auto');
        }
    });
})();
