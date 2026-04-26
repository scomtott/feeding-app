const AUTO_REFRESH_INTERVAL_MS = 15000;

let refreshTimerId = null;
let dashboardLoadInFlight = false;
let scheduleGraphInitialized = false;

document.addEventListener('DOMContentLoaded', () => {
    const reloadButton = document.getElementById('reloadButton');
    reloadButton.addEventListener('click', () => loadDashboard(false));

    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('beforeunload', stopAutoRefresh);

    loadDashboard(false);
    startAutoRefresh();
});

async function loadDashboard(isBackgroundRefresh = false) {
    if (dashboardLoadInFlight) {
        return;
    }

    const status = document.getElementById('statusMessage');
    if (!isBackgroundRefresh) {
        status.textContent = '';
        status.classList.remove('error');
    }

    dashboardLoadInFlight = true;

    try {
        const response = await fetch('/api/homeassistant/lights/brightness-dashboard');
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const data = await response.json();
        renderCurrentSchedule(data.current, data.timezone);
        if (!scheduleGraphInitialized) {
            renderScheduleGraph(data.schedulePoints || [], data.current);
            scheduleGraphInitialized = true;
        }
        renderScheduleAnchors(data.scheduleAnchors || []);
        renderOverrideLeases(data.manualOverrideLeases || []);
        renderLights(data.lights || []);

        if (isBackgroundRefresh && !status.classList.contains('error')) {
            status.textContent = `Auto-refreshed at ${new Date().toLocaleTimeString()}`;
        }
    } catch (error) {
        window.appLogger?.error('Failed to load brightness dashboard', { error: error.message });
        status.textContent = `Failed to load dashboard: ${error.message}`;
        status.classList.add('error');
    } finally {
        dashboardLoadInFlight = false;
    }
}

function renderScheduleGraph(points, current) {
    const line = document.getElementById('scheduleLine');
    const currentPoint = document.getElementById('currentPoint');
    const grid = document.getElementById('scheduleGrid');
    const legend = document.getElementById('graphLegend');

    if (!Array.isArray(points) || points.length === 0) {
        line.setAttribute('points', '');
        currentPoint.setAttribute('r', '0');
        grid.innerHTML = '';
        legend.textContent = 'No schedule graph data available';
        return;
    }

    const width = 1000;
    const height = 280;
    const paddingLeft = 44;
    const paddingRight = 20;
    const paddingTop = 16;
    const paddingBottom = 26;
    const plotWidth = width - paddingLeft - paddingRight;
    const plotHeight = height - paddingTop - paddingBottom;

    const maxSecond = 24 * 60 * 60 - 1;
    const maxBrightness = 255;

    grid.innerHTML = buildGraphGrid(paddingLeft, paddingTop, plotWidth, plotHeight);

    const pointString = points.map(point => {
        const x = paddingLeft + (Math.max(0, Math.min(maxSecond, Number(point.secondOfDay) || 0)) / maxSecond) * plotWidth;
        const y = paddingTop + (1 - (Math.max(0, Math.min(maxBrightness, Number(point.brightness) || 0)) / maxBrightness)) * plotHeight;
        return `${x.toFixed(2)},${y.toFixed(2)}`;
    }).join(' ');
    line.setAttribute('points', pointString);

    const currentSecondOfDay = Number(current?.currentSecondOfDay);
    const currentBrightness = Number(current?.targetBrightness);
    if (Number.isFinite(currentSecondOfDay) && Number.isFinite(currentBrightness)) {
        const cx = paddingLeft + (Math.max(0, Math.min(maxSecond, currentSecondOfDay)) / maxSecond) * plotWidth;
        const cy = paddingTop + (1 - (Math.max(0, Math.min(maxBrightness, currentBrightness)) / maxBrightness)) * plotHeight;
        currentPoint.setAttribute('cx', cx.toFixed(2));
        currentPoint.setAttribute('cy', cy.toFixed(2));
        currentPoint.setAttribute('r', '6');
        legend.textContent = `Current point: ${formatTimeOfDay(currentSecondOfDay)} at brightness ${Math.round(currentBrightness)}`;
    } else {
        currentPoint.setAttribute('r', '0');
        legend.textContent = 'Current point unavailable';
    }
}

function buildGraphGrid(paddingLeft, paddingTop, plotWidth, plotHeight) {
    const horizontal = [0, 0.25, 0.5, 0.75, 1.0].map(ratio => {
        const y = paddingTop + ratio * plotHeight;
        return `<line class="graph-grid-line" x1="${paddingLeft}" y1="${y}" x2="${paddingLeft + plotWidth}" y2="${y}"></line>`;
    });

    const vertical = [0, 6, 12, 18, 24].map(hour => {
        const x = paddingLeft + (hour / 24) * plotWidth;
        return `<line class="graph-grid-line" x1="${x}" y1="${paddingTop}" x2="${x}" y2="${paddingTop + plotHeight}"></line>`;
    });

    return [...horizontal, ...vertical].join('');
}

function formatTimeOfDay(secondOfDay) {
    const total = Math.max(0, Math.min(24 * 60 * 60 - 1, Math.floor(secondOfDay)));
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
}

function startAutoRefresh() {
    stopAutoRefresh();
    if (document.hidden) {
        return;
    }

    refreshTimerId = window.setInterval(() => {
        void loadDashboard(true);
    }, AUTO_REFRESH_INTERVAL_MS);
}

