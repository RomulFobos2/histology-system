/* ========================================
   АИС ГИ — JavaScript
   Top-navbar layout
   ======================================== */

document.addEventListener('DOMContentLoaded', function () {

    // ===== Mobile navbar toggle =====
    var toggler = document.getElementById('navbarToggler');
    var collapse = document.getElementById('navbarCollapse');

    if (toggler && collapse) {
        toggler.addEventListener('click', function () {
            collapse.classList.toggle('show');
        });

        // Close on click outside
        document.addEventListener('click', function (e) {
            if (!collapse.contains(e.target) && !toggler.contains(e.target)) {
                collapse.classList.remove('show');
            }
        });
    }

    // ===== Dropdown toggle on mobile (tap) =====
    var dropdowns = document.querySelectorAll('.nav-dropdown > a');
    dropdowns.forEach(function (link) {
        link.addEventListener('click', function (e) {
            if (window.innerWidth <= 992) {
                e.preventDefault();
                var parent = link.parentElement;
                // Close other dropdowns
                document.querySelectorAll('.nav-dropdown.open').forEach(function (d) {
                    if (d !== parent) d.classList.remove('open');
                });
                parent.classList.toggle('open');
            }
        });
    });

    // Reset mobile state on resize to desktop
    window.addEventListener('resize', function () {
        if (window.innerWidth > 992) {
            if (collapse) collapse.classList.remove('show');
            document.querySelectorAll('.nav-dropdown.open').forEach(function (d) {
                d.classList.remove('open');
            });
        }
    });
});

/**
 * Подтверждение деактивации пользователя
 */
function confirmDeactivate(url) {
    if (confirm('Вы уверены, что хотите деактивировать этого пользователя?')) {
        window.location.href = url;
    }
}

/**
 * Подтверждение удаления (универсальная)
 */
function confirmDelete(url) {
    if (confirm('Вы уверены, что хотите выполнить это действие?')) {
        window.location.href = url;
    }
}
