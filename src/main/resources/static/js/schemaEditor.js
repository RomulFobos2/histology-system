/**
 * SchemaEditor — визуальный SVG-редактор мнемосхем.
 * Vanilla JS, без зависимостей.
 */
var SchemaEditor = (function () {

    var svg, elementsLayer, connectionsLayer, tempLine, gridRect;
    var schemaId;
    var elements = [];      // {id, name, elementType, posX, posY, width, height, initialState, rotation, svgGroup}
    var connections = [];    // {id, sourceId, targetId, pathData, svgLine}
    var nextTempId = -1;

    var mode = 'select';    // 'select' | 'connect'
    var selectedElement = null;
    var selectedConnection = null;
    var connectSource = null;

    var isDragging = false;
    var dragElement = null;
    var dragOffset = { x: 0, y: 0 };

    var viewBox = { x: 0, y: 0, w: 1200, h: 800 };
    var zoom = 1;
    var canvasWidth = 1200;
    var canvasHeight = 800;

    var isPanning = false;
    var panStart = { x: 0, y: 0 };

    // ========== Инициализация ==========

    function init(id) {
        schemaId = id;
        svg = document.getElementById('editorSvg');
        elementsLayer = document.getElementById('elementsLayer');
        connectionsLayer = document.getElementById('connectionsLayer');
        tempLine = document.getElementById('tempLine');
        gridRect = document.getElementById('gridRect');

        canvasWidth = parseInt(svg.dataset.width) || 1200;
        canvasHeight = parseInt(svg.dataset.height) || 800;
        viewBox = { x: 0, y: 0, w: canvasWidth, h: canvasHeight };
        updateViewBox();

        setupEvents();
        loadData();
        setMode('select');
    }

    function initPreview(id, svgEl) {
        schemaId = id;
        svg = svgEl;
        fetch('/employee/chief/schemas/loadSchemaData/' + id)
            .then(function (r) { return r.json(); })
            .then(function (data) {
                renderPreview(svgEl, data);
            });
    }

    function renderPreview(svgEl, data) {
        var w = data.width || 1200;
        var h = data.height || 800;
        svgEl.setAttribute('viewBox', '0 0 ' + w + ' ' + h);

        // Copy defs from main editor symbols if available, or create simple shapes
        var defsHtml = getSymbolDefs();
        svgEl.innerHTML = defsHtml;

        var g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        svgEl.appendChild(g);

        var elemMap = {};
        (data.elements || []).forEach(function (el) {
            elemMap[el.id] = el;
            var state = el.initialState ? 'on' : 'off';
            var group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
            group.setAttribute('transform', 'translate(' + el.posX + ',' + el.posY + ')' +
                (el.rotation ? ' rotate(' + el.rotation + ',' + (el.width / 2) + ',' + (el.height / 2) + ')' : ''));

            var use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
            use.setAttribute('href', '#symbol-' + el.elementType + '-' + state);
            use.setAttribute('width', el.width || 60);
            use.setAttribute('height', el.height || 60);
            group.appendChild(use);

            var text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            text.setAttribute('x', (el.width || 60) / 2);
            text.setAttribute('y', (el.height || 60) + 14);
            text.setAttribute('text-anchor', 'middle');
            text.setAttribute('font-size', '11');
            text.setAttribute('fill', '#495057');
            text.textContent = el.name || '';
            group.appendChild(text);

            g.appendChild(group);
        });

        (data.connections || []).forEach(function (conn) {
            var src = elemMap[conn.sourceElementId];
            var tgt = elemMap[conn.targetElementId];
            if (!src || !tgt) return;
            var line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            line.setAttribute('x1', src.posX + (src.width || 60) / 2);
            line.setAttribute('y1', src.posY + (src.height || 60) / 2);
            line.setAttribute('x2', tgt.posX + (tgt.width || 60) / 2);
            line.setAttribute('y2', tgt.posY + (tgt.height || 60) / 2);
            line.setAttribute('stroke', '#6c757d');
            line.setAttribute('stroke-width', '3');
            line.setAttribute('marker-end', 'url(#editor-arrow)');
            g.insertBefore(line, g.firstChild);
        });
    }

    function getSymbolDefs() {
        var arrowMarker =
            '<marker id="editor-arrow" viewBox="0 0 10 6" refX="10" refY="3" markerWidth="8" markerHeight="6" orient="auto">' +
            '<path d="M0,0 L10,3 L0,6 Z" fill="#6c757d"/>' +
            '</marker>';

        var mainDefs = document.querySelector('#editorSvg defs');
        if (mainDefs) return '<defs>' + mainDefs.innerHTML + arrowMarker + '</defs>';

        // Fallback: встроенные символы для страниц без редактора (симуляция, просмотр, сценарий)
        return '<defs>' + arrowMarker +
            // VALVE off (red)
            '<symbol id="symbol-VALVE-off" viewBox="0 0 60 60">' +
            '<circle cx="30" cy="30" r="25" fill="#fde8e8" stroke="#e74c3c" stroke-width="2.5"/>' +
            '<path d="M15 30 L45 30 M30 15 L30 45" stroke="#e74c3c" stroke-width="3"/>' +
            '</symbol>' +
            // VALVE on (green)
            '<symbol id="symbol-VALVE-on" viewBox="0 0 60 60">' +
            '<circle cx="30" cy="30" r="25" fill="#d4edda" stroke="#27ae60" stroke-width="2.5"/>' +
            '<path d="M15 30 L45 30" stroke="#27ae60" stroke-width="3"/>' +
            '<circle cx="30" cy="30" r="5" fill="#27ae60"/>' +
            '</symbol>' +
            // PUMP off
            '<symbol id="symbol-PUMP-off" viewBox="0 0 60 60">' +
            '<circle cx="30" cy="30" r="25" fill="#eef2f7" stroke="#95a5a6" stroke-width="2.5"/>' +
            '<path d="M18 30 L30 18 L42 30 L30 42 Z" fill="none" stroke="#95a5a6" stroke-width="2.5"/>' +
            '</symbol>' +
            // PUMP on
            '<symbol id="symbol-PUMP-on" viewBox="0 0 60 60">' +
            '<circle cx="30" cy="30" r="25" fill="#d4edda" stroke="#27ae60" stroke-width="2.5"/>' +
            '<path d="M18 30 L30 18 L42 30 L30 42 Z" fill="#27ae60" fill-opacity="0.3" stroke="#27ae60" stroke-width="2.5"/>' +
            '<circle cx="30" cy="30" r="4" fill="#27ae60"/>' +
            '</symbol>' +
            // SWITCH off
            '<symbol id="symbol-SWITCH-off" viewBox="0 0 60 60">' +
            '<rect x="5" y="15" width="50" height="30" rx="6" fill="#eef2f7" stroke="#95a5a6" stroke-width="2.5"/>' +
            '<circle cx="22" cy="30" r="9" fill="#95a5a6"/>' +
            '</symbol>' +
            // SWITCH on
            '<symbol id="symbol-SWITCH-on" viewBox="0 0 60 60">' +
            '<rect x="5" y="15" width="50" height="30" rx="6" fill="#d4edda" stroke="#27ae60" stroke-width="2.5"/>' +
            '<circle cx="38" cy="30" r="9" fill="#27ae60"/>' +
            '</symbol>' +
            // SENSOR_PRESSURE off
            '<symbol id="symbol-SENSOR_PRESSURE-off" viewBox="0 0 60 60">' +
            '<circle cx="30" cy="30" r="25" fill="#f3e8ff" stroke="#9b59b6" stroke-width="2.5"/>' +
            '<text x="30" y="36" text-anchor="middle" font-size="22" font-weight="bold" fill="#9b59b6">P</text>' +
            '</symbol>' +
            // SENSOR_PRESSURE on
            '<symbol id="symbol-SENSOR_PRESSURE-on" viewBox="0 0 60 60">' +
            '<circle cx="30" cy="30" r="25" fill="#e8d5f5" stroke="#8e44ad" stroke-width="2.5"/>' +
            '<text x="30" y="36" text-anchor="middle" font-size="22" font-weight="bold" fill="#8e44ad">P</text>' +
            '</symbol>' +
            // SENSOR_TEMPERATURE off
            '<symbol id="symbol-SENSOR_TEMPERATURE-off" viewBox="0 0 60 60">' +
            '<circle cx="30" cy="30" r="25" fill="#fef3e2" stroke="#e67e22" stroke-width="2.5"/>' +
            '<text x="30" y="36" text-anchor="middle" font-size="22" font-weight="bold" fill="#e67e22">T</text>' +
            '</symbol>' +
            // SENSOR_TEMPERATURE on
            '<symbol id="symbol-SENSOR_TEMPERATURE-on" viewBox="0 0 60 60">' +
            '<circle cx="30" cy="30" r="25" fill="#fde3c8" stroke="#d35400" stroke-width="2.5"/>' +
            '<text x="30" y="36" text-anchor="middle" font-size="22" font-weight="bold" fill="#d35400">T</text>' +
            '</symbol>' +
            // HEATER off
            '<symbol id="symbol-HEATER-off" viewBox="0 0 60 60">' +
            '<rect x="5" y="8" width="50" height="44" rx="6" fill="#eef2f7" stroke="#95a5a6" stroke-width="2.5"/>' +
            '<path d="M14 22 Q22 17 30 22 Q38 27 46 22" stroke="#95a5a6" stroke-width="2.5" fill="none"/>' +
            '<path d="M14 35 Q22 30 30 35 Q38 40 46 35" stroke="#95a5a6" stroke-width="2.5" fill="none"/>' +
            '</symbol>' +
            // HEATER on
            '<symbol id="symbol-HEATER-on" viewBox="0 0 60 60">' +
            '<rect x="5" y="8" width="50" height="44" rx="6" fill="#fde8e8" stroke="#e74c3c" stroke-width="2.5"/>' +
            '<path d="M14 22 Q22 17 30 22 Q38 27 46 22" stroke="#e74c3c" stroke-width="2.5" fill="none"/>' +
            '<path d="M14 35 Q22 30 30 35 Q38 40 46 35" stroke="#e74c3c" stroke-width="2.5" fill="none"/>' +
            '</symbol>' +
            // LOCK off (locked)
            '<symbol id="symbol-LOCK-off" viewBox="0 0 60 60">' +
            '<rect x="13" y="28" width="34" height="26" rx="5" fill="#fde8e8" stroke="#e74c3c" stroke-width="2.5"/>' +
            '<path d="M20 28 V20 Q20 8 30 8 Q40 8 40 20 V28" fill="none" stroke="#e74c3c" stroke-width="3"/>' +
            '<circle cx="30" cy="40" r="3" fill="#e74c3c"/>' +
            '</symbol>' +
            // LOCK on (unlocked)
            '<symbol id="symbol-LOCK-on" viewBox="0 0 60 60">' +
            '<rect x="13" y="28" width="34" height="26" rx="5" fill="#d4edda" stroke="#27ae60" stroke-width="2.5"/>' +
            '<path d="M20 28 V20 Q20 8 30 8 Q40 8 40 20" fill="none" stroke="#27ae60" stroke-width="3"/>' +
            '<circle cx="30" cy="40" r="3" fill="#27ae60"/>' +
            '</symbol>' +
            // LABEL (надпись — одинаковая для on/off)
            '<symbol id="symbol-LABEL-off" viewBox="0 0 60 60">' +
            '<rect x="2" y="10" width="56" height="40" rx="4" fill="#fff" stroke="#adb5bd" stroke-width="1.5" stroke-dasharray="4,2"/>' +
            '<text x="30" y="36" text-anchor="middle" font-size="14" fill="#495057">Aa</text>' +
            '</symbol>' +
            '<symbol id="symbol-LABEL-on" viewBox="0 0 60 60">' +
            '<rect x="2" y="10" width="56" height="40" rx="4" fill="#fff" stroke="#adb5bd" stroke-width="1.5" stroke-dasharray="4,2"/>' +
            '<text x="30" y="36" text-anchor="middle" font-size="14" fill="#495057">Aa</text>' +
            '</symbol>' +
            // REDUCER off (трапеция — сужение давления)
            '<symbol id="symbol-REDUCER-off" viewBox="0 0 60 60">' +
            '<polygon points="8,15 52,22 52,38 8,45" fill="#f3e8ff" stroke="#8e44ad" stroke-width="2.5"/>' +
            '<path d="M30 25 L30 35" stroke="#8e44ad" stroke-width="2"/>' +
            '<path d="M25 30 L35 30" stroke="#8e44ad" stroke-width="2"/>' +
            '</symbol>' +
            // REDUCER on
            '<symbol id="symbol-REDUCER-on" viewBox="0 0 60 60">' +
            '<polygon points="8,15 52,22 52,38 8,45" fill="#d4edda" stroke="#27ae60" stroke-width="2.5"/>' +
            '<path d="M30 25 L30 35" stroke="#27ae60" stroke-width="2"/>' +
            '<path d="M25 30 L35 30" stroke="#27ae60" stroke-width="2"/>' +
            '</symbol>' +
            // SAFETY_VALVE off (клапан с пружиной)
            '<symbol id="symbol-SAFETY_VALVE-off" viewBox="0 0 60 60">' +
            '<polygon points="20,42 30,18 40,42" fill="#fef3e2" stroke="#e67e22" stroke-width="2.5"/>' +
            '<path d="M25 12 L28 16 L32 10 L35 16" stroke="#e67e22" stroke-width="2" fill="none"/>' +
            '<line x1="30" y1="18" x2="30" y2="12" stroke="#e67e22" stroke-width="2"/>' +
            '<line x1="18" y1="48" x2="42" y2="48" stroke="#e67e22" stroke-width="2.5"/>' +
            '</symbol>' +
            // SAFETY_VALVE on (то же, не переключается)
            '<symbol id="symbol-SAFETY_VALVE-on" viewBox="0 0 60 60">' +
            '<polygon points="20,42 30,18 40,42" fill="#fef3e2" stroke="#e67e22" stroke-width="2.5"/>' +
            '<path d="M25 12 L28 16 L32 10 L35 16" stroke="#e67e22" stroke-width="2" fill="none"/>' +
            '<line x1="30" y1="18" x2="30" y2="12" stroke="#e67e22" stroke-width="2"/>' +
            '<line x1="18" y1="48" x2="42" y2="48" stroke="#e67e22" stroke-width="2.5"/>' +
            '</symbol>' +
            // FILTER off (прямоугольник с сеткой)
            '<symbol id="symbol-FILTER-off" viewBox="0 0 60 60">' +
            '<rect x="10" y="10" width="40" height="40" rx="4" fill="#e8f8f5" stroke="#1abc9c" stroke-width="2.5"/>' +
            '<line x1="10" y1="22" x2="50" y2="22" stroke="#1abc9c" stroke-width="1.5"/>' +
            '<line x1="10" y1="34" x2="50" y2="34" stroke="#1abc9c" stroke-width="1.5"/>' +
            '<line x1="22" y1="10" x2="22" y2="50" stroke="#1abc9c" stroke-width="1.5"/>' +
            '<line x1="38" y1="10" x2="38" y2="50" stroke="#1abc9c" stroke-width="1.5"/>' +
            '</symbol>' +
            // FILTER on (то же)
            '<symbol id="symbol-FILTER-on" viewBox="0 0 60 60">' +
            '<rect x="10" y="10" width="40" height="40" rx="4" fill="#e8f8f5" stroke="#1abc9c" stroke-width="2.5"/>' +
            '<line x1="10" y1="22" x2="50" y2="22" stroke="#1abc9c" stroke-width="1.5"/>' +
            '<line x1="10" y1="34" x2="50" y2="34" stroke="#1abc9c" stroke-width="1.5"/>' +
            '<line x1="22" y1="10" x2="22" y2="50" stroke="#1abc9c" stroke-width="1.5"/>' +
            '<line x1="38" y1="10" x2="38" y2="50" stroke="#1abc9c" stroke-width="1.5"/>' +
            '</symbol>' +
            // CHECK_VALVE off (треугольник с вертикальной чертой — обратный клапан)
            '<symbol id="symbol-CHECK_VALVE-off" viewBox="0 0 60 60">' +
            '<polygon points="15,15 42,30 15,45" fill="#eef2f7" stroke="#34495e" stroke-width="2.5"/>' +
            '<line x1="42" y1="12" x2="42" y2="48" stroke="#34495e" stroke-width="3"/>' +
            '</symbol>' +
            // CHECK_VALVE on (то же)
            '<symbol id="symbol-CHECK_VALVE-on" viewBox="0 0 60 60">' +
            '<polygon points="15,15 42,30 15,45" fill="#eef2f7" stroke="#34495e" stroke-width="2.5"/>' +
            '<line x1="42" y1="12" x2="42" y2="48" stroke="#34495e" stroke-width="3"/>' +
            '</symbol>' +
            '</defs>';
    }

    // ========== Загрузка/сохранение ==========

    function loadData() {
        fetch('/employee/chief/schemas/loadSchemaData/' + schemaId)
            .then(function (r) { return r.json(); })
            .then(function (data) {
                elements = [];
                connections = [];
                elementsLayer.innerHTML = '';
                connectionsLayer.innerHTML = '';

                (data.elements || []).forEach(function (el) {
                    var elem = {
                        id: el.id || nextTempId--,
                        name: el.name,
                        elementType: el.elementType,
                        posX: el.posX,
                        posY: el.posY,
                        width: el.width || 60,
                        height: el.height || 60,
                        initialState: el.initialState,
                        rotation: el.rotation || 0,
                        minValue: el.minValue != null ? el.minValue : null,
                        maxValue: el.maxValue != null ? el.maxValue : null,
                        svgGroup: null
                    };
                    elements.push(elem);
                    renderElement(elem);
                });

                (data.connections || []).forEach(function (conn) {
                    var c = {
                        id: conn.id || nextTempId--,
                        sourceId: conn.sourceElementId,
                        targetId: conn.targetElementId,
                        pathData: conn.pathData,
                        svgLine: null
                    };
                    connections.push(c);
                    renderConnection(c);
                });
            });
    }

    function save() {
        showSaveStatus('saving', 'Сохранение...');
        var data = {
            width: canvasWidth,
            height: canvasHeight,
            elements: elements.map(function (el) {
                return {
                    id: el.id,
                    name: el.name,
                    elementType: el.elementType,
                    posX: el.posX,
                    posY: el.posY,
                    width: el.width,
                    height: el.height,
                    initialState: el.initialState,
                    rotation: el.rotation,
                    minValue: el.minValue != null ? el.minValue : null,
                    maxValue: el.maxValue != null ? el.maxValue : null
                };
            }),
            connections: connections.map(function (c) {
                return {
                    id: c.id,
                    sourceElementId: c.sourceId,
                    targetElementId: c.targetId,
                    pathData: c.pathData
                };
            })
        };

        fetch('/employee/chief/schemas/saveSchemaData/' + schemaId, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        })
            .then(function (r) { return r.json(); })
            .then(function (result) {
                if (result.success) {
                    showSaveStatus('saved', 'Сохранено');
                    // Перезагружаем данные чтобы получить реальные ID
                    loadData();
                } else {
                    showSaveStatus('error', 'Ошибка сохранения');
                }
            })
            .catch(function () {
                showSaveStatus('error', 'Ошибка сети');
            });
    }

    function showSaveStatus(cls, text) {
        var el = document.getElementById('saveIndicator');
        if (!el) return;
        el.className = 'save-indicator ms-2 ' + cls;
        el.textContent = text;
        if (cls === 'saved') {
            setTimeout(function () { el.textContent = ''; }, 3000);
        }
    }

    // ========== Рендеринг элементов ==========

    function renderElement(elem) {
        var ns = 'http://www.w3.org/2000/svg';
        var group = document.createElementNS(ns, 'g');
        group.classList.add('element-group');
        group.dataset.elemId = elem.id;

        updateElementTransform(group, elem);

        // Border rect (for selection highlight)
        var border = document.createElementNS(ns, 'rect');
        border.classList.add('element-border');
        border.setAttribute('x', '-2');
        border.setAttribute('y', '-2');
        border.setAttribute('width', elem.width + 4);
        border.setAttribute('height', elem.height + 18);
        border.setAttribute('rx', '4');
        border.setAttribute('fill', 'transparent');
        border.setAttribute('stroke', 'transparent');
        border.setAttribute('stroke-width', '2');
        group.appendChild(border);

        // Symbol
        var use = document.createElementNS(ns, 'use');
        var state = elem.initialState ? 'on' : 'off';
        use.setAttribute('href', '#symbol-' + elem.elementType + '-' + state);
        use.setAttribute('width', elem.width);
        use.setAttribute('height', elem.height);
        group.appendChild(use);

        // Label
        var text = document.createElementNS(ns, 'text');
        text.setAttribute('x', elem.width / 2);
        text.setAttribute('y', elem.height + 14);
        text.setAttribute('text-anchor', 'middle');
        text.setAttribute('font-size', '11');
        text.setAttribute('fill', '#495057');
        text.setAttribute('pointer-events', 'none');
        text.textContent = elem.name || '';
        group.appendChild(text);

        elementsLayer.appendChild(group);
        elem.svgGroup = group;
    }

    function updateElementTransform(group, elem) {
        var t = 'translate(' + elem.posX + ',' + elem.posY + ')';
        if (elem.rotation) {
            t += ' rotate(' + elem.rotation + ',' + (elem.width / 2) + ',' + (elem.height / 2) + ')';
        }
        group.setAttribute('transform', t);
    }

    function refreshElement(elem) {
        if (!elem.svgGroup) return;
        updateElementTransform(elem.svgGroup, elem);

        var use = elem.svgGroup.querySelector('use');
        if (use) {
            var state = elem.initialState ? 'on' : 'off';
            use.setAttribute('href', '#symbol-' + elem.elementType + '-' + state);
            use.setAttribute('width', elem.width);
            use.setAttribute('height', elem.height);
        }

        var border = elem.svgGroup.querySelector('.element-border');
        if (border) {
            border.setAttribute('width', elem.width + 4);
            border.setAttribute('height', elem.height + 18);
        }

        var text = elem.svgGroup.querySelector('text');
        if (text) {
            text.setAttribute('x', elem.width / 2);
            text.setAttribute('y', elem.height + 14);
            text.textContent = elem.name || '';
        }

        // Update connections
        connections.forEach(function (c) {
            if (c.sourceId === elem.id || c.targetId === elem.id) {
                refreshConnectionLine(c);
            }
        });
    }

    // ========== Рендеринг соединений ==========

    function renderConnection(conn) {
        var ns = 'http://www.w3.org/2000/svg';
        var src = findElement(conn.sourceId);
        var tgt = findElement(conn.targetId);
        if (!src || !tgt) return;

        var line = document.createElementNS(ns, 'line');
        line.classList.add('connection-line');
        line.dataset.connId = conn.id;
        line.setAttribute('x1', src.posX + src.width / 2);
        line.setAttribute('y1', src.posY + src.height / 2);
        line.setAttribute('x2', tgt.posX + tgt.width / 2);
        line.setAttribute('y2', tgt.posY + tgt.height / 2);
        line.setAttribute('marker-end', 'url(#editor-arrow)');
        connectionsLayer.appendChild(line);
        conn.svgLine = line;
    }

    function refreshConnectionLine(conn) {
        if (!conn.svgLine) return;
        var src = findElement(conn.sourceId);
        var tgt = findElement(conn.targetId);
        if (!src || !tgt) return;
        conn.svgLine.setAttribute('x1', src.posX + src.width / 2);
        conn.svgLine.setAttribute('y1', src.posY + src.height / 2);
        conn.svgLine.setAttribute('x2', tgt.posX + tgt.width / 2);
        conn.svgLine.setAttribute('y2', tgt.posY + tgt.height / 2);
    }

    // ========== Режимы ==========

    function setMode(m) {
        mode = m;
        connectSource = null;
        tempLine.style.display = 'none';
        document.getElementById('btnSelect').classList.toggle('active', m === 'select');
        document.getElementById('btnConnect').classList.toggle('active', m === 'connect');
        svg.style.cursor = m === 'connect' ? 'crosshair' : 'default';
    }

    // ========== События ==========

    function setupEvents() {
        // Drag from palette
        var paletteItems = document.querySelectorAll('.palette-item');
        paletteItems.forEach(function (item) {
            item.addEventListener('dragstart', function (e) {
                e.dataTransfer.setData('text/plain', item.dataset.type);
                e.dataTransfer.effectAllowed = 'copy';
            });
        });

        var wrapper = document.getElementById('canvasWrapper');
        wrapper.addEventListener('dragover', function (e) {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'copy';
        });
        wrapper.addEventListener('drop', function (e) {
            e.preventDefault();
            var type = e.dataTransfer.getData('text/plain');
            if (!type) return;
            var pt = screenToSVG(e.clientX, e.clientY);
            addElement(type, pt.x - 30, pt.y - 30);
        });

        // SVG mouse events
        svg.addEventListener('mousedown', onMouseDown);
        svg.addEventListener('mousemove', onMouseMove);
        svg.addEventListener('mouseup', onMouseUp);
        svg.addEventListener('wheel', onWheel, { passive: false });

        // Keyboard
        document.addEventListener('keydown', onKeyDown);
    }

    function onMouseDown(e) {
        var pt = screenToSVG(e.clientX, e.clientY);

        // Middle mouse button or Ctrl+Left = pan
        if (e.button === 1 || (e.button === 0 && e.ctrlKey)) {
            isPanning = true;
            panStart = { x: e.clientX, y: e.clientY };
            e.preventDefault();
            return;
        }

        if (e.button !== 0) return;

        var elemGroup = e.target.closest('.element-group');
        var connLine = e.target.closest('.connection-line');

        if (mode === 'connect') {
            if (elemGroup) {
                var elemId = parseInt(elemGroup.dataset.elemId);
                if (!connectSource) {
                    connectSource = elemId;
                    var src = findElement(elemId);
                    if (src) {
                        tempLine.setAttribute('x1', src.posX + src.width / 2);
                        tempLine.setAttribute('y1', src.posY + src.height / 2);
                        tempLine.setAttribute('x2', pt.x);
                        tempLine.setAttribute('y2', pt.y);
                        tempLine.style.display = '';
                    }
                } else {
                    if (elemId !== connectSource) {
                        addConnection(connectSource, elemId);
                    }
                    connectSource = null;
                    tempLine.style.display = 'none';
                }
            } else {
                connectSource = null;
                tempLine.style.display = 'none';
            }
            return;
        }

        // Select mode
        if (elemGroup) {
            var eId = parseInt(elemGroup.dataset.elemId);
            selectElement(eId);
            // Start drag
            var el = findElement(eId);
            if (el) {
                isDragging = true;
                dragElement = el;
                dragOffset = { x: pt.x - el.posX, y: pt.y - el.posY };
            }
            e.preventDefault();
        } else if (connLine) {
            var cId = parseInt(connLine.dataset.connId);
            selectConnection(cId);
            e.preventDefault();
        } else {
            deselectAll();
        }
    }

    function onMouseMove(e) {
        if (isPanning) {
            var dx = (e.clientX - panStart.x) / zoom;
            var dy = (e.clientY - panStart.y) / zoom;
            viewBox.x -= dx;
            viewBox.y -= dy;
            panStart = { x: e.clientX, y: e.clientY };
            updateViewBox();
            return;
        }

        if (mode === 'connect' && connectSource) {
            var pt = screenToSVG(e.clientX, e.clientY);
            tempLine.setAttribute('x2', pt.x);
            tempLine.setAttribute('y2', pt.y);
            return;
        }

        if (isDragging && dragElement) {
            var pt2 = screenToSVG(e.clientX, e.clientY);
            dragElement.posX = Math.max(0, Math.min(canvasWidth - dragElement.width, pt2.x - dragOffset.x));
            dragElement.posY = Math.max(0, Math.min(canvasHeight - dragElement.height, pt2.y - dragOffset.y));
            // Snap to grid (20px)
            dragElement.posX = Math.round(dragElement.posX / 20) * 20;
            dragElement.posY = Math.round(dragElement.posY / 20) * 20;
            refreshElement(dragElement);
            if (selectedElement && selectedElement.id === dragElement.id) {
                showElementProps(dragElement);
            }
        }
    }

    function onMouseUp(e) {
        isPanning = false;
        isDragging = false;
        dragElement = null;
    }

    function onWheel(e) {
        e.preventDefault();
        var delta = e.deltaY > 0 ? -0.1 : 0.1;
        var newZoom = Math.max(0.2, Math.min(3, zoom + delta));

        // Zoom toward cursor
        var pt = screenToSVG(e.clientX, e.clientY);
        var ratio = newZoom / zoom;
        viewBox.x = pt.x - (pt.x - viewBox.x) / ratio;
        viewBox.y = pt.y - (pt.y - viewBox.y) / ratio;

        zoom = newZoom;
        viewBox.w = canvasWidth / zoom;
        viewBox.h = canvasHeight / zoom;
        updateViewBox();
        updateZoomLabel();
    }

    function onKeyDown(e) {
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.tagName === 'SELECT') return;

        if (e.key === 'Delete' || e.key === 'Backspace') {
            deleteSelected();
            e.preventDefault();
        }
        if (e.key === 'v' || e.key === 'V') setMode('select');
        if (e.key === 'c' || e.key === 'C') setMode('connect');
        if (e.key === 'Escape') {
            deselectAll();
            connectSource = null;
            tempLine.style.display = 'none';
        }
        if (e.ctrlKey && (e.key === 's' || e.key === 'S')) {
            e.preventDefault();
            save();
        }
    }

    // ========== Операции ==========

    function addElement(type, x, y) {
        var typeNames = {
            'VALVE': 'VP', 'PUMP': 'N', 'SWITCH': 'WS',
            'SENSOR_PRESSURE': 'PT', 'SENSOR_TEMPERATURE': 'TT',
            'HEATER': 'NR', 'LOCK': 'BH', 'LABEL': 'L',
            'REDUCER': 'RD', 'SAFETY_VALVE': 'SV', 'FILTER': 'FL', 'CHECK_VALVE': 'CV'
        };
        var prefix = typeNames[type] || type;
        var count = elements.filter(function (el) { return el.elementType === type; }).length + 1;

        var elem = {
            id: nextTempId--,
            name: prefix + count,
            elementType: type,
            posX: Math.round(x / 20) * 20,
            posY: Math.round(y / 20) * 20,
            width: 60,
            height: 60,
            initialState: false,
            rotation: 0,
            minValue: null,
            maxValue: null,
            svgGroup: null
        };
        elements.push(elem);
        renderElement(elem);
        selectElement(elem.id);
    }

    function addConnection(sourceId, targetId) {
        // Check duplicate
        var exists = connections.some(function (c) {
            return (c.sourceId === sourceId && c.targetId === targetId) ||
                   (c.sourceId === targetId && c.targetId === sourceId);
        });
        if (exists) return;

        var conn = {
            id: nextTempId--,
            sourceId: sourceId,
            targetId: targetId,
            pathData: null,
            svgLine: null
        };
        connections.push(conn);
        renderConnection(conn);
    }

    function deleteSelected() {
        if (selectedElement) {
            // Remove connected connections
            var elemId = selectedElement.id;
            connections = connections.filter(function (c) {
                if (c.sourceId === elemId || c.targetId === elemId) {
                    if (c.svgLine) c.svgLine.remove();
                    return false;
                }
                return true;
            });
            // Remove element
            if (selectedElement.svgGroup) selectedElement.svgGroup.remove();
            elements = elements.filter(function (el) { return el.id !== elemId; });
            selectedElement = null;
            hideProps();
        } else if (selectedConnection) {
            var connId = selectedConnection.id;
            if (selectedConnection.svgLine) selectedConnection.svgLine.remove();
            connections = connections.filter(function (c) { return c.id !== connId; });
            selectedConnection = null;
            hideProps();
        }
    }

    // ========== Выделение ==========

    function selectElement(id) {
        deselectAll();
        var elem = findElement(id);
        if (!elem) return;
        selectedElement = elem;
        if (elem.svgGroup) elem.svgGroup.classList.add('selected');
        showElementProps(elem);
    }

    function selectConnection(id) {
        deselectAll();
        var conn = findConnection(id);
        if (!conn) return;
        selectedConnection = conn;
        if (conn.svgLine) conn.svgLine.classList.add('selected');
        showConnectionProps(conn);
    }

    function deselectAll() {
        if (selectedElement && selectedElement.svgGroup) {
            selectedElement.svgGroup.classList.remove('selected');
        }
        if (selectedConnection && selectedConnection.svgLine) {
            selectedConnection.svgLine.classList.remove('selected');
        }
        selectedElement = null;
        selectedConnection = null;
        hideProps();
    }

    // ========== Панель свойств ==========

    function showElementProps(elem) {
        document.getElementById('noSelection').style.display = 'none';
        document.getElementById('elementProps').style.display = 'block';
        document.getElementById('connectionProps').style.display = 'none';

        var typeNames = {
            'VALVE': 'Клапан', 'PUMP': 'Насос', 'SWITCH': 'Переключатель',
            'SENSOR_PRESSURE': 'Датчик давления', 'SENSOR_TEMPERATURE': 'Датчик температуры',
            'HEATER': 'Нагреватель', 'LOCK': 'Блокировка',
            'REDUCER': 'Редуктор', 'SAFETY_VALVE': 'Предохр. клапан',
            'FILTER': 'Фильтр', 'CHECK_VALVE': 'Обратный клапан'
        };

        document.getElementById('propName').value = elem.name || '';
        document.getElementById('propType').value = typeNames[elem.elementType] || elem.elementType;
        document.getElementById('propX').value = Math.round(elem.posX);
        document.getElementById('propY').value = Math.round(elem.posY);
        document.getElementById('propWidth').value = elem.width;
        document.getElementById('propHeight').value = elem.height;
        document.getElementById('propRotation').value = elem.rotation;
        document.getElementById('propInitialState').checked = elem.initialState;

        // Поля min/max для датчиков
        var sensorRangeBlock = document.getElementById('sensorRangeProps');
        if (sensorRangeBlock) {
            var isSensor = elem.elementType === 'SENSOR_PRESSURE' || elem.elementType === 'SENSOR_TEMPERATURE';
            sensorRangeBlock.style.display = isSensor ? 'block' : 'none';
            if (isSensor) {
                document.getElementById('propMinValue').value = elem.minValue != null ? elem.minValue : '';
                document.getElementById('propMaxValue').value = elem.maxValue != null ? elem.maxValue : '';
            }
        }
    }

    function showConnectionProps(conn) {
        document.getElementById('noSelection').style.display = 'none';
        document.getElementById('elementProps').style.display = 'none';
        document.getElementById('connectionProps').style.display = 'block';

        var src = findElement(conn.sourceId);
        var tgt = findElement(conn.targetId);
        document.getElementById('connInfo').textContent =
            (src ? src.name : '?') + ' → ' + (tgt ? tgt.name : '?');
    }

    function hideProps() {
        document.getElementById('noSelection').style.display = 'block';
        document.getElementById('elementProps').style.display = 'none';
        document.getElementById('connectionProps').style.display = 'none';
    }

    function updateProperty(prop, value) {
        if (!selectedElement) return;
        selectedElement[prop] = value;
        refreshElement(selectedElement);
    }

    // ========== Zoom ==========

    function zoomIn() {
        zoom = Math.min(3, zoom + 0.2);
        viewBox.w = canvasWidth / zoom;
        viewBox.h = canvasHeight / zoom;
        updateViewBox();
        updateZoomLabel();
    }

    function zoomOut() {
        zoom = Math.max(0.2, zoom - 0.2);
        viewBox.w = canvasWidth / zoom;
        viewBox.h = canvasHeight / zoom;
        updateViewBox();
        updateZoomLabel();
    }

    function zoomReset() {
        zoom = 1;
        viewBox = { x: 0, y: 0, w: canvasWidth, h: canvasHeight };
        updateViewBox();
        updateZoomLabel();
    }

    function updateViewBox() {
        svg.setAttribute('viewBox', viewBox.x + ' ' + viewBox.y + ' ' + viewBox.w + ' ' + viewBox.h);
        svg.style.width = '100%';
        svg.style.height = '100%';
    }

    function updateZoomLabel() {
        var label = document.getElementById('zoomLevel');
        if (label) label.textContent = Math.round(zoom * 100) + '%';
    }

    // ========== Утилиты ==========

    function screenToSVG(clientX, clientY) {
        var pt = svg.createSVGPoint();
        pt.x = clientX;
        pt.y = clientY;
        var ctm = svg.getScreenCTM();
        if (ctm) {
            var svgPt = pt.matrixTransform(ctm.inverse());
            return { x: svgPt.x, y: svgPt.y };
        }
        return { x: clientX, y: clientY };
    }

    function findElement(id) {
        return elements.find(function (el) { return el.id === id; }) || null;
    }

    function findConnection(id) {
        return connections.find(function (c) { return c.id === id; }) || null;
    }

    // ========== Public API ==========

    return {
        init: init,
        initPreview: initPreview,
        setMode: setMode,
        save: save,
        deleteSelected: deleteSelected,
        updateProperty: updateProperty,
        zoomIn: zoomIn,
        zoomOut: zoomOut,
        zoomReset: zoomReset,
        getSymbolDefs: getSymbolDefs
    };

})();
