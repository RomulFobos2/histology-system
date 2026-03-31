/**
 * testing.js — логика страницы прохождения теста
 * Таймер, DnD для SEQUENCE, валидация, confirm при навигации вперёд
 */

// ========== Таймер ==========

function initTimer(endTimeStr) {
    if (!endTimeStr) return;
    var timerEl = document.getElementById('timer');
    if (!timerEl) return;

    var endTime = new Date(endTimeStr);

    function tick() {
        var remaining = endTime - new Date();
        if (remaining <= 0) {
            timerEl.textContent = '00:00';
            timerEl.classList.add('text-danger');
            // Автосабмит
            var autoForm = document.getElementById('autoSubmitForm');
            if (autoForm) autoForm.submit();
            return;
        }
        var m = Math.floor(remaining / 60000);
        var s = Math.floor((remaining % 60000) / 1000);
        timerEl.textContent = String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');

        // Подсветка при < 60 секунд
        if (remaining < 60000) {
            timerEl.classList.add('text-danger', 'fw-bold');
        }
    }

    setInterval(tick, 1000);
    tick();
}

// ========== DnD для SEQUENCE ==========

function initSequenceDnD() {
    var list = document.getElementById('sequenceSortable');
    if (!list) return;

    var dragSrcEl = null;

    function handleDragStart(e) {
        dragSrcEl = this;
        this.classList.add('dragging');
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/plain', '');
    }

    function handleDragOver(e) {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        var item = this.closest('.sequence-item');
        if (item && item !== dragSrcEl) {
            item.classList.add('drag-over');
        }
    }

    function handleDragLeave() {
        this.classList.remove('drag-over');
    }

    function handleDrop(e) {
        e.preventDefault();
        this.classList.remove('drag-over');
        if (dragSrcEl !== this) {
            var allItems = Array.from(list.querySelectorAll('.sequence-item'));
            var fromIndex = allItems.indexOf(dragSrcEl);
            var toIndex = allItems.indexOf(this);
            if (fromIndex < toIndex) {
                list.insertBefore(dragSrcEl, this.nextSibling);
            } else {
                list.insertBefore(dragSrcEl, this);
            }
            updateSequenceOrder();
        }
    }

    function handleDragEnd() {
        this.classList.remove('dragging');
        list.querySelectorAll('.sequence-item').forEach(function (item) {
            item.classList.remove('drag-over');
        });
    }

    // Touch events для мобильных
    var touchSrcEl = null;
    var touchClone = null;

    function handleTouchStart(e) {
        touchSrcEl = this;
        this.classList.add('dragging');
    }

    function handleTouchMove(e) {
        e.preventDefault();
        var touch = e.touches[0];
        var target = document.elementFromPoint(touch.clientX, touch.clientY);
        if (target) {
            var item = target.closest('.sequence-item');
            if (item && item !== touchSrcEl) {
                list.querySelectorAll('.sequence-item').forEach(function (i) {
                    i.classList.remove('drag-over');
                });
                item.classList.add('drag-over');
            }
        }
    }

    function handleTouchEnd(e) {
        if (!touchSrcEl) return;
        touchSrcEl.classList.remove('dragging');
        var overItem = list.querySelector('.sequence-item.drag-over');
        if (overItem && overItem !== touchSrcEl) {
            var allItems = Array.from(list.querySelectorAll('.sequence-item'));
            var fromIndex = allItems.indexOf(touchSrcEl);
            var toIndex = allItems.indexOf(overItem);
            if (fromIndex < toIndex) {
                list.insertBefore(touchSrcEl, overItem.nextSibling);
            } else {
                list.insertBefore(touchSrcEl, overItem);
            }
            updateSequenceOrder();
        }
        list.querySelectorAll('.sequence-item').forEach(function (item) {
            item.classList.remove('drag-over');
        });
        touchSrcEl = null;
    }

    list.querySelectorAll('.sequence-item').forEach(function (item) {
        item.addEventListener('dragstart', handleDragStart);
        item.addEventListener('dragover', handleDragOver);
        item.addEventListener('dragleave', handleDragLeave);
        item.addEventListener('drop', handleDrop);
        item.addEventListener('dragend', handleDragEnd);
        item.addEventListener('touchstart', handleTouchStart, { passive: true });
        item.addEventListener('touchmove', handleTouchMove, { passive: false });
        item.addEventListener('touchend', handleTouchEnd);
    });
}

function updateSequenceOrder() {
    var list = document.getElementById('sequenceSortable');
    if (!list) return;
    var items = list.querySelectorAll('.sequence-item');
    var ids = [];
    items.forEach(function (item, i) {
        ids.push(item.getAttribute('data-answer-id'));
        var numEl = item.querySelector('.seq-number');
        if (numEl) numEl.textContent = (i + 1);
    });
    var hiddenInput = document.getElementById('sequenceOrderInput');
    if (hiddenInput) {
        hiddenInput.value = ids.join(',');
    }
}

// ========== Валидация формы ==========

function validateAnswerForm(questionType, allowBack) {
    switch (questionType) {
        case 'SINGLE_CHOICE': {
            var selected = document.querySelector('input[name="selectedAnswer"]:checked');
            if (!selected) {
                alert('Выберите один вариант ответа');
                return false;
            }
            break;
        }
        case 'MULTIPLE_CHOICE': {
            var checked = document.querySelectorAll('input[name="selectedAnswers"]:checked');
            if (checked.length === 0) {
                alert('Выберите хотя бы один вариант ответа');
                return false;
            }
            break;
        }
        case 'SEQUENCE': {
            // Порядок всегда задан через DnD, проверяем что hidden input заполнен
            var orderInput = document.getElementById('sequenceOrderInput');
            if (!orderInput || !orderInput.value) {
                alert('Установите порядок элементов');
                return false;
            }
            break;
        }
        case 'MATCHING': {
            var selects = document.querySelectorAll('select[name^="match_"]');
            var allFilled = true;
            selects.forEach(function (sel) {
                if (!sel.value) allFilled = false;
            });
            if (!allFilled) {
                alert('Установите соответствие для всех элементов');
                return false;
            }
            break;
        }
        case 'OPEN_TEXT': {
            var textarea = document.querySelector('textarea[name="openAnswer"]');
            if (!textarea || !textarea.value.trim()) {
                alert('Введите ответ');
                return false;
            }
            break;
        }
    }

    // Предупреждение при навигации вперёд (без возврата)
    if (!allowBack) {
        return confirm('Вы не сможете изменить ответ. Продолжить?');
    }

    return true;
}
