/**
 * Универсальная клиентская фильтрация, поиск и пагинация таблиц.
 * Использование: initTableFilter('tableId', { filters: [...], searchPlaceholder: '...', perPage: 20 })
 *
 * Опции:
 *   searchPlaceholder (string) — плейсхолдер для поля поиска, по умолчанию "Поиск..."
 *   filters (array)           — массив фильтров: { column: number, label: string, options: [{value, text}] }
 *   perPage (number)          — кол-во строк на странице, 0 = без встроенной пагинации
 *
 * Порядок работы:
 *   1. Фильтр помечает строки классом 'filtered-out' и скрывает их (display:none).
 *   2. Пагинация (tablePagination.js) работает только с НЕ-отфильтрованными строками.
 *   3. Сортировка (tableSort.js) сортирует все строки, потом вызывает _paginationRefresh.
 *
 * Возвращает объект с методом refresh() для принудительного пересчёта фильтров извне.
 */
(function () {
    'use strict';

    window.initTableFilter = function (tableId, options) {
        var opts = Object.assign({
            searchPlaceholder: 'Поиск...',
            filters: [],
            perPage: 20
        }, options || {});

        var table = document.getElementById(tableId);
        if (!table) return null;

        var tbody = table.querySelector('tbody');
        if (!tbody) return null;

        // =====================================================================
        // Состояние
        // =====================================================================
        var searchTerm = '';
        var filterValues = {};   // column -> value
        var customFilters = [];  // массив function(row) -> boolean
        var debounceTimer = null;
        var emptyMessage = null; // элемент «ничего не найдено»

        // =====================================================================
        // Построение панели фильтров
        // =====================================================================
        var bar = document.createElement('div');
        bar.className = 'd-flex gap-2 mb-3 flex-wrap align-items-center';

        // Поле поиска
        var searchInput = document.createElement('input');
        searchInput.type = 'text';
        searchInput.className = 'form-control';
        searchInput.placeholder = opts.searchPlaceholder;
        searchInput.style.maxWidth = '300px';
        bar.appendChild(searchInput);

        // Выпадающие фильтры
        for (var i = 0; i < opts.filters.length; i++) {
            var f = opts.filters[i];
            filterValues[f.column] = '';

            var select = document.createElement('select');
            select.className = 'form-select';
            select.style.maxWidth = '220px';
            select.setAttribute('data-filter-col', f.column);

            // Первый пункт — «все»
            var defaultOpt = document.createElement('option');
            defaultOpt.value = '';
            defaultOpt.textContent = f.label;
            select.appendChild(defaultOpt);

            for (var j = 0; j < f.options.length; j++) {
                var opt = document.createElement('option');
                opt.value = f.options[j].value;
                opt.textContent = f.options[j].text;
                select.appendChild(opt);
            }

            bar.appendChild(select);
        }

        // Кнопка сброса
        var resetBtn = document.createElement('button');
        resetBtn.type = 'button';
        resetBtn.className = 'btn btn-outline-secondary btn-sm';
        resetBtn.innerHTML = '<i class="bi bi-x-lg"></i> Сброс';
        resetBtn.style.display = 'none';
        bar.appendChild(resetBtn);

        // Вставляем панель перед таблицей внутри родительского контейнера
        table.parentNode.insertBefore(bar, table);

        // =====================================================================
        // Элемент «ничего не найдено»
        // =====================================================================
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

        // =====================================================================
        // Основная логика фильтрации
        // =====================================================================
        function applyFilters() {
            var rows = Array.from(tbody.querySelectorAll('tr')).filter(function (tr) {
                return !tr.classList.contains('pagination-empty-row');
            });

            var visibleCount = 0;
            var term = searchTerm.toLowerCase();
            var hasActiveFilter = !!term;

            for (var col in filterValues) {
                if (filterValues.hasOwnProperty(col) && filterValues[col]) {
                    hasActiveFilter = true;
                    break;
                }
            }

            for (var r = 0; r < rows.length; r++) {
                var row = rows[r];
                var visible = true;

                // --- Текстовый поиск: ANY ячейка содержит подстроку ---
                if (term) {
                    var cells = row.querySelectorAll('td, th');
                    var matchSearch = false;
                    for (var c = 0; c < cells.length; c++) {
                        var cellText = (cells[c].textContent || '').toLowerCase();
                        if (cellText.indexOf(term) !== -1) {
                            matchSearch = true;
                            break;
                        }
                    }
                    if (!matchSearch) visible = false;
                }

                // --- Выпадающие фильтры: AND между всеми ---
                if (visible) {
                    for (var col in filterValues) {
                        if (!filterValues.hasOwnProperty(col)) continue;
                        var fv = filterValues[col];
                        if (!fv) continue; // пустое значение = «все»

                        var cell = row.children[parseInt(col, 10)];
                        var text = cell ? (cell.textContent || '').toLowerCase() : '';
                        if (text.indexOf(fv.toLowerCase()) === -1) {
                            visible = false;
                            break;
                        }
                    }
                }

                // --- Пользовательские фильтры ---
                if (visible) {
                    for (var cf = 0; cf < customFilters.length; cf++) {
                        if (!customFilters[cf](row)) {
                            visible = false;
                            break;
                        }
                    }
                }

                // --- Помечаем строку ---
                if (visible) {
                    row.classList.remove('filtered-out');
                    row.style.display = '';
                    visibleCount++;
                } else {
                    row.classList.add('filtered-out');
                    row.style.display = 'none';
                }
            }

            // Перенумерация видимых строк
            renumberRows();

            // Кнопка сброса
            resetBtn.style.display = hasActiveFilter ? '' : 'none';

            // Сообщение «ничего не найдено»
            var msg = getEmptyMessage();
            msg.style.display = visibleCount === 0 ? '' : 'none';
            table.style.display = visibleCount === 0 ? 'none' : '';

            // Интеграция с пагинацией — пересчитать после фильтрации
            if (table._paginationRefresh) {
                table._paginationRefresh();
            }
        }

        // =====================================================================
        // Перенумерация видимых строк (ячейки с классом row-number)
        // =====================================================================
        function renumberRows() {
            var rows = Array.from(tbody.querySelectorAll('tr')).filter(function (tr) {
                return !tr.classList.contains('pagination-empty-row') &&
                       !tr.classList.contains('filtered-out');
            });
            for (var i = 0; i < rows.length; i++) {
                var numCell = rows[i].querySelector('.row-number');
                if (numCell) numCell.textContent = i + 1;
            }
        }

        // =====================================================================
        // Сброс фильтров
        // =====================================================================
        function resetFilters() {
            searchInput.value = '';
            searchTerm = '';
            var selects = bar.querySelectorAll('select[data-filter-col]');
            for (var s = 0; s < selects.length; s++) {
                selects[s].selectedIndex = 0;
                var col = selects[s].getAttribute('data-filter-col');
                filterValues[col] = '';
            }
            applyFilters();
        }

        // =====================================================================
        // Обработчики событий
        // =====================================================================

        // Поиск с debounce 200 мс
        searchInput.addEventListener('input', function () {
            var self = this;
            if (debounceTimer) clearTimeout(debounceTimer);
            debounceTimer = setTimeout(function () {
                searchTerm = self.value.trim();
                applyFilters();
            }, 200);
        });

        // Выпадающие фильтры — немедленная реакция
        var selects = bar.querySelectorAll('select[data-filter-col]');
        for (var s = 0; s < selects.length; s++) {
            selects[s].addEventListener('change', function () {
                var col = this.getAttribute('data-filter-col');
                filterValues[col] = this.value;
                applyFilters();
            });
        }

        // Кнопка сброса
        resetBtn.addEventListener('click', resetFilters);

        // =====================================================================
        // Встроенная пагинация (если perPage > 0 и нет внешней)
        // =====================================================================
        if (opts.perPage > 0 && !table._paginationRefresh) {
            if (typeof window.initPagination === 'function') {
                window.initPagination(tableId, { perPage: opts.perPage });
            }
        }

        // =====================================================================
        // Публичный API
        // =====================================================================
        return {
            /** Принудительно пересчитать фильтры (например, после добавления строк) */
            refresh: function () {
                applyFilters();
            },
            /** Добавить пользовательский фильтр: fn(row) -> boolean (true = показать) */
            addCustomFilter: function (fn) {
                customFilters.push(fn);
            }
        };
    };
})();
