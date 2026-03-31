/**
 * Клиентская сортировка таблиц по клику на заголовок.
 * Использование: initSortable('tableId')
 * Атрибуты на <th>: data-sortable, data-sort-type="text|number|date|percent"
 *
 * Интеграция: после сортировки вызывает table._paginationRefresh() (если есть).
 */
(function () {
    'use strict';

    window.initSortable = function (tableId) {
        var table = document.getElementById(tableId);
        if (!table) return;

        var headers = table.querySelectorAll('th[data-sortable]');
        if (!headers.length) return;

        headers.forEach(function (th) {
            th.style.cursor = 'pointer';
            th.style.userSelect = 'none';

            var icon = document.createElement('span');
            icon.className = 'sort-icon ms-1 text-muted';
            icon.textContent = '↕';
            th.appendChild(icon);

            th.addEventListener('click', function () {
                sortByColumn(table, th, icon, headers);
            });
        });
    };

    function sortByColumn(table, th, icon, allHeaders) {
        var tbody = table.querySelector('tbody');
        if (!tbody) return;

        var colIndex = Array.from(th.parentNode.children).indexOf(th);
        var sortType = th.getAttribute('data-sort-type') || 'text';
        var currentDir = th.getAttribute('data-sort-dir');
        var newDir = currentDir === 'asc' ? 'desc' : 'asc';

        // Сбросить все заголовки
        allHeaders.forEach(function (h) {
            h.removeAttribute('data-sort-dir');
            h.classList.remove('sort-asc', 'sort-desc');
            var si = h.querySelector('.sort-icon');
            if (si) {
                si.textContent = '↕';
                si.className = 'sort-icon ms-1 text-muted';
            }
        });

        th.setAttribute('data-sort-dir', newDir);
        th.classList.add('sort-' + newDir);
        icon.textContent = newDir === 'asc' ? '↑' : '↓';
        icon.className = 'sort-icon ms-1';

        // Сортируем ВСЕ строки (включая отфильтрованные) — чтобы DOM-порядок был правильным
        var rows = Array.from(tbody.querySelectorAll('tr')).filter(function (tr) {
            return !tr.classList.contains('pagination-empty-row');
        });

        rows.sort(function (a, b) {
            var aVal = getCellValue(a, colIndex);
            var bVal = getCellValue(b, colIndex);
            var cmp = compare(aVal, bVal, sortType);
            return newDir === 'asc' ? cmp : -cmp;
        });

        rows.forEach(function (row) {
            tbody.appendChild(row);
        });

        // Перенумерация только видимых строк
        var visibleIdx = 0;
        rows.forEach(function (row) {
            if (!row.classList.contains('filtered-out')) {
                visibleIdx++;
                var numCell = row.querySelector('.row-number');
                if (numCell) numCell.textContent = visibleIdx;
            }
        });

        // Пересчитать пагинацию
        if (table._paginationRefresh) {
            table._paginationRefresh();
        }
    }

    function getCellValue(row, index) {
        var cell = row.children[index];
        if (!cell) return '';
        return (cell.textContent || cell.innerText).trim();
    }

    function compare(a, b, sortType) {
        switch (sortType) {
            case 'number':
                return parseNum(a) - parseNum(b);
            case 'date':
                return parseDate(a) - parseDate(b);
            case 'percent':
                return parseNum(a) - parseNum(b);
            default:
                return a.localeCompare(b, 'ru');
        }
    }

    function parseNum(val) {
        var n = parseFloat(val.replace(/[^0-9.,\-]/g, '').replace(',', '.'));
        return isNaN(n) ? 0 : n;
    }

    function parseDate(val) {
        // dd.MM.yyyy HH:mm или dd.MM.yyyy
        var parts = val.split(/[\s.:/]+/);
        if (parts.length >= 3) {
            var d = new Date(parts[2], parts[1] - 1, parts[0],
                parts[3] || 0, parts[4] || 0);
            return d.getTime() || 0;
        }
        return 0;
    }
})();
