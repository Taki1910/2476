(() => {
    const refreshEveryMs = 15000;
    let lastInteraction = Date.now();
    let formDirty = false;

    ['pointerdown', 'keydown', 'touchstart'].forEach((eventName) =>
        document.addEventListener(eventName, () => {
            lastInteraction = Date.now();
        }, {passive: true})
    );
    document.addEventListener('input', (event) => {
        lastInteraction = Date.now();
        if (event.target.closest('form')) formDirty = true;
    });
    document.addEventListener('submit', () => {
        formDirty = false;
    });

    window.setInterval(() => {
        const activeTag = document.activeElement?.tagName;
        const userIsEditing = ['INPUT', 'SELECT', 'TEXTAREA', 'BUTTON'].includes(activeTag);
        const recentlyActive = Date.now() - lastInteraction < refreshEveryMs;
        if (!document.hidden && !formDirty && !userIsEditing && !recentlyActive) {
            window.location.reload();
        }
    }, refreshEveryMs);
})();
