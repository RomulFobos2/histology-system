/* ========================================
   АИС ГИ — JavaScript
   Top-navbar layout
   ======================================== */

document.addEventListener('DOMContentLoaded', function () {

    // ===== Mobile navbar toggle =====
    var toggler = document.getElementById('navbarToggler');
    var collapse = document.getElementById('navbarCollapse');

    if (toggler && collapse) {
        toggler.addEventListener('click', function (e) {
            e.stopPropagation();
            collapse.classList.toggle('show');
        });
    }

    // ===== Dropdown: click-based for reliable behavior =====
    var dropdownToggles = document.querySelectorAll('.nav-dropdown > .nav-link');

    dropdownToggles.forEach(function (link) {
        link.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            var parent = link.parentElement;
            var isOpen = parent.classList.contains('open');

            // Close all other dropdowns
            closeAllDropdowns();

            // Toggle this one
            if (!isOpen) {
                parent.classList.add('open');
            }
        });
    });

    // Close dropdowns when clicking outside
    document.addEventListener('click', function (e) {
        var navbar = document.querySelector('.top-navbar');
        if (navbar && !navbar.contains(e.target)) {
            closeAllDropdowns();
            if (collapse) collapse.classList.remove('show');
        }
    });

    // Close dropdown when clicking a dropdown link (navigation)
    document.querySelectorAll('.dropdown-link').forEach(function (link) {
        link.addEventListener('click', function () {
            closeAllDropdowns();
        });
    });

    // Desktop: hover support (only on wide screens)
    var dropdownItems = document.querySelectorAll('.nav-dropdown');
    var hoverTimeout;

    dropdownItems.forEach(function (item) {
        item.addEventListener('mouseenter', function () {
            if (window.innerWidth > 992) {
                clearTimeout(hoverTimeout);
                // Close others first
                dropdownItems.forEach(function (other) {
                    if (other !== item) other.classList.remove('open');
                });
                item.classList.add('open');
            }
        });

        item.addEventListener('mouseleave', function () {
            if (window.innerWidth > 992) {
                hoverTimeout = setTimeout(function () {
                    item.classList.remove('open');
                }, 150);  // Небольшая задержка чтобы не закрывалось при случайном выходе курсора
            }
        });
    });

    // Reset on resize
    window.addEventListener('resize', function () {
        if (window.innerWidth > 992) {
            if (collapse) collapse.classList.remove('show');
        }
        closeAllDropdowns();
    });

    function closeAllDropdowns() {
        document.querySelectorAll('.nav-dropdown.open').forEach(function (d) {
            d.classList.remove('open');
        });
    }
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