function stopAutoRefresh() {
    if (refreshTimerId != null) {
        window.clearInterval(refreshTimerId);
        refreshTimerId = null;
    }
}

function handleVisibilityChange() {
    if (document.hidden) {
        stopAutoRefresh();
        return;
    }

    void loadDashboard(true);
    startAutoRefresh();
}

function renderCurrentSchedule(current, timezone) {
    const container = document.getElementById('currentSchedule');
    const progressBar = document.getElementById('phaseProgressBar');

    if (!current) {
        container.innerHTML = '<div class="muted">No schedule state available</div>';
        progressBar.style.width = '0%';
        return;
    }

    const progressPercent = Math.max(0, Math.min(100, Math.round((current.phaseProgress || 0) * 100)));
    progressBar.style.width = `${progressPercent}%`;

    container.innerHTML = `
        <div class="kpi">
            <div class="kpi-label">Current Time</div>
            <div class="kpi-value">${escapeHtml(current.currentTime || '-')}</div>
        </div>
        <div class="kpi">
            <div class="kpi-label">Timezone</div>
            <div class="kpi-value">${escapeHtml(timezone || '-')}</div>
        </div>
        <div class="kpi">
            <div class="kpi-label">Schedule Phase</div>
            <div class="kpi-value">${escapeHtml(current.phase || '-')}</div>
        </div>
        <div class="kpi">
            <div class="kpi-label">Target Brightness</div>
            <div class="kpi-value">${escapeHtml(String(current.targetBrightness ?? '-'))}</div>
        </div>
        <div class="kpi">
            <div class="kpi-label">Phase Progress</div>
            <div class="kpi-value">${progressPercent}%</div>
        </div>
    `;
}

function renderScheduleAnchors(anchors) {
    const tbody = document.getElementById('scheduleTableBody');
    if (!anchors.length) {
        tbody.innerHTML = '<tr><td colspan="3" class="muted">No schedule anchors found</td></tr>';
        return;
    }

    tbody.innerHTML = anchors.map(anchor => `
        <tr>
            <td>${escapeHtml(anchor.time || '-')}</td>
            <td>${escapeHtml(anchor.label || '-')}</td>
            <td>${escapeHtml(String(anchor.brightness ?? '-'))}</td>
        </tr>
    `).join('');
}

function renderOverrideLeases(leases) {
    const tbody = document.getElementById('overrideTableBody');
    if (!leases.length) {
        tbody.innerHTML = '<tr><td colspan="4" class="muted">No active override leases</td></tr>';
        return;
    }

    tbody.innerHTML = leases.map(lease => `
        <tr>
            <td>${escapeHtml(lease.friendlyName || '-')}</td>
            <td>${escapeHtml(lease.entityId || '-')}</td>
            <td>${formatTimestamp(lease.overrideUntil)}</td>
            <td>
                <button type="button" class="secondary" onclick="clearOverrideLease('${encodeForOnClick(lease.entityId)}')">Clear Lease</button>
            </td>
        </tr>
    `).join('');
}

function renderLights(lights) {
    const tbody = document.getElementById('lightsTableBody');
    if (!lights.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="muted">No lights found</td></tr>';
        return;
    }

    tbody.innerHTML = lights.map(light => {
        const stateClass = String(light.state).toLowerCase() === 'on' ? 'state-on' : 'state-off';
        const excludedBadge = light.excluded ? '<span class="badge badge-yes">Yes</span>' : '<span class="badge badge-no">No</span>';
        const hasOverride = !!light.manualOverrideUntil;
        const overrideText = hasOverride ? formatTimestamp(light.manualOverrideUntil) : '-';
        const clearButton = hasOverride
            ? `<button type="button" class="secondary" onclick="clearOverrideLease('${encodeForOnClick(light.entityId)}')">Clear Lease</button>`
            : '';

        return `
            <tr>
                <td>${escapeHtml(light.friendlyName || '-')}</td>
                <td>${escapeHtml(light.entityId || '-')}</td>
                <td class="${stateClass}">${escapeHtml(light.state || '-')}</td>
                <td>${escapeHtml(String(light.brightness ?? '-'))}</td>
                <td>${escapeHtml(light.lightKind || '-')}</td>
                <td>${excludedBadge}</td>
                <td>${overrideText}</td>
                <td>${clearButton}</td>
            </tr>
        `;
    }).join('');
}

async function clearOverrideLease(entityId) {
    if (!entityId) {
        return;
    }

    const status = document.getElementById('statusMessage');
    status.classList.remove('error');

    try {
        const response = await fetch(`/api/homeassistant/lights/manual-override/clear?entity_id=${encodeURIComponent(entityId)}`, {
            method: 'POST'
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        status.textContent = `Cleared manual override lease for ${entityId}`;
        await loadDashboard(false);
    } catch (error) {
        window.appLogger?.error('Failed to clear manual override lease', { entityId, error: error.message });
        status.textContent = `Failed to clear override lease for ${entityId}: ${error.message}`;
        status.classList.add('error');
    }
}

function formatTimestamp(value) {
    if (!value) {
        return '-';
    }
    try {
        return new Date(value).toLocaleString();
    } catch (_) {
        return escapeHtml(String(value));
    }
}

function encodeForOnClick(text) {
    return String(text || '').replace(/'/g, "\\'");
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
