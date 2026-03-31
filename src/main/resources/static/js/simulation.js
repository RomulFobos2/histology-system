/**
 * Simulation — клиентский движок симуляции мнемосхемы.
 * Таймер, переключение элементов, проверка шагов, tooltip,
 * окрашивание труб, значения датчиков, направление потока.
 */
var Simulation = (function () {

    var svgEl;
    var elementState = {};   // {name: true/false}
    var elementGroups = {};  // {name: {group, use, el, valueText?}}
    var connectionLines = []; // [{line, src, tgt}]
    var timerInterval = null;
    var tooltipEl = null;
    var lockedElements = [];       // ["VP5", "NR2"]
    var stepTimerInterval = null;

    /** Русские названия типов элементов */
    var TYPE_LABELS = {
        'VALVE': 'Пневмоклапан',
        'PUMP': 'Насос',
        'SWITCH': 'Переключатель',
        'SENSOR_PRESSURE': 'Датчик давления',
        'SENSOR_TEMPERATURE': 'Датчик температуры',
        'HEATER': 'Нагреватель',
        'LOCK': 'Блокиратор',
        'LABEL': 'Надпись',
        'REDUCER': 'Редуктор',
        'SAFETY_VALVE': 'Предохр. клапан',
        'FILTER': 'Фильтр',
        'CHECK_VALVE': 'Обратный клапан'
    };

    /** Диапазоны значений датчиков при ВКЛ */
    var SENSOR_RANGES = {
        'SENSOR_PRESSURE': { min: 0.5, max: 6.0, unit: 'МПа' },
        'SENSOR_TEMPERATURE': { min: 15, max: 85, unit: '°C' }
    };

    function init() {
        svgEl = document.getElementById('simSvg');
        if (!svgEl || typeof simSchemaData === 'undefined') return;

        // Парсить текущее состояние
        if (typeof simCurrentState === 'string') {
            try { elementState = JSON.parse(simCurrentState); } catch (e) { elementState = {}; }
        } else if (typeof simCurrentState === 'object' && simCurrentState !== null) {
            elementState = simCurrentState;
        }

        // Парсить заблокированные элементы
        if (typeof simLockedElements === 'string') {
            try { lockedElements = JSON.parse(simLockedElements); } catch (e) { lockedElements = []; }
        } else if (Array.isArray(simLockedElements)) {
            lockedElements = simLockedElements;
        }

        createTooltip();
        renderSchema();
        renderStepProgress();
        startTimer();

        // Показать аварийное событие текущего шага (если есть)
        if (typeof simFaultEvent === 'string' && simFaultEvent) {
            try {
                var initFault = JSON.parse(simFaultEvent);
                applySensorOverrides(initFault);
                showFaultEventModal(initFault);
            } catch (e) { /* ignore */ }
        } else if (typeof simFaultEvent === 'object' && simFaultEvent !== null) {
            applySensorOverrides(simFaultEvent);
            showFaultEventModal(simFaultEvent);
        }

        // Запустить таймер шага (если есть)
        if (typeof simStepTimeLimit === 'number' && simStepTimeLimit > 0 && simStepStartedAt) {
            startStepTimer(simStepTimeLimit, simStepStartedAt);
        }

        document.getElementById('btnCheckStep').addEventListener('click', checkStep);
    }

    // ========== Tooltip ==========

    function createTooltip() {
        tooltipEl = document.createElement('div');
        tooltipEl.className = 'sim-tooltip';
        tooltipEl.style.display = 'none';
        document.body.appendChild(tooltipEl);
    }

    function showTooltip(el, evt) {
        var isOn = elementState.hasOwnProperty(el.name) ? elementState[el.name] : el.initialState;
        var typeName = TYPE_LABELS[el.elementType] || el.elementType;

        if (el.elementType === 'LABEL') {
            tooltipEl.innerHTML =
                '<div class="sim-tooltip-name">' + (el.name || '—') + '</div>' +
                '<div class="sim-tooltip-type">' + typeName + '</div>';
        } else if (isSensorType(el.elementType)) {
            // Датчик — пассивный элемент, показания зависят от потока
            var range = SENSOR_RANGES[el.elementType];
            var valText = '—';
            var valColor = '';
            if (sensorOverrides[el.name] !== undefined && range) {
                valText = sensorOverrides[el.name].toFixed(2) + ' ' + range.unit;
                valColor = ' style="color:#dc3545"';
            } else {
                var hasFlow = checkSensorFlow(el.name);
                var val = hasFlow && range ? getSensorValue(el.name, range) : 0;
                valText = range ? val.toFixed(2) + ' ' + range.unit : '—';
            }

            tooltipEl.innerHTML =
                '<div class="sim-tooltip-name">' + (el.name || '—') + '</div>' +
                '<div class="sim-tooltip-type">' + typeName + '</div>' +
                '<div class="sim-tooltip-value"' + valColor + '>' + valText + '</div>' +
                '<div class="sim-tooltip-hint">Пассивный элемент (показания зависят от потока)</div>';
        } else {
            var stateText = isOn ? 'ВКЛ' : 'ВЫКЛ';
            var stateClass = isOn ? 'sim-tooltip-on' : 'sim-tooltip-off';

            tooltipEl.innerHTML =
                '<div class="sim-tooltip-name">' + (el.name || '—') + '</div>' +
                '<div class="sim-tooltip-type">' + typeName + '</div>' +
                '<div class="sim-tooltip-state ' + stateClass + '">' + stateText + '</div>' +
                '<div class="sim-tooltip-hint">Нажмите для переключения</div>';
        }

        tooltipEl.style.display = 'block';
        positionTooltip(evt);
    }

    function positionTooltip(evt) {
        if (!tooltipEl || tooltipEl.style.display === 'none') return;
        var x = evt.clientX + 14;
        var y = evt.clientY + 14;
        var rect = tooltipEl.getBoundingClientRect();
        if (x + rect.width > window.innerWidth) x = evt.clientX - rect.width - 10;
        if (y + rect.height > window.innerHeight) y = evt.clientY - rect.height - 10;
        tooltipEl.style.left = x + 'px';
        tooltipEl.style.top = y + 'px';
    }

    function hideTooltip() {
        if (tooltipEl) tooltipEl.style.display = 'none';
    }

    // ========== Значения датчиков (pseudo-random, стабильные для одного имени) ==========

    var sensorCache = {};
    var sensorOverrides = {};

    function applySensorOverrides(fault) {
        if (fault && fault.sensorOverrides) {
            var ov = fault.sensorOverrides;
            for (var key in ov) {
                if (ov.hasOwnProperty(key)) {
                    sensorOverrides[key] = Number(ov[key]);
                }
            }
            updateSensorValues();
        }
    }

    function getSensorValue(name, range) {
        if (sensorCache[name] !== undefined) return sensorCache[name];
        // Генерируем стабильное псевдослучайное значение по имени
        var hash = 0;
        for (var i = 0; i < name.length; i++) {
            hash = ((hash << 5) - hash) + name.charCodeAt(i);
            hash |= 0;
        }
        var norm = (Math.abs(hash) % 10000) / 10000;
        var val = range.min + norm * (range.max - range.min);
        sensorCache[name] = val;
        return val;
    }

    /** Укоротить линию: отступить от начала на padStart и от конца на padEnd */
    function shortenLine(x1, y1, x2, y2, padStart, padEnd) {
        var dx = x2 - x1;
        var dy = y2 - y1;
        var len = Math.sqrt(dx * dx + dy * dy);
        if (len < padStart + padEnd) return { x1: x1, y1: y1, x2: x2, y2: y2 };
        var ux = dx / len;
        var uy = dy / len;
        return {
            x1: x1 + ux * padStart,
            y1: y1 + uy * padStart,
            x2: x2 - ux * padEnd,
            y2: y2 - uy * padEnd
        };
    }

    function isSensorType(type) {
        return type === 'SENSOR_PRESSURE' || type === 'SENSOR_TEMPERATURE';
    }

    function isNonToggleableType(type) {
        return type === 'SENSOR_PRESSURE' || type === 'SENSOR_TEMPERATURE'
            || type === 'SAFETY_VALVE' || type === 'FILTER' || type === 'CHECK_VALVE';
    }

    /** Проверяет, доходит ли поток до датчика (есть ли активная входящая труба) */
    function checkSensorFlow(sensorName) {
        return connectionLines.some(function (conn) {
            return conn.tgt.name === sensorName && (isNeutralElement(conn.src) || isElementOn(conn.src));
        });
    }

    /** Обновляет значения всех датчиков на основе потока */
    function updateSensorValues() {
        Object.keys(elementGroups).forEach(function (name) {
            var eg = elementGroups[name];
            if (!eg.valueText) return;
            var el = eg.el;
            var range = SENSOR_RANGES[el.elementType];
            if (!range) return;

            // Аварийное переопределение значения датчика
            if (sensorOverrides[name] !== undefined) {
                eg.valueText.textContent = sensorOverrides[name].toFixed(2) + ' ' + range.unit;
                eg.valueText.setAttribute('fill', '#dc3545');
                return;
            }

            var hasFlow = checkSensorFlow(name);
            if (hasFlow) {
                var effectiveRange = range;
                if (el.minValue != null && el.maxValue != null) {
                    effectiveRange = { min: el.minValue, max: el.maxValue, unit: range.unit };
                }
                var val = getSensorValue(el.name, effectiveRange);
                eg.valueText.textContent = val.toFixed(2) + ' ' + range.unit;
                eg.valueText.setAttribute('fill', '#0d6efd');
            } else {
                eg.valueText.textContent = '0,00';
                eg.valueText.setAttribute('fill', '#adb5bd');
            }
        });
    }

    // ========== Рендер схемы ==========

    function renderSchema() {
        var data = simSchemaData;
        var w = data.width || 1200;
        var h = data.height || 800;

        svgEl.setAttribute('width', w);
        svgEl.setAttribute('height', h);
        svgEl.setAttribute('viewBox', '0 0 ' + w + ' ' + h);

        var defsHtml = '';
        if (typeof SchemaEditor !== 'undefined' && SchemaEditor.getSymbolDefs) {
            defsHtml = SchemaEditor.getSymbolDefs();
        }

        // Добавляем маркер стрелки для направления потока
        var arrowDefs =
            '<defs>' +
            '<marker id="arrow-active" viewBox="0 0 10 6" refX="10" refY="3" markerWidth="8" markerHeight="6" orient="auto">' +
            '<path d="M0,0 L10,3 L0,6 Z" fill="#27ae60"/>' +
            '</marker>' +
            '<marker id="arrow-inactive" viewBox="0 0 10 6" refX="10" refY="3" markerWidth="8" markerHeight="6" orient="auto">' +
            '<path d="M0,0 L10,3 L0,6 Z" fill="#adb5bd"/>' +
            '</marker>' +
            '</defs>';

        svgEl.innerHTML = defsHtml + arrowDefs +
            '<rect width="' + w + '" height="' + h + '" fill="#f8f9fa"/>';

        connectionLines = [];
        var elementsById = {};
        (data.elements || []).forEach(function (el) { elementsById[el.id] = el; });

        // Соединения (трубы) с направлением и окрашиванием
        (data.connections || []).forEach(function (conn) {
            var src = elementsById[conn.sourceElementId];
            var tgt = elementsById[conn.targetElementId];
            if (!src || !tgt) return;

            // Центры элементов
            var cx1 = src.posX + (src.width || 60) / 2;
            var cy1 = src.posY + (src.height || 60) / 2;
            var cx2 = tgt.posX + (tgt.width || 60) / 2;
            var cy2 = tgt.posY + (tgt.height || 60) / 2;

            // Укоротить линию — от края source до края target (не от центра)
            var pts = shortenLine(cx1, cy1, cx2, cy2,
                (src.width || 60) / 2 + 4, (tgt.width || 60) / 2 + 12);

            var line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            line.setAttribute('x1', pts.x1);
            line.setAttribute('y1', pts.y1);
            line.setAttribute('x2', pts.x2);
            line.setAttribute('y2', pts.y2);
            line.setAttribute('class', 'connection-line');
            line.style.stroke = '#adb5bd';
            line.style.strokeWidth = '3';
            svgEl.appendChild(line);

            connectionLines.push({ line: line, src: src, tgt: tgt });
        });

        // Элементы
        (data.elements || []).forEach(function (el) {
            var isOn = elementState.hasOwnProperty(el.name) ? elementState[el.name] : el.initialState;
            var state = isOn ? 'on' : 'off';
            var isLabel = el.elementType === 'LABEL';
            var isSensor = el.elementType === 'SENSOR_PRESSURE' || el.elementType === 'SENSOR_TEMPERATURE';

            var group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
            group.setAttribute('class', 'element-group' + (isLabel ? ' label-group' : ''));
            group.setAttribute('transform', 'translate(' + el.posX + ',' + el.posY + ')' +
                (el.rotation ? ' rotate(' + el.rotation + ',' + ((el.width || 60) / 2) + ',' + ((el.height || 60) / 2) + ')' : ''));

            if (isLabel) {
                // Надпись — отображаем имя как текст, без иконки
                var labelText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
                labelText.setAttribute('x', (el.width || 80) / 2);
                labelText.setAttribute('y', (el.height || 30) / 2 + 5);
                labelText.setAttribute('text-anchor', 'middle');
                labelText.setAttribute('font-size', '13');
                labelText.setAttribute('font-weight', '600');
                labelText.setAttribute('fill', '#495057');
                labelText.textContent = el.name || 'Текст';
                group.appendChild(labelText);
            } else {
                var border = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
                border.setAttribute('class', 'element-border');
                border.setAttribute('width', el.width || 60);
                border.setAttribute('height', el.height || 60);
                border.setAttribute('fill', 'transparent');
                border.setAttribute('stroke', '#dee2e6');
                border.setAttribute('stroke-width', '1');
                border.setAttribute('rx', '4');
                group.appendChild(border);

                var use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
                use.setAttribute('href', '#symbol-' + el.elementType + '-' + state);
                use.setAttribute('width', el.width || 60);
                use.setAttribute('height', el.height || 60);
                group.appendChild(use);

                // Имя элемента под иконкой
                var label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
                label.setAttribute('x', (el.width || 60) / 2);
                label.setAttribute('y', (el.height || 60) + 14);
                label.setAttribute('text-anchor', 'middle');
                label.setAttribute('font-size', '11');
                label.setAttribute('fill', '#333');
                label.textContent = el.name || '';
                group.appendChild(label);

                // Значение датчика под именем (заполняется позже через updateSensorValues)
                var valueText = null;
                if (isSensor) {
                    valueText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
                    valueText.setAttribute('x', (el.width || 60) / 2);
                    valueText.setAttribute('y', (el.height || 60) + 26);
                    valueText.setAttribute('text-anchor', 'middle');
                    valueText.setAttribute('font-size', '10');
                    valueText.setAttribute('font-weight', '700');
                    valueText.textContent = '0,00';
                    valueText.setAttribute('fill', '#adb5bd');
                    group.appendChild(valueText);
                }

                // Tooltip при наведении
                group.addEventListener('mouseenter', function (evt) { showTooltip(el, evt); });
                group.addEventListener('mousemove', function (evt) { positionTooltip(evt); });
                group.addEventListener('mouseleave', function () { hideTooltip(); });

                // Клик — переключение (не для LABEL, датчиков и пассивных элементов)
                group.addEventListener('click', function () {
                    hideTooltip();
                    if (isNonToggleableType(el.elementType)) {
                        if (isSensorType(el.elementType)) {
                            showFeedback(el.name + ': показания зависят от потока', 'info');
                        } else {
                            showFeedback(el.name + ': элемент не переключается вручную', 'info');
                        }
                        return;
                    }
                    toggleElement(el.name, group, use, el, valueText);
                });

                elementGroups[el.name] = { group: group, use: use, el: el, valueText: valueText, border: border };
            }

            svgEl.appendChild(group);
        });

        // Отметить заблокированные элементы
        updateLockedVisuals();

        // Обновить цвета труб и значения датчиков
        updatePipeColors();
        updateSensorValues();
    }

    // ========== Окрашивание труб ==========

    function updatePipeColors() {
        connectionLines.forEach(function (conn) {
            var srcOn = isElementOn(conn.src);
            var tgtOn = isElementOn(conn.tgt);

            // Труба активна если ОБА конца включены (или это LABEL/датчик — считаем нейтральными)
            var srcActive = isNeutralElement(conn.src) || srcOn;
            var tgtActive = isNeutralElement(conn.tgt) || tgtOn;
            var active = srcActive && tgtActive;

            if (active) {
                conn.line.style.stroke = '#27ae60';
                conn.line.style.strokeWidth = '4';
                conn.line.setAttribute('marker-end', 'url(#arrow-active)');
            } else {
                conn.line.style.stroke = '#adb5bd';
                conn.line.style.strokeWidth = '3';
                conn.line.setAttribute('marker-end', 'url(#arrow-inactive)');
            }
        });
    }

    function isElementOn(el) {
        if (!el) return false;
        return elementState.hasOwnProperty(el.name) ? elementState[el.name] : el.initialState;
    }

    function isNeutralElement(el) {
        return el.elementType === 'LABEL' || el.elementType === 'SENSOR_PRESSURE' || el.elementType === 'SENSOR_TEMPERATURE'
            || el.elementType === 'SAFETY_VALVE' || el.elementType === 'FILTER' || el.elementType === 'CHECK_VALVE';
    }

    // ========== Переключение элемента ==========

    function toggleElement(name, group, use, el, valueText) {
        showFeedback('', '');

        fetch('/employee/specialist/mnemo/toggleElement/' + simSessionId, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ elementName: name })
        })
        .then(function (r) { return r.json(); })
        .then(function (data) {
            if (data.expired) {
                window.location.href = '/employee/specialist/mnemo/result/' + simSessionId;
                return;
            }
            if (data.notToggleable) {
                showFeedback(name + ': этот элемент нельзя переключить', 'info');
                return;
            }
            if (data.locked) {
                showFeedback(name + ': ' + (data.message || 'элемент неисправен'), 'error');
                return;
            }
            if (data.forbidden && data.failed) {
                showFaultFailModal(data.message || 'Запрещённое действие привело к аварии!');
                return;
            }

            elementState[name] = data.newState;
            var state = data.newState ? 'on' : 'off';
            use.setAttribute('href', '#symbol-' + el.elementType + '-' + state);

            // Обновить цвета труб и значения датчиков
            updatePipeColors();
            updateSensorValues();

            // Предупреждение (WARNING/TIME_PENALTY) — действие выполнено, но со штрафом
            if (data.warning) {
                showFeedback('\u26A0 ' + data.warning, 'warning');
            } else {
                showFeedback(el.name + ': ' + (data.newState ? 'ВКЛ' : 'ВЫКЛ'), 'info');
            }

            // Visual feedback
            group.classList.add('toggled');
            setTimeout(function () { group.classList.remove('toggled'); }, 300);
        })
        .catch(function () {
            showFeedback('Ошибка связи с сервером', 'error');
        });
    }

    // ========== Проверка шага ==========

    function checkStep() {
        var btn = document.getElementById('btnCheckStep');
        btn.disabled = true;
        btn.innerHTML = '<i class="bi bi-hourglass-split"></i> Проверка...';

        fetch('/employee/specialist/mnemo/checkStep/' + simSessionId, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        })
        .then(function (r) { return r.json(); })
        .then(function (data) {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-check-circle"></i> Проверить шаг';

            if (data.status === 'completed') {
                showFeedback('Симуляция успешно завершена!', 'success');
                stopStepTimer();
                setTimeout(function () {
                    window.location.href = '/employee/specialist/mnemo/result/' + simSessionId;
                }, 1500);
            } else if (data.status === 'advance') {
                simCurrentStep = data.nextStep;
                simCompletedSteps = data.nextStep - 1;
                document.getElementById('currentStepNum').textContent = data.nextStep;
                document.getElementById('instructionText').textContent = data.nextInstruction || '';
                renderStepProgress();
                showFeedback('Шаг пройден! Переход к шагу ' + data.nextStep, 'success');

                // Обновить заблокированные элементы
                if (data.lockedElements) {
                    try {
                        lockedElements = typeof data.lockedElements === 'string'
                            ? JSON.parse(data.lockedElements) : data.lockedElements;
                    } catch (e) { /* ignore */ }
                    updateLockedVisuals();
                }

                // Показать аварийное событие нового шага + переопределения датчиков
                if (data.faultEvent) {
                    try {
                        var fault = typeof data.faultEvent === 'string'
                            ? JSON.parse(data.faultEvent) : data.faultEvent;
                        applySensorOverrides(fault);
                        showFaultEventModal(fault);
                    } catch (e) { /* ignore */ }
                }

                // Запустить/остановить таймер шага
                if (data.stepTimeLimit && data.stepStartedAt) {
                    startStepTimer(data.stepTimeLimit, data.stepStartedAt);
                } else {
                    stopStepTimer();
                }
            } else if (data.status === 'wrong') {
                highlightError(data.failedElement);
                showFeedback('Ошибка: элемент «' + data.failedElement + '» — ожидалось ' +
                    (data.expected ? 'ВКЛ' : 'ВЫКЛ') + ', текущее ' +
                    (data.actual ? 'ВКЛ' : 'ВЫКЛ'), 'error');
            } else if (data.status === 'expired') {
                window.location.href = '/employee/specialist/mnemo/result/' + simSessionId;
            } else if (data.status === 'step_expired') {
                showFeedback(data.message || 'Время на шаг истекло!', 'error');
                stopStepTimer();
                setTimeout(function () {
                    window.location.href = '/employee/specialist/mnemo/result/' + simSessionId;
                }, 2000);
            }
        })
        .catch(function () {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-check-circle"></i> Проверить шаг';
            showFeedback('Ошибка связи с сервером', 'error');
        });
    }

    // ========== Подсветка ошибочного элемента ==========

    function highlightError(elementName) {
        var info = elementGroups[elementName];
        if (!info) return;
        var border = info.group.querySelector('.element-border');
        if (!border) return;
        border.setAttribute('stroke', '#dc3545');
        border.setAttribute('stroke-width', '3');
        border.setAttribute('stroke-dasharray', '5,3');
        setTimeout(function () {
            border.setAttribute('stroke', '#dee2e6');
            border.setAttribute('stroke-width', '1');
            border.removeAttribute('stroke-dasharray');
        }, 3000);
    }

    // ========== Прогресс шагов ==========

    function renderStepProgress() {
        var container = document.getElementById('stepProgress');
        container.innerHTML = '';
        for (var i = 1; i <= simTotalSteps; i++) {
            var badge = document.createElement('span');
            badge.className = 'sim-step-badge';
            badge.textContent = i;
            if (i < simCurrentStep) {
                badge.classList.add('done');
            } else if (i === simCurrentStep) {
                badge.classList.add('current');
            } else {
                badge.classList.add('pending');
            }
            container.appendChild(badge);
        }
    }

    // ========== Таймер ==========

    function startTimer() {
        var timerEl = document.getElementById('simTimer');
        if (!timerEl) return;

        var endTimeStr = timerEl.getAttribute('data-end-time');
        if (!endTimeStr) return;

        var endTime = new Date(endTimeStr).getTime();

        function update() {
            var now = Date.now();
            var diff = endTime - now;

            if (diff <= 0) {
                timerEl.textContent = '00:00';
                timerEl.classList.add('warning');
                clearInterval(timerInterval);
                window.location.href = '/employee/specialist/mnemo/result/' + simSessionId;
                return;
            }

            var minutes = Math.floor(diff / 60000);
            var seconds = Math.floor((diff % 60000) / 1000);
            timerEl.textContent = pad(minutes) + ':' + pad(seconds);

            if (diff < 60000) {
                timerEl.classList.add('warning');
            }
        }

        update();
        timerInterval = setInterval(update, 1000);
    }

    function pad(n) { return n < 10 ? '0' + n : '' + n; }

    // ========== Feedback ==========

    function showFeedback(msg, type) {
        var el = document.getElementById('simFeedback');
        if (!msg) {
            el.classList.remove('show', 'success', 'error', 'info', 'warning');
            return;
        }
        el.textContent = msg;
        el.className = 'sim-feedback show ' + type;
        if (type === 'success' || type === 'info' || type === 'warning') {
            setTimeout(function () { el.classList.remove('show'); }, 4000);
        }
    }

    // ========== Fault: заблокированные элементы ==========

    function updateLockedVisuals() {
        Object.keys(elementGroups).forEach(function (name) {
            var eg = elementGroups[name];
            var isLocked = lockedElements.indexOf(name) !== -1;
            var group = eg.group;
            var border = eg.border;

            // Удалить старые locked-иконки
            var oldIcons = group.querySelectorAll('.locked-icon');
            oldIcons.forEach(function (ic) { ic.remove(); });
            group.classList.remove('element-locked');

            if (isLocked && border) {
                group.classList.add('element-locked');
                var warnIcon = document.createElementNS('http://www.w3.org/2000/svg', 'text');
                warnIcon.setAttribute('x', String((eg.el.width || 60) - 6));
                warnIcon.setAttribute('y', '14');
                warnIcon.setAttribute('class', 'locked-icon');
                warnIcon.textContent = '\u26A0';
                group.appendChild(warnIcon);
            }
        });
    }

    // ========== Fault: модалка аварийного события ==========

    function showFaultEventModal(fault) {
        if (!fault) return;
        var msg = fault.message || 'Аварийное событие!';
        var elName = fault.elementName || '';
        var typeName = fault.type || '';

        var TYPE_DISPLAY = {
            'ELEMENT_FAILURE': 'Отказ элемента',
            'PRESSURE_ANOMALY': 'Аномалия давления',
            'OVERHEAT': 'Перегрев',
            'FALSE_ALARM': 'Ложное срабатывание'
        };

        var overlay = document.createElement('div');
        overlay.className = 'modal fade';
        overlay.setAttribute('tabindex', '-1');
        overlay.innerHTML =
            '<div class="modal-dialog modal-dialog-centered">' +
            '<div class="modal-content border-danger">' +
            '<div class="modal-header bg-danger text-white">' +
            '<h5 class="modal-title"><i class="bi bi-exclamation-triangle-fill me-2"></i>Аварийное событие</h5>' +
            '</div>' +
            '<div class="modal-body">' +
            (typeName ? '<p class="mb-1"><strong>' + (TYPE_DISPLAY[typeName] || typeName) + '</strong></p>' : '') +
            '<p class="mb-1">' + escapeHtml(msg) + '</p>' +
            (elName ? '<p class="text-muted small mb-0">Элемент: <strong>' + escapeHtml(elName) + '</strong></p>' : '') +
            '</div>' +
            '<div class="modal-footer">' +
            '<button type="button" class="btn btn-danger" data-bs-dismiss="modal">Понятно</button>' +
            '</div></div></div>';

        document.body.appendChild(overlay);
        var modal = new bootstrap.Modal(overlay);
        modal.show();
        overlay.addEventListener('hidden.bs.modal', function () {
            overlay.remove();
        });
    }

    // ========== Fault: модалка провала ==========

    function showFaultFailModal(message) {
        var overlay = document.createElement('div');
        overlay.className = 'modal fade';
        overlay.setAttribute('tabindex', '-1');
        overlay.setAttribute('data-bs-backdrop', 'static');
        overlay.innerHTML =
            '<div class="modal-dialog modal-dialog-centered">' +
            '<div class="modal-content border-danger">' +
            '<div class="modal-header bg-danger text-white">' +
            '<h5 class="modal-title"><i class="bi bi-x-circle-fill me-2"></i>Симуляция провалена</h5>' +
            '</div>' +
            '<div class="modal-body">' +
            '<p>' + escapeHtml(message) + '</p>' +
            '<p class="text-muted small mb-0">Запрещённое действие привело к аварийной ситуации.</p>' +
            '</div>' +
            '<div class="modal-footer">' +
            '<button type="button" class="btn btn-danger" id="btnFaultFailOk">Перейти к результатам</button>' +
            '</div></div></div>';

        document.body.appendChild(overlay);
        var modal = new bootstrap.Modal(overlay);
        modal.show();
        overlay.querySelector('#btnFaultFailOk').addEventListener('click', function () {
            window.location.href = '/employee/specialist/mnemo/result/' + simSessionId;
        });
    }

    // ========== Fault: таймер шага ==========

    function startStepTimer(timeLimitSec, stepStartedAtStr) {
        stopStepTimer();
        var timerEl = document.getElementById('stepTimer');
        var valueEl = document.getElementById('stepTimerValue');
        if (!timerEl || !valueEl) return;
        timerEl.style.display = 'inline';

        var deadline = new Date(stepStartedAtStr).getTime() + timeLimitSec * 1000;

        function updateStepTimer() {
            var diff = deadline - Date.now();
            if (diff <= 0) {
                valueEl.textContent = '00:00';
                timerEl.classList.remove('bg-warning');
                timerEl.classList.add('bg-danger', 'text-white');
                stopStepTimer();
                // Автоматическая проверка шага (сервер вернёт step_expired)
                checkStep();
                return;
            }
            var m = Math.floor(diff / 60000);
            var s = Math.floor((diff % 60000) / 1000);
            valueEl.textContent = pad(m) + ':' + pad(s);
            if (diff < 10000) {
                timerEl.classList.remove('bg-warning');
                timerEl.classList.add('bg-danger', 'text-white');
            }
        }

        stepTimerInterval = setInterval(updateStepTimer, 1000);
        updateStepTimer();
    }

    function stopStepTimer() {
        if (stepTimerInterval) {
            clearInterval(stepTimerInterval);
            stepTimerInterval = null;
        }
        var timerEl = document.getElementById('stepTimer');
        if (timerEl) {
            timerEl.style.display = 'none';
            timerEl.classList.remove('bg-danger', 'text-white');
            timerEl.classList.add('bg-warning', 'text-dark');
        }
    }

    // ========== Утилиты ==========

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    return { init: init };
})();

document.addEventListener('DOMContentLoaded', function () {
    if (typeof simSessionId !== 'undefined') {
        Simulation.init();
    }
});
