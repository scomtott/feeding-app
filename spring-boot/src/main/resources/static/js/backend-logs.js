const DEFAULT_LAST_LOG_COUNT = 200;
const DEFAULT_WINDOW_HOURS = 2;
const MAX_QUERY_RESULTS = 1000;

document.addEventListener('DOMContentLoaded', () => {
    initializeControls();
    void loadLatestLogs(false);
    void loadStorageUsage();
});

function initializeControls() {
    const fromInput = document.getElementById('fromInput');
    const toInput = document.getElementById('toInput');
    const loadWindowButton = document.getElementById('loadWindowButton');
    const loadLatestButton = document.getElementById('loadLatestButton');
    const refreshStorageButton = document.getElementById('refreshStorageButton');
    const lastCountInput = document.getElementById('lastCountInput');
    const clearFiltersButton = document.getElementById('clearFiltersButton');

    setDefaultWindow(fromInput, toInput);
    lastCountInput.value = String(DEFAULT_LAST_LOG_COUNT);

    loadWindowButton.addEventListener('click', () => {
        void loadWindowLogs();
    });

    loadLatestButton.addEventListener('click', () => {
        void loadLatestLogs(true);
    });

    refreshStorageButton.addEventListener('click', () => {
        void loadStorageUsage();
    });

    clearFiltersButton.addEventListener('click', () => {
        clearFilters();
    });
}

async function loadWindowLogs() {
    const fromInput = document.getElementById('fromInput').value;
    const toInput = document.getElementById('toInput').value;

    if (!fromInput || !toInput) {
        setStatus('Select both "from" and "to" values.', true);
        return;
    }

    const fromDate = new Date(fromInput);
    const toDate = new Date(toInput);
    if (Number.isNaN(fromDate.getTime()) || Number.isNaN(toDate.getTime())) {
        setStatus('Invalid date/time values.', true);
        return;
    }

    if (fromDate.getTime() > toDate.getTime()) {
        setStatus('"From" date must be before "To" date.', true);
        return;
    }

    setStatus('Loading logs for selected time window...', false);
    try {
        const params = new URLSearchParams({
            from: fromDate.toISOString(),
            to: toDate.toISOString(),
            limit: String(MAX_QUERY_RESULTS)
        });
        appendActiveFilters(params, readActiveFilters());

        const response = await fetch('/api/backend-file-logs/window?' + params.toString());
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const payload = await response.json();
        renderLogResponse(payload, 'Time window results');
        setStatus(`Loaded ${payload.returnedCount} logs from selected window.`, false);
    } catch (error) {
        window.appLogger?.error('Failed to load time-window logs', { error: error.message });
        setStatus('Failed to load time-window logs: ' + error.message, true);
    }
}

async function loadLatestLogs(showStatus) {
    const countInput = document.getElementById('lastCountInput').value;
    const requestedCount = Number.parseInt(countInput, 10);
    if (!Number.isFinite(requestedCount) || requestedCount <= 0) {
        setStatus('Last X count must be a positive whole number.', true);
        return;
    }
    const effectiveRequestedCount = Math.min(requestedCount, MAX_QUERY_RESULTS);
    if (effectiveRequestedCount !== requestedCount) {
        document.getElementById('lastCountInput').value = String(effectiveRequestedCount);
    }

    if (showStatus) {
        setStatus('Loading latest logs...', false);
    }

    try {
        const params = new URLSearchParams({ count: String(effectiveRequestedCount) });
        appendActiveFilters(params, readActiveFilters());
        const response = await fetch('/api/backend-file-logs/latest?' + params.toString());
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const payload = await response.json();
        renderLogResponse(payload, `Last ${effectiveRequestedCount} logs`);
        if (showStatus) {
            setStatus(`Loaded ${payload.returnedCount} latest logs.`, false);
        }
    } catch (error) {
        window.appLogger?.error('Failed to load latest logs', { error: error.message });
        setStatus('Failed to load latest logs: ' + error.message, true);
    }
}

async function loadStorageUsage() {
    try {
        const response = await fetch('/api/backend-file-logs/storage');
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const payload = await response.json();
        renderStorage(payload.totalSizeDisplay, payload.totalBytes, payload.fileCount);
    } catch (error) {
        window.appLogger?.error('Failed to load log storage usage', { error: error.message });
        setStatus('Failed to refresh storage usage: ' + error.message, true);
    }
}

function renderLogResponse(payload, label) {
    const logs = Array.isArray(payload.logs) ? payload.logs : [];
    const summaryText = document.getElementById('summaryText');
    summaryText.textContent = `${label}: ${logs.length} logs shown.`;

    renderStorage(payload.totalSizeDisplay, payload.totalBytes, payload.fileCount);
    renderLogs(logs);
}

function renderStorage(totalSizeDisplay, totalBytes, fileCount) {
    const storageText = document.getElementById('storageText');
    storageText.textContent = `Storage usage: ${totalSizeDisplay} (${totalBytes} bytes) across ${fileCount} file${fileCount === 1 ? '' : 's'}.`;
}

function renderLogs(logs) {
    const tableBody = document.getElementById('logsTableBody');
    if (!logs.length) {
        tableBody.innerHTML = '<tr><td colspan="5" class="muted">No logs found.</td></tr>';
        return;
    }

    tableBody.innerHTML = logs.map((log) => {
        const timestamp = formatTimestamp(log.timestamp);
        const logLevel = escapeHtml(log.logLevel || 'INFO');
        const category = escapeHtml(log.category || 'general');
        const sourceClass = escapeHtml(log.sourceClass || 'unknown');
        const message = escapeHtml(log.message || '');
        return `
            <tr>
                <td>${timestamp}</td>
                <td>${logLevel}</td>
                <td>${category}</td>
                <td>${sourceClass}</td>
                <td class="message-cell">${message}</td>
            </tr>
        `;
    }).join('');
}

function readActiveFilters() {
    return {
        sourceClass: document.getElementById('sourceClassFilterInput').value.trim(),
        logLevel: document.getElementById('logLevelFilterSelect').value.trim(),
        category: document.getElementById('categoryFilterInput').value.trim()
    };
}

function appendActiveFilters(params, filters) {
    if (filters.sourceClass) {
        params.set('sourceClass', filters.sourceClass);
    }
    if (filters.logLevel) {
        params.set('logLevel', filters.logLevel);
    }
    if (filters.category) {
        params.set('category', filters.category);
    }
}

function clearFilters() {
    document.getElementById('sourceClassFilterInput').value = '';
    document.getElementById('logLevelFilterSelect').value = '';
    document.getElementById('categoryFilterInput').value = '';
    setStatus('Filters cleared.', false);
}

function setDefaultWindow(fromInput, toInput) {
    const now = new Date();
    const from = new Date(now.getTime() - (DEFAULT_WINDOW_HOURS * 60 * 60 * 1000));
    fromInput.value = toDateTimeLocalValue(from);
    toInput.value = toDateTimeLocalValue(now);
}

function toDateTimeLocalValue(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
}

function formatTimestamp(rawTimestamp) {
    if (!rawTimestamp) {
        return '-';
    }
    const parsed = new Date(rawTimestamp);
    if (Number.isNaN(parsed.getTime())) {
        return escapeHtml(String(rawTimestamp));
    }
    return escapeHtml(parsed.toLocaleString());
}

function setStatus(message, isError) {
    const statusMessage = document.getElementById('statusMessage');
    statusMessage.textContent = message;
    statusMessage.classList.toggle('error', Boolean(isError));
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
