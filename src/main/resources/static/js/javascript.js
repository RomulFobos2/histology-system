/* ========================================
   АИС ГИ — JavaScript
   ======================================== */

document.addEventListener('DOMContentLoaded', function () {

    // ===== Sidebar toggle =====
    var toggler = document.getElementById('sidebarToggle');
    var sidebar = document.getElementById('sidebar');
    var contentWrapper = document.getElementById('content-wrapper');
    var overlay = document.getElementById('sidebarOverlay');

    if (toggler && sidebar) {
        toggler.addEventListener('click', function () {
            if (window.innerWidth <= 768) {
                sidebar.classList.toggle('mobile-open');
                if (overlay) overlay.classList.toggle('active');
            } else {
                sidebar.classList.toggle('collapsed');
                if (contentWrapper) contentWrapper.classList.toggle('sidebar-collapsed');
            }
        });
    }

    // Close sidebar on mobile when clicking overlay
    if (overlay) {
        overlay.addEventListener('click', function () {
            sidebar.classList.remove('mobile-open');
            overlay.classList.remove('active');
        });
    }

    // Close sidebar on mobile when resizing to desktop
    window.addEventListener('resize', function () {
        if (window.innerWidth > 768 && sidebar) {
            sidebar.classList.remove('mobile-open');
            if (overlay) overlay.classList.remove('active');
        }
    });
});

/**
 * Подтверждение деактивации пользователя
 * @param {string} url — URL для деактивации
 */
function confirmDeactivate(url) {
    if (confirm('Вы уверены, что хотите деактивировать этого пользователя?')) {
        window.location.href = url;
    }
}

/**
 * Подтверждение удаления (универсальная)
 * @param {string} url — URL для удаления
 */
function confirmDelete(url) {
    if (confirm('Вы уверены, что хотите выполнить это действие?')) {
        window.location.href = url;
    }
}
