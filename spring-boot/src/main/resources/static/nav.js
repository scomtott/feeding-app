// Navigation menu injection
document.addEventListener('DOMContentLoaded', () => {
    const nav = document.getElementById('main-nav');
    if (!nav) return;

    // Get current page filename
    const currentPage = window.location.pathname.split('/').pop() || 'daily-totals.html';

    // Navigation items
    const navItems = [
        { href: 'daily-totals.html', label: 'Daily Totals' },
        { href: 'feeding.html', label: 'Feeding' },
        { href: 'pumping.html', label: 'Pumping' },
        { href: 'weight-tracker.html', label: 'Weight Tracker' }
    ];

    // Build navigation HTML
    const navHTML = navItems.map(item => {
        const activeClass = currentPage === item.href ? ' class="active"' : '';
        return `<a href="${item.href}"${activeClass}>${item.label}</a>`;
    }).join('\n            ');

    nav.innerHTML = navHTML;
});
