(() => {
    const STORAGE_KEY = 'theme';
    const root = document.documentElement;

    function normalizeTheme(value) {
        return value === 'light' ? 'light' : 'dark';
    }

    function getTheme() {
        try {
            return normalizeTheme(localStorage.getItem(STORAGE_KEY));
        } catch (error) {
            return 'dark';
        }
    }

    function applyTheme(value, persist = false) {
        const theme = normalizeTheme(value);
        const isLight = theme === 'light';

        root.classList.toggle('light-mode', isLight);
        root.classList.remove('light');
        root.dataset.theme = theme;
        root.style.colorScheme = theme;

        if (persist) {
            try {
                localStorage.setItem(STORAGE_KEY, theme);
            } catch (error) {
                // The selected theme still works for this page when storage is unavailable.
            }
        }

        window.dispatchEvent(new CustomEvent('app-theme-change', {
            detail: { theme }
        }));

        return theme;
    }

    window.appTheme = {
        get: getTheme,
        set(value) {
            return applyTheme(value, true);
        }
    };

    applyTheme(getTheme());

    window.addEventListener('storage', (event) => {
        if (event.key === STORAGE_KEY) {
            applyTheme(event.newValue);
        }
    });
})();
