const AUTO_REFRESH_INTERVAL_MS = 10000;

let refreshTimerId = null;
let loadInFlight = false;

document.addEventListener('DOMContentLoaded', () => {
    const reloadButton = document.getElementById('reloadButton');
    reloadButton.addEventListener('click', () => loadPendingActions(false));

    document.addEventListener('visibilitychange', onVisibilityChanged);
    window.addEventListener('beforeunload', stopAutoRefresh);

    loadPendingActions(false);
    startAutoRefresh();
});

async function loadPendingActions(isBackgroundRefresh) {
    if (loadInFlight) {
        return;
    }

    loadInFlight = true;
    const status = document.getElementById('statusMessage');
    if (!isBackgroundRefresh) {
        status.textContent = '';
        status.classList.remove('error');
    }

    try {
        const response = await fetch('/api/homeassistant/delayed-actions');
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const actions = await response.json();
        renderActions(Array.isArray(actions) ? actions : []);

        if (isBackgroundRefresh && !status.classList.contains('error')) {
            status.textContent = `Auto-refreshed at ${new Date().toLocaleTimeString()}`;
        }
    } catch (error) {
        window.appLogger?.error('Failed to load delayed actions', { error: error.message });
        renderError(error.message);
        status.textContent = `Failed to load delayed actions: ${error.message}`;
        status.classList.add('error');
    } finally {
        loadInFlight = false;
    }
}

function renderActions(actions) {
    const summaryText = document.getElementById('summaryText');
    const tbody = document.getElementById('actionsTableBody');

    if (!actions.length) {
        summaryText.textContent = 'No pending delayed actions found.';
        tbody.innerHTML = '<tr><td colspan="7" class="muted">No pending delayed actions</td></tr>';
        return;
    }

    summaryText.textContent = `${actions.length} pending delayed action${actions.length === 1 ? '' : 's'}.`;

    tbody.innerHTML = actions.map(action => {
        const nextFire = parseDate(action.nextFireTime);
        const rootScheduled = parseEpoch(action.rootScheduledAtEpochMs);
        const timeRemaining = formatRemaining(nextFire);
        const phase = action.phase || '-';
        const attempt = Number.isFinite(Number(action.attempt)) ? Number(action.attempt) : '-';

        return `
            <tr>
                <td>${escapeHtml(action.actionKey || '-')}</td>
                <td>${escapeHtml(action.lightEntityId || '-')}</td>
                <td><span class="phase">${escapeHtml(phase)}</span></td>
                <td>${escapeHtml(String(attempt))}</td>
                <td>${escapeHtml(rootScheduled)}</td>
                <td>${escapeHtml(formatDate(nextFire))}</td>
                <td>${escapeHtml(timeRemaining)}</td>
            </tr>
        `;
    }).join('');
}

function renderError(message) {
    const summaryText = document.getElementById('summaryText');
    const tbody = document.getElementById('actionsTableBody');
    summaryText.textContent = 'Unable to load pending delayed actions.';
    tbody.innerHTML = `<tr><td colspan="7" class="error">${escapeHtml(message)}</td></tr>`;
}

function startAutoRefresh() {
    stopAutoRefresh();
    if (document.hidden) {
        return;
    }

    refreshTimerId = window.setInterval(() => {
        void loadPendingActions(true);
    }, AUTO_REFRESH_INTERVAL_MS);
}

function stopAutoRefresh() {
    if (refreshTimerId != null) {
        window.clearInterval(refreshTimerId);
        refreshTimerId = null;
    }
}

function onVisibilityChanged() {
    if (document.hidden) {
        stopAutoRefresh();
        return;
    }

    void loadPendingActions(true);
    startAutoRefresh();
}

function parseDate(value) {
    if (!value) {
        return null;
    }
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function parseEpoch(value) {
    const parsed = Number(value);
    if (!Number.isFinite(parsed) || parsed <= 0) {
        return '-';
    }
    return formatDate(new Date(parsed));
}

function formatDate(date) {
    if (!date) {
        return '-';
    }
    return date.toLocaleString();
}

function formatRemaining(nextFireDate) {
    if (!nextFireDate) {
        return '-';
    }

    const ms = nextFireDate.getTime() - Date.now();
    if (ms <= 0) {
        return 'due now';
    }

    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;

    if (minutes <= 0) {
        return `${seconds}s`;
    }

    return `${minutes}m ${seconds}s`;
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
