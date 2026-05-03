/**
 * Клиентская пагинация таблиц.
 * Использование: initPagination('tableId', { perPage: 20 })
 *
 * Интеграция с tableFilter.js:
 *   - Строки с классом 'filtered-out' исключаются из пагинации.
 *   - tableFilter.js вызывает table._paginationRefresh() после применения фильтров.
 */
(function () {
    'use strict';

    window.initPagination = function (tableId, options) {
        var opts = Object.assign({ perPage: 20 }, options || {});
        var table = document.getElementById(tableId);
        if (!table) return;

        var tbody = table.querySelector('tbody');
        if (!tbody) return;

        // Если pagination уже инициализирована — destroy перед новой инициализацией
        if (typeof table._paginationDestroy === 'function') {
            table._paginationDestroy();
        }

        var visibleRows = [];
        var currentPage = 1;
        var wrapper = null;

        function collectVisibleRows() {
            visibleRows = Array.from(tbody.querySelectorAll('tr')).filter(function (tr) {
                return !tr.classList.contains('pagination-empty-row') &&
                       !tr.classList.contains('filtered-out');
            });
        }

        function totalPages() {
            return Math.max(1, Math.ceil(visibleRows.length / opts.perPage));
        }

        function showPage(page) {
            currentPage = Math.max(1, Math.min(page, totalPages()));
            var start = (currentPage - 1) * opts.perPage;
            var end = start + opts.perPage;

            for (var i = 0; i < visibleRows.length; i++) {
                visibleRows[i].style.display = (i >= start && i < end) ? '' : 'none';
            }

            renderControls();
        }

        function renderControls() {
            if (!wrapper) {
                wrapper = document.createElement('div');
                wrapper.className = 'pagination-wrapper d-flex justify-content-between align-items-center mt-3';
                table.parentNode.insertBefore(wrapper, table.nextSibling);
            }

            var total = totalPages();
            if (visibleRows.length <= opts.perPage) {
                wrapper.style.display = 'none';
                // Показать все видимые строки если пагинация не нужна
                for (var k = 0; k < visibleRows.length; k++) {
                    visibleRows[k].style.display = '';
                }
                return;
            }
            wrapper.style.display = '';

            var start = (currentPage - 1) * opts.perPage + 1;
            var end = Math.min(currentPage * opts.perPage, visibleRows.length);

            var html = '<div class="text-muted small">Показано ' + start + '–' + end + ' из ' + visibleRows.length + '</div>';
            html += '<nav><ul class="pagination pagination-sm mb-0">';

            html += '<li class="page-item' + (currentPage === 1 ? ' disabled' : '') + '">';
            html += '<a class="page-link" href="#" data-page="' + (currentPage - 1) + '">&laquo;</a></li>';

            var pages = getPageNumbers(currentPage, total);
            for (var i = 0; i < pages.length; i++) {
                var p = pages[i];
                if (p === '...') {
                    html += '<li class="page-item disabled"><span class="page-link">…</span></li>';
                } else {
                    html += '<li class="page-item' + (p === currentPage ? ' active' : '') + '">';
                    html += '<a class="page-link" href="#" data-page="' + p + '">' + p + '</a></li>';
                }
            }

            html += '<li class="page-item' + (currentPage === total ? ' disabled' : '') + '">';
            html += '<a class="page-link" href="#" data-page="' + (currentPage + 1) + '">&raquo;</a></li>';
            html += '</ul></nav>';

            wrapper.innerHTML = html;

            wrapper.querySelectorAll('a[data-page]').forEach(function (link) {
                link.addEventListener('click', function (e) {
                    e.preventDefault();
                    showPage(parseInt(this.getAttribute('data-page')));
                });
            });
        }

        function getPageNumbers(current, total) {
            if (total <= 7) {
                var arr = [];
                for (var i = 1; i <= total; i++) arr.push(i);
                return arr;
            }
            var pages = [1];
            if (current > 3) pages.push('...');
            for (var j = Math.max(2, current - 1); j <= Math.min(total - 1, current + 1); j++) {
                pages.push(j);
            }
            if (current < total - 2) pages.push('...');
            pages.push(total);
            return pages;
        }

        // Публичный API: пересчёт после фильтрации/сортировки
        table._paginationRefresh = function () {
            collectVisibleRows();
            showPage(1);
        };

        // Удалить wrapper и снять API — для последующего реинита (например, при смене perPage)
        table._paginationDestroy = function () {
            if (wrapper && wrapper.parentNode) {
                wrapper.parentNode.removeChild(wrapper);
            }
            wrapper = null;
            // Показать все строки (восстановить полный список)
            var rows = tbody.querySelectorAll('tr');
            for (var i = 0; i < rows.length; i++) {
                if (!rows[i].classList.contains('filtered-out')) {
                    rows[i].style.display = '';
                }
            }
            delete table._paginationRefresh;
            delete table._paginationDestroy;
        };

        collectVisibleRows();
        if (visibleRows.length > opts.perPage) {
            showPage(1);
        }
    };
})();
