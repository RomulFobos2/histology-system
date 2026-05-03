/**
 * Универсальная клиентская фильтрация, поиск и пагинация таблиц.
 * Принимает УЖЕ ОТРЕНДЕРЕННЫЕ контролы (input/select/button) и привязывает к ним логику.
 *
 * Использование:
 *   var tf = initTableFilter('tableId', {
 *       perPage: 20,
 *       searchInput: document.getElementById('filterSearch'),
 *       selectFilters: [
 *           { element: document.getElementById('filterTissue'),
 *             cellSelector: '.filter-tissue', exact: true }
 *       ],
 *       resetButton: document.getElementById('filterReset')
 *   });
 *   tf.addCustomFilter(fn); // fn(row) -> boolean; опц. fn._isActive(), fn._reset()
 *
 * Опции:
 *   perPage         — кол-во строк на странице, 0 = без встроенной пагинации.
 *   searchInput     — input[type=text] для полнотекстового поиска по всем td (debounce 200мс).
 *   selectFilters   — массив { element, cellSelector?, column?, exact? }.
 *                     element        — <select>, его value сравнивается с textContent ячейки.
 *                     cellSelector   — CSS-селектор внутри <tr> для целевой ячейки.
 *                     column         — индекс столбца (если не задан cellSelector).
 *                     exact          — true: сравнение через ===; false: substring (default).
 *   resetButton     — <button>, при клике сбрасывает все фильтры.
 *
 * Порядок работы:
 *   1. Фильтр помечает строки классом 'filtered-out' и скрывает их (display:none).
 *   2. Пагинация (tablePagination.js) работает только с НЕ-отфильтрованными строками.
 *   3. Сортировка (tableSort.js) сортирует все строки, потом вызывает _paginationRefresh.
 */
