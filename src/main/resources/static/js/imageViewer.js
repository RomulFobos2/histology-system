/* ========================================
   АИС ГИ — Image Viewer
   Zoom (scroll) + Pan (drag)
   ======================================== */

(function () {
    var container = document.getElementById('viewerContainer');
    var img = document.getElementById('viewerImage');
    var zoomInfo = document.getElementById('zoomInfo');

    if (!container || !img) return;

    var scale = 1;
    var translateX = 0;
    var translateY = 0;
    var isDragging = false;
    var startX = 0;
    var startY = 0;
    var lastX = 0;
    var lastY = 0;

    var MIN_SCALE = 0.1;
    var MAX_SCALE = 15;
    var ZOOM_STEP = 0.15;

    function updateTransform() {
        img.style.transform = 'translate(' + translateX + 'px, ' + translateY + 'px) scale(' + scale + ')';
        if (zoomInfo) {
            zoomInfo.textContent = Math.round(scale * 100) + '%';
        }
    }

    // ===== Zoom (mouse wheel) =====
    container.addEventListener('wheel', function (e) {
        e.preventDefault();
        var delta = e.deltaY < 0 ? 1 : -1;
        var newScale = scale * (1 + delta * ZOOM_STEP);
        newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));

        // Zoom towards cursor position
        var rect = container.getBoundingClientRect();
        var cx = e.clientX - rect.left - rect.width / 2;
        var cy = e.clientY - rect.top - rect.height / 2;

        var factor = newScale / scale;
        translateX = cx - factor * (cx - translateX);
        translateY = cy - factor * (cy - translateY);

        scale = newScale;
        updateTransform();
    }, { passive: false });

    // ===== Pan (mouse drag) =====
    container.addEventListener('mousedown', function (e) {
        if (e.button !== 0) return;
        isDragging = true;
        startX = e.clientX;
        startY = e.clientY;
        lastX = translateX;
        lastY = translateY;
        container.classList.add('dragging');
        e.preventDefault();
    });

    document.addEventListener('mousemove', function (e) {
        if (!isDragging) return;
        translateX = lastX + (e.clientX - startX);
        translateY = lastY + (e.clientY - startY);
        updateTransform();
    });

    document.addEventListener('mouseup', function () {
        isDragging = false;
        container.classList.remove('dragging');
    });

    // ===== Touch support =====
    var lastTouchDist = 0;
    var lastTouchScale = 1;

    container.addEventListener('touchstart', function (e) {
        if (e.touches.length === 1) {
            isDragging = true;
            startX = e.touches[0].clientX;
            startY = e.touches[0].clientY;
            lastX = translateX;
            lastY = translateY;
        } else if (e.touches.length === 2) {
            isDragging = false;
            lastTouchDist = getTouchDist(e.touches);
            lastTouchScale = scale;
        }
    }, { passive: true });

    container.addEventListener('touchmove', function (e) {
        e.preventDefault();
        if (e.touches.length === 1 && isDragging) {
            translateX = lastX + (e.touches[0].clientX - startX);
            translateY = lastY + (e.touches[0].clientY - startY);
            updateTransform();
        } else if (e.touches.length === 2) {
            var dist = getTouchDist(e.touches);
            var newScale = lastTouchScale * (dist / lastTouchDist);
            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
            updateTransform();
        }
    }, { passive: false });

    container.addEventListener('touchend', function () {
        isDragging = false;
    });

    function getTouchDist(touches) {
        var dx = touches[0].clientX - touches[1].clientX;
        var dy = touches[0].clientY - touches[1].clientY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ===== Keyboard shortcuts =====
    document.addEventListener('keydown', function (e) {
        if (e.key === '+' || e.key === '=') { zoomIn(); e.preventDefault(); }
        if (e.key === '-') { zoomOut(); e.preventDefault(); }
        if (e.key === '0') { resetView(); e.preventDefault(); }
        if (e.key === 'Escape') { window.history.back(); }
    });

    // ===== Global functions for toolbar buttons =====
    window.zoomIn = function () {
        scale = Math.min(MAX_SCALE, scale * (1 + ZOOM_STEP));
        updateTransform();
    };

    window.zoomOut = function () {
        scale = Math.max(MIN_SCALE, scale * (1 - ZOOM_STEP));
        updateTransform();
    };

    window.resetView = function () {
        scale = 1;
        translateX = 0;
        translateY = 0;
        updateTransform();
    };

    updateTransform();
})();
