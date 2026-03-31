/**
 * ScenarioEditor — редактор шагов сценария симуляции.
 * Управление списком шагов + визуальный редактор ожидаемого состояния через SVG-схему.
 */
var ScenarioEditor = (function () {

    var steps = [];         // [{stepNumber, instructionText, expectedState, faultEvent, forbiddenActions, stepTimeLimit}]
    var schemaElements = []; // [{id, name, elementType, posX, posY, width, height, initialState, rotation}]
    var schemaConnections = [];
    var schemaWidth = 1200;
    var schemaHeight = 800;

    var editingStepIndex = -1;
    var tempExpectedState = {}; // {elementId: true/false}

    function init() {
        document.getElementById('btnAddStep').addEventListener('click', addStep);
        document.getElementById('btnSaveSteps').addEventListener('click', saveSteps);
        document.getElementById('stateEditorClose').addEventListener('click', closeStateEditor);
        document.getElementById('stateEditorCancel').addEventListener('click', closeStateEditor);
        document.getElementById('stateEditorApply').addEventListener('click', applyState);

        document.getElementById('stateEditorOverlay').addEventListener('click', function (e) {
            if (e.target === this) closeStateEditor();
        });

        loadSteps();
    }

    // ========== Steps CRUD ==========

    function loadSteps() {
        fetch('/employee/chief/scenarios/loadSteps/' + scenarioId)
            .then(function (r) { return r.json(); })
            .then(function (data) {
                steps = data.map(function (s) {
                    return {
                        stepNumber: s.stepNumber,
                        instructionText: s.instructionText || '',
                        expectedState: s.expectedState || '{}',
                        faultEvent: s.faultEvent || '',
                        forbiddenActions: s.forbiddenActions || '[]',
                        stepTimeLimit: s.stepTimeLimit || null
                    };
                });
                renderSteps();
            });
    }

    function addStep() {
        steps.push({
            stepNumber: steps.length + 1,
            instructionText: '',
            expectedState: '{}',
            faultEvent: '',
            forbiddenActions: '[]',
            stepTimeLimit: null
        });
        renderSteps();
        var cards = document.querySelectorAll('.step-card');
        if (cards.length > 0) {
            var last = cards[cards.length - 1];
            last.scrollIntoView({ behavior: 'smooth', block: 'center' });
            last.querySelector('textarea').focus();
        }
    }

    function removeStep(index) {
        if (!confirm('Удалить шаг ' + (index + 1) + '?')) return;
        steps.splice(index, 1);
        renumberSteps();
        renderSteps();
    }

    function moveStep(fromIndex, toIndex) {
        if (toIndex < 0 || toIndex >= steps.length) return;
        var item = steps.splice(fromIndex, 1)[0];
        steps.splice(toIndex, 0, item);
        renumberSteps();
        renderSteps();
    }

    function renumberSteps() {
        steps.forEach(function (s, i) { s.stepNumber = i + 1; });
    }

    function renderSteps() {
        var container = document.getElementById('stepsContainer');
        var noMsg = document.getElementById('noStepsMessage');
        container.innerHTML = '';

        if (steps.length === 0) {
            noMsg.style.display = 'block';
            return;
        }
        noMsg.style.display = 'none';

        steps.forEach(function (step, index) {
            var card = document.createElement('div');
            card.className = 'step-card';
            card.setAttribute('draggable', 'true');
            card.dataset.index = index;

            var stateObj = parseState(step.expectedState);
            var stateKeys = Object.keys(stateObj);
            var stateBadgesHtml = '';
            if (stateKeys.length > 0) {
                stateBadgesHtml = '<div class="step-state-badges mt-2">';
                stateKeys.forEach(function (key) {
                    var val = stateObj[key];
                    var cls = val ? 'bg-success' : 'bg-secondary';
                    stateBadgesHtml += '<span class="badge ' + cls + '">' + escapeHtml(key) + ': ' + (val ? 'ВКЛ' : 'ВЫКЛ') + '</span>';
                });
                stateBadgesHtml += '</div>';
            }

            // Parse fault data for this step
            var faultObj = parseFaultEvent(step.faultEvent);
            var forbiddenArr = parseForbiddenActions(step.forbiddenActions);
            var hasFault = faultObj !== null || forbiddenArr.length > 0 || step.stepTimeLimit;
            var faultCollapseId = 'faultCollapse_' + index;

            card.innerHTML =
                '<div class="step-actions">' +
                '  <button class="btn btn-outline-secondary btn-sm btn-move-up" title="Вверх"><i class="bi bi-arrow-up"></i></button>' +
                '  <button class="btn btn-outline-secondary btn-sm btn-move-down" title="Вниз"><i class="bi bi-arrow-down"></i></button>' +
                '  <button class="btn btn-outline-danger btn-sm btn-remove" title="Удалить"><i class="bi bi-trash"></i></button>' +
                '</div>' +
                '<div class="d-flex align-items-start gap-3">' +
                '  <span class="drag-handle" title="Перетащить"><i class="bi bi-grip-vertical"></i></span>' +
                '  <span class="step-number">' + step.stepNumber + '</span>' +
                '  <div class="flex-grow-1">' +
                '    <textarea class="form-control mb-2 step-instruction" rows="2" placeholder="Инструкция для оператора...">' +
                       escapeHtml(step.instructionText) +
                '    </textarea>' +
                '    <div class="d-flex gap-2 align-items-center flex-wrap">' +
                '      <button class="btn btn-outline-info btn-sm btn-set-state">' +
                '        <i class="bi bi-cpu"></i> Ожидаемое состояние' +
                '        <span class="badge bg-light text-dark ms-1">' + stateKeys.length + '</span>' +
                '      </button>' +
                '      <button class="btn btn-sm ' + (hasFault ? 'btn-warning' : 'btn-outline-warning') + ' btn-toggle-fault" type="button"' +
                '        data-bs-toggle="collapse" data-bs-target="#' + faultCollapseId + '">' +
                '        <i class="bi bi-exclamation-triangle"></i> Аварийные настройки' +
                (hasFault ? ' <span class="badge bg-dark text-white ms-1"><i class="bi bi-check-lg"></i></span>' : '') +
                '      </button>' +
                '    </div>' +
                    stateBadgesHtml +
                '    <div class="collapse ' + (hasFault ? 'show' : '') + '" id="' + faultCollapseId + '">' +
                '      <div class="fault-settings mt-2">' +
                         buildFaultSettingsHtml(index, step) +
                '      </div>' +
                '    </div>' +
                '  </div>' +
                '</div>';

            // Event listeners
            card.querySelector('.btn-move-up').addEventListener('click', function () { moveStep(index, index - 1); });
            card.querySelector('.btn-move-down').addEventListener('click', function () { moveStep(index, index + 1); });
            card.querySelector('.btn-remove').addEventListener('click', function () { removeStep(index); });
            card.querySelector('.btn-set-state').addEventListener('click', function () { openStateEditor(index); });
            card.querySelector('.step-instruction').addEventListener('input', function () {
                steps[index].instructionText = this.value;
            });

            // Fault settings listeners
            bindFaultSettingsListeners(card, index);

            // Drag-and-drop reordering
            card.addEventListener('dragstart', function (e) {
                e.dataTransfer.setData('text/plain', index);
                card.classList.add('dragging');
            });
            card.addEventListener('dragend', function () { card.classList.remove('dragging'); });
            card.addEventListener('dragover', function (e) {
                e.preventDefault();
                card.classList.add('drag-over');
            });
            card.addEventListener('dragleave', function () { card.classList.remove('drag-over'); });
            card.addEventListener('drop', function (e) {
                e.preventDefault();
                card.classList.remove('drag-over');
                var from = parseInt(e.dataTransfer.getData('text/plain'));
                var to = index;
                if (from !== to) moveStep(from, to);
            });

            container.appendChild(card);
        });
    }

    // ========== Save/Load Steps ==========

    function saveSteps() {
        var indicator = document.getElementById('stepsSaveIndicator');
        indicator.textContent = 'Сохранение...';
        indicator.className = 'text-warning';

        var payload = steps.map(function (s, i) {
            return {
                stepNumber: i + 1,
                instructionText: s.instructionText,
                expectedState: s.expectedState,
                faultEvent: s.faultEvent || null,
                forbiddenActions: s.forbiddenActions || null,
                stepTimeLimit: s.stepTimeLimit || null
            };
        });

        fetch('/employee/chief/scenarios/saveSteps/' + scenarioId, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        })
        .then(function (r) { return r.json(); })
        .then(function (data) {
            if (data.success) {
                indicator.textContent = 'Сохранено';
                indicator.className = 'text-success';
            } else {
                indicator.textContent = 'Ошибка сохранения';
                indicator.className = 'text-danger';
            }
            setTimeout(function () { indicator.textContent = ''; }, 3000);
        })
        .catch(function () {
            indicator.textContent = 'Ошибка сети';
            indicator.className = 'text-danger';
        });
    }

    // ========== State Editor ==========

    function openStateEditor(stepIndex) {
        editingStepIndex = stepIndex;
        var step = steps[stepIndex];
        tempExpectedState = parseState(step.expectedState);

        document.getElementById('stateEditorStepNum').textContent = step.stepNumber;
        document.getElementById('stateEditorOverlay').classList.add('show');
        document.body.style.overflow = 'hidden';

        loadSchemaForStateEditor();
    }

    function closeStateEditor() {
        document.getElementById('stateEditorOverlay').classList.remove('show');
        document.body.style.overflow = '';
        editingStepIndex = -1;
    }

    function applyState() {
        if (editingStepIndex < 0) return;
        // Remove entries where state matches initialState (no change expected)
        var cleanState = {};
        Object.keys(tempExpectedState).forEach(function (key) {
            cleanState[key] = tempExpectedState[key];
        });
        steps[editingStepIndex].expectedState = JSON.stringify(cleanState);
        closeStateEditor();
        renderSteps();
    }

    function loadSchemaForStateEditor() {
        var sid = currentSchemaId || document.getElementById('schemaSelect').value;
        if (!sid) {
            alert('Сначала выберите мнемосхему в настройках сценария.');
            closeStateEditor();
            return;
        }

        fetch('/employee/chief/scenarios/loadSchemaElements/' + sid)
            .then(function (r) { return r.json(); })
            .then(function (data) {
                schemaElements = data.elements || [];
                schemaConnections = data.connections || [];
                schemaWidth = data.width || 1200;
                schemaHeight = data.height || 800;
                renderStateEditorSvg();
            })
            .catch(function () {
                alert('Не удалось загрузить данные схемы');
                closeStateEditor();
            });
    }

    function renderStateEditorSvg() {
        var svgEl = document.getElementById('stateEditorSvg');
        svgEl.setAttribute('width', schemaWidth);
        svgEl.setAttribute('height', schemaHeight);
        svgEl.setAttribute('viewBox', '0 0 ' + schemaWidth + ' ' + schemaHeight);

        // Get symbol defs from SchemaEditor if available
        var defsHtml = '';
        if (typeof SchemaEditor !== 'undefined' && SchemaEditor.getSymbolDefs) {
            defsHtml = SchemaEditor.getSymbolDefs();
        }

        svgEl.innerHTML = defsHtml +
            '<rect width="' + schemaWidth + '" height="' + schemaHeight + '" fill="#f8f9fa"/>';

        // Render connections
        schemaConnections.forEach(function (conn) {
            var sourceEl = schemaElements.find(function (e) { return e.id === conn.sourceElementId; });
            var targetEl = schemaElements.find(function (e) { return e.id === conn.targetElementId; });
            if (!sourceEl || !targetEl) return;

            var line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            line.setAttribute('x1', sourceEl.posX + (sourceEl.width || 60) / 2);
            line.setAttribute('y1', sourceEl.posY + (sourceEl.height || 60) / 2);
            line.setAttribute('x2', targetEl.posX + (targetEl.width || 60) / 2);
            line.setAttribute('y2', targetEl.posY + (targetEl.height || 60) / 2);
            line.setAttribute('class', 'connection-line');
            svgEl.appendChild(line);
        });

        // Render elements
        schemaElements.forEach(function (el) {
            var isOn = tempExpectedState.hasOwnProperty(el.name)
                ? tempExpectedState[el.name]
                : el.initialState;

            var group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
            group.setAttribute('class', 'element-group' + (tempExpectedState.hasOwnProperty(el.name) && tempExpectedState[el.name] ? ' state-on' : ''));
            group.setAttribute('transform', 'translate(' + el.posX + ',' + el.posY + ')' +
                (el.rotation ? ' rotate(' + el.rotation + ',' + ((el.width || 60) / 2) + ',' + ((el.height || 60) / 2) + ')' : ''));
            group.style.cursor = 'pointer';

            // Border rect
            var border = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
            border.setAttribute('class', 'element-border');
            border.setAttribute('width', el.width || 60);
            border.setAttribute('height', el.height || 60);
            border.setAttribute('fill', 'transparent');
            border.setAttribute('stroke', tempExpectedState.hasOwnProperty(el.name) ? (tempExpectedState[el.name] ? '#198754' : '#dc3545') : '#dee2e6');
            border.setAttribute('stroke-width', tempExpectedState.hasOwnProperty(el.name) ? '2.5' : '1');
            border.setAttribute('rx', '4');
            group.appendChild(border);

            // Use symbol
            var state = isOn ? 'on' : 'off';
            var use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
            use.setAttribute('href', '#symbol-' + el.elementType + '-' + state);
            use.setAttribute('width', el.width || 60);
            use.setAttribute('height', el.height || 60);
            group.appendChild(use);

            // Label
            var label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            label.setAttribute('x', (el.width || 60) / 2);
            label.setAttribute('y', (el.height || 60) + 14);
            label.setAttribute('text-anchor', 'middle');
            label.setAttribute('font-size', '11');
            label.setAttribute('fill', '#333');
            label.textContent = el.name || '';
            group.appendChild(label);

            // Click handler
            group.addEventListener('click', function () {
                toggleElementState(el.name, group, border, use, el);
            });

            svgEl.appendChild(group);
        });
    }

    function toggleElementState(elName, group, border, use, el) {
        if (tempExpectedState.hasOwnProperty(elName)) {
            if (tempExpectedState[elName]) {
                // Was ON -> set OFF
                tempExpectedState[elName] = false;
                border.setAttribute('stroke', '#dc3545');
                border.setAttribute('stroke-width', '2.5');
                group.classList.remove('state-on');
                use.setAttribute('href', '#symbol-' + el.elementType + '-off');
            } else {
                // Was OFF -> remove from state (back to initial)
                delete tempExpectedState[elName];
                border.setAttribute('stroke', '#dee2e6');
                border.setAttribute('stroke-width', '1');
                group.classList.remove('state-on');
                var initState = el.initialState ? 'on' : 'off';
                use.setAttribute('href', '#symbol-' + el.elementType + '-' + initState);
            }
        } else {
            // Not set -> set ON
            tempExpectedState[elName] = true;
            border.setAttribute('stroke', '#198754');
            border.setAttribute('stroke-width', '2.5');
            group.classList.add('state-on');
            use.setAttribute('href', '#symbol-' + el.elementType + '-on');
        }
    }

    // ========== Utils ==========

    function parseState(json) {
        if (!json || json === '{}') return {};
        try { return JSON.parse(json); }
        catch (e) { return {}; }
    }

    function escapeHtml(str) {
        if (!str) return '';
        var div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    // ========== Fault Settings ==========

    function parseFaultEvent(json) {
        if (!json || json === '') return null;
        try { return JSON.parse(json); }
        catch (e) { return null; }
    }

    function parseForbiddenActions(json) {
        if (!json || json === '[]' || json === '') return [];
        try { return JSON.parse(json); }
        catch (e) { return []; }
    }

    function buildFaultSettingsHtml(index, step) {
        var faultObj = parseFaultEvent(step.faultEvent);
        var forbiddenArr = parseForbiddenActions(step.forbiddenActions);

        var html = '';

        // --- Step time limit ---
        html += '<div class="row g-2 mb-2">' +
            '<div class="col-md-4">' +
            '  <label class="form-label fault-label"><i class="bi bi-hourglass-split"></i> Время на шаг (сек)</label>' +
            '  <input type="number" class="form-control form-control-sm fault-step-time" min="0" max="9999" placeholder="Без ограничения"' +
            '    value="' + (step.stepTimeLimit || '') + '">' +
            '</div>' +
            '</div>';

        // --- Fault event ---
        html += '<div class="mb-2">' +
            '<label class="form-label fault-label"><i class="bi bi-lightning"></i> Аварийное событие</label>' +
            '<div class="row g-2">' +
            '  <div class="col-md-3">' +
            '    <select class="form-select form-select-sm fault-type">' +
            '      <option value="">— нет —</option>' +
            '      <option value="ELEMENT_FAILURE"' + (faultObj && faultObj.type === 'ELEMENT_FAILURE' ? ' selected' : '') + '>Отказ элемента</option>' +
            '      <option value="PRESSURE_ANOMALY"' + (faultObj && faultObj.type === 'PRESSURE_ANOMALY' ? ' selected' : '') + '>Аномалия давления</option>' +
            '      <option value="OVERHEAT"' + (faultObj && faultObj.type === 'OVERHEAT' ? ' selected' : '') + '>Перегрев</option>' +
            '      <option value="FALSE_ALARM"' + (faultObj && faultObj.type === 'FALSE_ALARM' ? ' selected' : '') + '>Ложное срабатывание</option>' +
            '    </select>' +
            '  </div>' +
            '  <div class="col-md-2">' +
            '    <input type="text" class="form-control form-control-sm fault-element" placeholder="Элемент (VP5)"' +
            '      value="' + escapeHtml(faultObj ? faultObj.elementName || '' : '') + '">' +
            '  </div>' +
            '  <div class="col-md-4">' +
            '    <input type="text" class="form-control form-control-sm fault-message" placeholder="Сообщение оператору"' +
            '      value="' + escapeHtml(faultObj ? faultObj.message || '' : '') + '">' +
            '  </div>' +
            '  <div class="col-md-3">' +
            '    <div class="form-check mt-1">' +
            '      <input type="checkbox" class="form-check-input fault-lock"' + (faultObj && faultObj.lockElement ? ' checked' : '') + '>' +
            '      <label class="form-check-label" style="font-size:0.85rem;">Заблокировать элемент</label>' +
            '    </div>' +
            '  </div>' +
            '</div>' +
            '</div>';

        // --- Sensor overrides ---
        var overrides = (faultObj && faultObj.sensorOverrides) ? faultObj.sensorOverrides : {};
        html += '<div class="mb-2">' +
            '<label class="form-label fault-label"><i class="bi bi-speedometer2"></i> Показания датчиков (переопределение)</label>' +
            '<div class="sensor-overrides-list" data-step-index="' + index + '">';

        Object.keys(overrides).forEach(function (sName) {
            html += buildSensorOverrideRowHtml(sName, overrides[sName]);
        });

        html += '</div>' +
            '<button type="button" class="btn btn-outline-secondary btn-sm mt-1 btn-add-sensor-override">' +
            '  <i class="bi bi-plus"></i> Добавить датчик' +
            '</button>' +
            '</div>';

        // --- Forbidden actions ---
        html += '<div class="mb-1">' +
            '<label class="form-label fault-label"><i class="bi bi-ban"></i> Запрещённые действия</label>' +
            '<div class="forbidden-actions-list" data-step-index="' + index + '">';

        forbiddenArr.forEach(function (fa, faIndex) {
            html += buildForbiddenActionRowHtml(index, faIndex, fa);
        });

        html += '</div>' +
            '<button type="button" class="btn btn-outline-secondary btn-sm mt-1 btn-add-forbidden">' +
            '  <i class="bi bi-plus"></i> Добавить запрет' +
            '</button>' +
            '</div>';

        return html;
    }

    function buildSensorOverrideRowHtml(sName, sValue) {
        return '<div class="row g-1 mb-1 sensor-override-row">' +
            '<div class="col-md-4">' +
            '  <input type="text" class="form-control form-control-sm so-name" placeholder="Имя датчика (PT1)" value="' + escapeHtml(sName || '') + '">' +
            '</div>' +
            '<div class="col-md-4">' +
            '  <input type="number" class="form-control form-control-sm so-value" step="0.01" placeholder="Значение" value="' + (sValue != null ? sValue : '') + '">' +
            '</div>' +
            '<div class="col-md-2">' +
            '  <button type="button" class="btn btn-outline-danger btn-sm btn-remove-sensor-override"><i class="bi bi-x"></i></button>' +
            '</div>' +
            '</div>';
    }

    function buildForbiddenActionRowHtml(stepIndex, faIndex, fa) {
        fa = fa || { elementName: '', action: 'on', penalty: 'FAIL', message: '' };
        return '<div class="row g-1 mb-1 forbidden-action-row">' +
            '<div class="col-md-2">' +
            '  <input type="text" class="form-control form-control-sm fa-element" placeholder="Элемент" value="' + escapeHtml(fa.elementName) + '">' +
            '</div>' +
            '<div class="col-md-2">' +
            '  <select class="form-select form-select-sm fa-action">' +
            '    <option value="on"' + (fa.action === 'on' ? ' selected' : '') + '>Включить (on)</option>' +
            '    <option value="off"' + (fa.action === 'off' ? ' selected' : '') + '>Выключить (off)</option>' +
            '  </select>' +
            '</div>' +
            '<div class="col-md-2">' +
            '  <select class="form-select form-select-sm fa-penalty">' +
            '    <option value="FAIL"' + (fa.penalty === 'FAIL' ? ' selected' : '') + '>Провал</option>' +
            '    <option value="WARNING"' + (fa.penalty === 'WARNING' ? ' selected' : '') + '>Предупреждение</option>' +
            '    <option value="TIME_PENALTY"' + (fa.penalty === 'TIME_PENALTY' ? ' selected' : '') + '>Штраф времени</option>' +
            '  </select>' +
            '</div>' +
            '<div class="col-md-4">' +
            '  <input type="text" class="form-control form-control-sm fa-message" placeholder="Сообщение" value="' + escapeHtml(fa.message) + '">' +
            '</div>' +
            '<div class="col-md-2">' +
            '  <button type="button" class="btn btn-outline-danger btn-sm btn-remove-forbidden" title="Удалить"><i class="bi bi-x"></i></button>' +
            '</div>' +
            '</div>';
    }

    function bindFaultSettingsListeners(card, index) {
        // Step time limit
        var timeInput = card.querySelector('.fault-step-time');
        if (timeInput) {
            timeInput.addEventListener('change', function () {
                var val = parseInt(this.value);
                steps[index].stepTimeLimit = (val > 0) ? val : null;
            });
        }

        // Fault event fields
        var faultType = card.querySelector('.fault-type');
        var faultElement = card.querySelector('.fault-element');
        var faultMessage = card.querySelector('.fault-message');
        var faultLock = card.querySelector('.fault-lock');

        function updateFaultEvent() {
            if (!faultType) return;
            var type = faultType.value;
            if (!type) {
                steps[index].faultEvent = '';
                return;
            }
            var faultObj = {
                type: type,
                elementName: faultElement.value.trim(),
                message: faultMessage.value.trim(),
                lockElement: faultLock.checked
            };
            // Собрать переопределения датчиков
            var overrides = {};
            var soRows = card.querySelectorAll('.sensor-override-row');
            soRows.forEach(function (row) {
                var name = row.querySelector('.so-name').value.trim();
                var val = row.querySelector('.so-value').value;
                if (name && val !== '') {
                    overrides[name] = parseFloat(val);
                }
            });
            if (Object.keys(overrides).length > 0) {
                faultObj.sensorOverrides = overrides;
            }
            steps[index].faultEvent = JSON.stringify(faultObj);
        }

        if (faultType) faultType.addEventListener('change', updateFaultEvent);
        if (faultElement) faultElement.addEventListener('input', updateFaultEvent);
        if (faultMessage) faultMessage.addEventListener('input', updateFaultEvent);
        if (faultLock) faultLock.addEventListener('change', updateFaultEvent);

        // Sensor overrides: add button
        var addSoBtn = card.querySelector('.btn-add-sensor-override');
        if (addSoBtn) {
            addSoBtn.addEventListener('click', function () {
                var list = card.querySelector('.sensor-overrides-list');
                var newRow = document.createElement('div');
                newRow.innerHTML = buildSensorOverrideRowHtml('', '');
                var row = newRow.firstChild;
                list.appendChild(row);
                bindSensorOverrideRowListeners(card, index, row);
                updateFaultEvent();
            });
        }

        // Existing sensor override rows
        var soRows = card.querySelectorAll('.sensor-override-row');
        soRows.forEach(function (row) {
            bindSensorOverrideRowListeners(card, index, row);
        });

        // Forbidden actions: add button
        var addBtn = card.querySelector('.btn-add-forbidden');
        if (addBtn) {
            addBtn.addEventListener('click', function () {
                var list = card.querySelector('.forbidden-actions-list');
                var newRow = document.createElement('div');
                newRow.innerHTML = buildForbiddenActionRowHtml(index, list.children.length, null);
                var row = newRow.firstChild;
                list.appendChild(row);
                bindForbiddenRowListeners(card, index, row);
                updateForbiddenActions(card, index);
            });
        }

        // Existing forbidden action rows
        var rows = card.querySelectorAll('.forbidden-action-row');
        rows.forEach(function (row) {
            bindForbiddenRowListeners(card, index, row);
        });
    }

    function bindSensorOverrideRowListeners(card, stepIndex, row) {
        var removeBtn = row.querySelector('.btn-remove-sensor-override');
        if (removeBtn) {
            removeBtn.addEventListener('click', function () {
                row.remove();
                // Перезапустить updateFaultEvent через симуляцию change
                var faultType = card.querySelector('.fault-type');
                if (faultType) faultType.dispatchEvent(new Event('change'));
            });
        }
        var inputs = row.querySelectorAll('input');
        inputs.forEach(function (inp) {
            inp.addEventListener('input', function () {
                var faultType = card.querySelector('.fault-type');
                if (faultType) faultType.dispatchEvent(new Event('change'));
            });
        });
    }

    function bindForbiddenRowListeners(card, stepIndex, row) {
        var removeBtn = row.querySelector('.btn-remove-forbidden');
        if (removeBtn) {
            removeBtn.addEventListener('click', function () {
                row.remove();
                updateForbiddenActions(card, stepIndex);
            });
        }

        var inputs = row.querySelectorAll('input, select');
        inputs.forEach(function (inp) {
            inp.addEventListener('change', function () { updateForbiddenActions(card, stepIndex); });
            inp.addEventListener('input', function () { updateForbiddenActions(card, stepIndex); });
        });
    }

    function updateForbiddenActions(card, stepIndex) {
        var rows = card.querySelectorAll('.forbidden-action-row');
        var arr = [];
        rows.forEach(function (row) {
            var el = row.querySelector('.fa-element').value.trim();
            var action = row.querySelector('.fa-action').value;
            var penalty = row.querySelector('.fa-penalty').value;
            var message = row.querySelector('.fa-message').value.trim();
            if (el) {
                arr.push({ elementName: el, action: action, penalty: penalty, message: message });
            }
        });
        steps[stepIndex].forbiddenActions = arr.length > 0 ? JSON.stringify(arr) : '[]';
    }

    // ========== Public API ==========

    return {
        init: init
    };

})();

document.addEventListener('DOMContentLoaded', function () {
    if (typeof scenarioId !== 'undefined') {
        ScenarioEditor.init();
    }
});
