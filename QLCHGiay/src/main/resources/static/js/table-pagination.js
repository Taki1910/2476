(() => {
    const DEFAULT_PAGE_SIZE = 10;

    function createButton({ label, page, currentPage, disabled = false, icon = null, onSelect }) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'page-btn';
        button.disabled = disabled;
        button.setAttribute('aria-label', label);

        if (page === currentPage && icon === null) {
            button.classList.add('active');
            button.setAttribute('aria-current', 'page');
        }

        if (icon) {
            const iconElement = document.createElement('i');
            iconElement.className = `fa-solid ${icon}`;
            iconElement.setAttribute('aria-hidden', 'true');
            button.appendChild(iconElement);
        } else {
            button.textContent = String(page);
        }

        button.addEventListener('click', () => onSelect(page));
        return button;
    }

    function createTablePagination(options) {
        const table = document.querySelector(options.tableSelector);
        const info = document.querySelector(options.infoSelector);
        const buttons = document.querySelector(options.buttonsSelector);

        if (!table || !info || !buttons) {
            throw new Error('Không tìm thấy thành phần phân trang của bảng.');
        }

        const tbody = table.tBodies[0];
        const rows = [...tbody.querySelectorAll('tr:not(.empty-row)')];
        const pageSize = Number(options.pageSize) || DEFAULT_PAGE_SIZE;
        const itemLabel = options.itemLabel || 'mục';
        let filter = options.filter || (() => true);
        let currentPage = 1;
        let emptyRow = tbody.querySelector('.empty-row');

        if (!emptyRow) {
            emptyRow = document.createElement('tr');
            emptyRow.className = 'empty-row';
            const cell = document.createElement('td');
            cell.colSpan = table.tHead?.rows[0]?.cells.length || 1;
            cell.textContent = options.emptyMessage || 'Không có dữ liệu phù hợp.';
            emptyRow.appendChild(cell);
            tbody.appendChild(emptyRow);
        }

        function renderButtons(totalPages) {
            buttons.replaceChildren();

            buttons.appendChild(createButton({
                label: 'Trang trước',
                page: Math.max(1, currentPage - 1),
                currentPage,
                disabled: currentPage <= 1 || totalPages === 0,
                icon: 'fa-chevron-left',
                onSelect: goToPage
            }));

            for (let page = 1; page <= totalPages; page += 1) {
                buttons.appendChild(createButton({
                    label: `Trang ${page}`,
                    page,
                    currentPage,
                    onSelect: goToPage
                }));
            }

            buttons.appendChild(createButton({
                label: 'Trang sau',
                page: Math.min(Math.max(1, totalPages), currentPage + 1),
                currentPage,
                disabled: currentPage >= totalPages || totalPages === 0,
                icon: 'fa-chevron-right',
                onSelect: goToPage
            }));
        }

        function render() {
            const matchedRows = rows.filter(filter);
            const totalItems = matchedRows.length;
            const totalPages = Math.ceil(totalItems / pageSize);

            if (totalPages > 0) {
                currentPage = Math.min(Math.max(1, currentPage), totalPages);
            } else {
                currentPage = 1;
            }

            rows.forEach((row) => {
                row.hidden = true;
            });

            const start = totalItems === 0 ? 0 : (currentPage - 1) * pageSize;
            const end = Math.min(start + pageSize, totalItems);
            matchedRows.slice(start, end).forEach((row) => {
                row.hidden = false;
            });

            emptyRow.hidden = totalItems !== 0;
            info.textContent = totalItems === 0
                ? `Hiển thị 0 ${itemLabel}`
                : `Hiển thị ${start + 1}-${end} / ${totalItems} ${itemLabel}`;

            renderButtons(totalPages);
        }

        function goToPage(page) {
            currentPage = page;
            render();
        }

        render();

        return {
            refresh(nextFilter) {
                if (typeof nextFilter === 'function') {
                    filter = nextFilter;
                }
                currentPage = 1;
                render();
            }
        };
    }

    window.TablePagination = {
        create: createTablePagination
    };
})();
