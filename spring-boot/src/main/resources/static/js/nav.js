// Navigation menu injection
document.addEventListener('DOMContentLoaded', () => {
    try {
        const nav = document.getElementById('main-nav');
        if (!nav) {
            window.appLogger?.warn('Navigation element not found');
            return;
        }

        // Get current page filename
        const currentPage = window.location.pathname.split('/').pop() || 'dashboard.html';
        window.appLogger?.info('Navigation initialized', { currentPage });

        // Navigation items
        const navItems = [
            { href: 'dashboard.html', label: 'Dashboard' },
            { href: 'feeding.html', label: 'Feeding' },
            { href: 'journal.html', label: 'Journal' },
            { href: 'pumping.html', label: 'Pumping' },
            { href: 'weight-tracker.html', label: 'Weight Tracker' },
            { href: 'length-tracker.html', label: 'Length Tracker' },
            { href: 'home-assistant.html', label: 'Home Assistant' },
            { href: 'brightness-dashboard.html', label: 'Brightness Dashboard' },
            { href: 'delayed-actions.html', label: 'Delayed Actions' },
            { href: 'bathroom-telemetry.html', label: 'Bathroom Telemetry' }
        ];

        // Build navigation HTML
        const navHTML = navItems.map(item => {
            const activeClass = currentPage === item.href ? ' class="active"' : '';
            return `<a href="${item.href}"${activeClass}>${item.label}</a>`;
        }).join('\n            ');

        nav.innerHTML = navHTML;
    } catch (error) {
        window.appLogger?.error('Error initializing navigation', { error: error.message });
    }
});