(function () {
    'use strict';

    window.initTableFilter = function (tableId, options) {
        var opts = Object.assign({
            perPage: 20,
            searchInput: null,
            selectFilters: [],
            resetButton: null
        }, options || {});

        var table = document.getElementById(tableId);
        if (!table) return null;

        var tbody = table.querySelector('tbody');
        if (!tbody) return null;

        var searchTerm = opts.searchInput ? (opts.searchInput.value || '').trim() : '';
        var filterDefs = [];
        var filterValues = {};
        var customFilters = [];
        var debounceTimer = null;
        var emptyMessage = null;

        for (var i = 0; i < opts.selectFilters.length; i++) {
            var sf = opts.selectFilters[i];
            var key = sf.cellSelector || ('col-' + sf.column) || ('idx-' + i);
            filterDefs.push({
                key: key,
                element: sf.element || null,
                exact: !!sf.exact,
                cellSelector: sf.cellSelector || null,
                column: typeof sf.column === 'number' ? sf.column : -1
            });
            filterValues[key] = sf.element ? sf.element.value : '';
        }

        function getEmptyMessage() {
            if (!emptyMessage) {
                emptyMessage = document.createElement('div');
                emptyMessage.className = 'text-center text-muted py-4';
                emptyMessage.textContent = 'Ничего не найдено';
                emptyMessage.style.display = 'none';
                table.parentNode.insertBefore(emptyMessage, table.nextSibling);
            }
            return emptyMessage;
        }

        function applyFilters() {
            var rows = Array.prototype.slice.call(tbody.querySelectorAll('tr')).filter(function (tr) {
                return !tr.classList.contains('pagination-empty-row');
            });

            var visibleCount = 0;
            var term = (searchTerm || '').toLowerCase();
            var hasActiveFilter = !!term;

            for (var k in filterValues) {
                if (filterValues.hasOwnProperty(k) && filterValues[k]) {
                    hasActiveFilter = true;
                    break;
                }
            }
            if (!hasActiveFilter) {
                for (var ci = 0; ci < customFilters.length; ci++) {
                    if (typeof customFilters[ci]._isActive === 'function' && customFilters[ci]._isActive()) {
                        hasActiveFilter = true;
                        break;
                    }
                }
            }

            for (var r = 0; r < rows.length; r++) {
                var row = rows[r];
                var visible = true;

                if (term) {
                    var cells = row.querySelectorAll('td, th');
                    var matchSearch = false;
                    for (var c = 0; c < cells.length; c++) {
                        if ((cells[c].textContent || '').toLowerCase().indexOf(term) !== -1) {
                            matchSearch = true;
                            break;
                        }
                    }
                    if (!matchSearch) visible = false;
                }

                if (visible) {
                    for (var d = 0; d < filterDefs.length; d++) {
                        var def = filterDefs[d];
                        var fv = filterValues[def.key];
                        if (!fv) continue;

                        var cell;
                        if (def.cellSelector) {
                            cell = row.querySelector(def.cellSelector);
                        } else if (def.column >= 0) {
                            cell = row.children[def.column];
                        } else {
                            cell = null;
                        }
                        var text = cell ? (cell.textContent || '').trim() : '';

                        var matched = def.exact
                            ? text === fv
                            : text.toLowerCase().indexOf(fv.toLowerCase()) !== -1;
                        if (!matched) {
                            visible = false;
                            break;
                        }
                    }
                }

                if (visible) {
                    for (var cf = 0; cf < customFilters.length; cf++) {
                        if (!customFilters[cf](row)) {
                            visible = false;
                            break;
                        }
                    }
                }

                if (visible) {
                    row.classList.remove('filtered-out');
                    row.style.display = '';
                    visibleCount++;
                } else {
                    row.classList.add('filtered-out');
                    row.style.display = 'none';
                }
            }

            renumberRows();

            if (opts.resetButton) {
                opts.resetButton.style.display = hasActiveFilter ? '' : 'none';
            }

            var msg = getEmptyMessage();
            msg.style.display = visibleCount === 0 ? '' : 'none';
            table.style.display = visibleCount === 0 ? 'none' : '';

            if (table._paginationRefresh) {
                table._paginationRefresh();
            }
        }

        function renumberRows() {
            var rows = Array.prototype.slice.call(tbody.querySelectorAll('tr')).filter(function (tr) {
                return !tr.classList.contains('pagination-empty-row') &&
                       !tr.classList.contains('filtered-out');
            });
            for (var i = 0; i < rows.length; i++) {
                var n = rows[i].querySelector('.row-number');
                if (n) n.textContent = i + 1;
            }
        }

        function resetFilters() {
            if (opts.searchInput) opts.searchInput.value = '';
            searchTerm = '';
            for (var d = 0; d < filterDefs.length; d++) {
                if (filterDefs[d].element) filterDefs[d].element.value = '';
                filterValues[filterDefs[d].key] = '';
            }
            for (var cf = 0; cf < customFilters.length; cf++) {
                if (typeof customFilters[cf]._reset === 'function') {
                    customFilters[cf]._reset();
                }
            }
            applyFilters();
        }

        if (opts.searchInput) {
            opts.searchInput.addEventListener('input', function () {
                var self = this;
                if (debounceTimer) clearTimeout(debounceTimer);
                debounceTimer = setTimeout(function () {
                    searchTerm = self.value.trim();
                    applyFilters();
                }, 200);
            });
        }

        for (var d = 0; d < filterDefs.length; d++) {
            (function (def) {
                if (!def.element) return;
                def.element.addEventListener('change', function () {
                    filterValues[def.key] = this.value;
                    applyFilters();
                });
            })(filterDefs[d]);
        }

        if (opts.resetButton) {
            opts.resetButton.addEventListener('click', resetFilters);
            opts.resetButton.style.display = 'none';
        }

        if (opts.perPage > 0 && !table._paginationRefresh) {
            if (typeof window.initPagination === 'function') {
                window.initPagination(tableId, { perPage: opts.perPage });
            }
        }

        return {
            refresh: function () { applyFilters(); },
            addCustomFilter: function (fn) { customFilters.push(fn); }
        };
    };
})();
