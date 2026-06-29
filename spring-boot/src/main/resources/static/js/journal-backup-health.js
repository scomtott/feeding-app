(function () {
    const checkButton = document.getElementById('check-health');
    const enabled = document.getElementById('enabled');
    const checkedAt = document.getElementById('checked-at');
    const credentialsPresent = document.getElementById('credentials-present');
    const tokenAcquired = document.getElementById('token-acquired');
    const driveReachable = document.getElementById('drive-reachable');
    const driveName = document.getElementById('drive-name');
    const message = document.getElementById('message');
    const bootstrapClientId = document.getElementById('bootstrap-client-id');
    const bootstrapTenantId = document.getElementById('bootstrap-tenant-id');
    const bootstrapRemoteRoot = document.getElementById('bootstrap-remote-root');
    const startBootstrapButton = document.getElementById('start-bootstrap');
    const pollBootstrapButton = document.getElementById('poll-bootstrap');
    const bootstrapUserCode = document.getElementById('bootstrap-user-code');
    const bootstrapVerificationUrl = document.getElementById('bootstrap-verification-url');
    const bootstrapExpires = document.getElementById('bootstrap-expires');
    const bootstrapStatus = document.getElementById('bootstrap-status');
    const bootstrapMessage = document.getElementById('bootstrap-message');
    const bootstrapHint = document.getElementById('bootstrap-hint');

    let bootstrapRequestId = sessionStorage.getItem('journalBackupBootstrapRequestId');
    let bootstrapPollTimer = null;

    checkButton.addEventListener('click', runHealthCheck);
    startBootstrapButton.addEventListener('click', startBootstrapFlow);
    pollBootstrapButton.addEventListener('click', pollBootstrapStatus);

    document.addEventListener('DOMContentLoaded', async () => {
        if (bootstrapRequestId) {
            bootstrapStatus.textContent = 'PENDING';
            bootstrapStatus.className = '';
            bootstrapMessage.textContent = 'Resuming pending bootstrap request. Click Check Authorization Status.';
            bootstrapMessage.className = '';
            pollBootstrapButton.disabled = false;
            bootstrapHint.textContent = 'A pending bootstrap request was restored for this browser session.';
        }
        await runHealthCheck();
    });

    async function runHealthCheck() {
        const previousText = checkButton.textContent;
        checkButton.disabled = true;
        checkButton.textContent = 'Checking...';
        message.textContent = 'Running health check...';
        message.className = '';

        try {
            const response = await fetch('/api/journal/backup/health');
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }

            const health = await response.json();
            renderHealth(health);
        } catch (error) {
            window.appLogger?.error('Backup health check failed', { error: error.message });
            message.textContent = 'Health check failed: ' + error.message;
            message.className = 'bad';
        } finally {
            checkButton.disabled = false;
            checkButton.textContent = previousText;
        }
    }

    async function startBootstrapFlow() {
        const clientId = bootstrapClientId.value.trim();
        const tenantId = bootstrapTenantId.value.trim() || 'consumers';
        const remoteRootFolder = bootstrapRemoteRoot.value.trim();

        if (!clientId) {
            bootstrapStatus.textContent = 'FAILED';
            bootstrapStatus.className = 'bad';
            bootstrapMessage.textContent = 'Client ID is required';
            bootstrapMessage.className = 'bad';
            return;
        }

        startBootstrapButton.disabled = true;
        bootstrapStatus.textContent = 'STARTING';
        bootstrapStatus.className = '';
        bootstrapMessage.textContent = 'Requesting device code...';
        bootstrapMessage.className = '';

        try {
            const response = await fetch('/api/journal/backup/bootstrap/device/start', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    clientId,
                    tenantId,
                    remoteRootFolder: remoteRootFolder || null
                })
            });

            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }

            const data = await response.json();
            bootstrapRequestId = data.requestId;
            sessionStorage.setItem('journalBackupBootstrapRequestId', bootstrapRequestId);
            bootstrapUserCode.textContent = data.userCode || '-';
            bootstrapVerificationUrl.innerHTML = data.verificationUriComplete
                ? `<a href="${escapeHtml(data.verificationUriComplete)}" target="_blank" rel="noopener noreferrer">${escapeHtml(data.verificationUri)}</a>`
                : escapeHtml(data.verificationUri || '-');
            bootstrapExpires.textContent = formatDateTime(data.expiresAt);
            bootstrapStatus.textContent = 'PENDING';
            bootstrapStatus.className = '';
            bootstrapMessage.textContent = data.message || 'Open the URL and enter code.';
            bootstrapMessage.className = '';
            pollBootstrapButton.disabled = false;
            bootstrapHint.textContent = 'Authorization request active. Use Check Authorization Status until completed.';

            if (bootstrapPollTimer) {
                clearInterval(bootstrapPollTimer);
            }
            const intervalMs = Math.max(3000, (data.pollIntervalSeconds || 5) * 1000);
            bootstrapPollTimer = setInterval(pollBootstrapStatus, intervalMs);
        } catch (error) {
            window.appLogger?.error('Bootstrap start failed', { error: error.message });
            bootstrapStatus.textContent = 'FAILED';
            bootstrapStatus.className = 'bad';
            bootstrapMessage.textContent = 'Bootstrap start failed: ' + error.message;
            bootstrapMessage.className = 'bad';
        } finally {
            startBootstrapButton.disabled = false;
        }
    }

    async function pollBootstrapStatus() {
        if (!bootstrapRequestId) {
            return;
        }

        pollBootstrapButton.disabled = true;
        try {
            const response = await fetch('/api/journal/backup/bootstrap/device/poll?requestId=' + encodeURIComponent(bootstrapRequestId), {
                method: 'POST'
            });
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }

            const data = await response.json();
            bootstrapStatus.textContent = data.status || 'UNKNOWN';

            if (data.status === 'COMPLETED') {
                bootstrapStatus.className = 'ok';
                bootstrapMessage.textContent = data.message || 'Bootstrap complete.';
                bootstrapMessage.className = 'ok';
                stopBootstrapPolling();
                await runHealthCheck();
            } else if (data.status === 'PENDING') {
                bootstrapStatus.className = '';
                bootstrapMessage.textContent = data.message || 'Waiting for authorization.';
                bootstrapMessage.className = '';
            } else {
                bootstrapStatus.className = 'bad';
                bootstrapMessage.textContent = data.message || 'Bootstrap failed.';
                bootstrapMessage.className = 'bad';
                stopBootstrapPolling();
            }
        } catch (error) {
            window.appLogger?.error('Bootstrap poll failed', { error: error.message });
            bootstrapStatus.textContent = 'FAILED';
            bootstrapStatus.className = 'bad';
            bootstrapMessage.textContent = 'Bootstrap poll failed: ' + error.message;
            bootstrapMessage.className = 'bad';
            stopBootstrapPolling();
        } finally {
            pollBootstrapButton.disabled = bootstrapRequestId == null;
        }
    }

    function stopBootstrapPolling() {
        if (bootstrapPollTimer) {
            clearInterval(bootstrapPollTimer);
            bootstrapPollTimer = null;
        }
        bootstrapRequestId = null;
        sessionStorage.removeItem('journalBackupBootstrapRequestId');
        pollBootstrapButton.disabled = true;
        bootstrapHint.textContent = 'No active bootstrap request. Start a new authorization to enable status polling.';
    }

    function renderHealth(health) {
        enabled.textContent = formatBoolean(health.enabled);
        enabled.className = health.enabled ? 'ok' : 'bad';

        checkedAt.textContent = formatDateTime(health.checkedAt);

        credentialsPresent.textContent = formatBoolean(health.credentialsPresent);
        credentialsPresent.className = health.credentialsPresent ? 'ok' : 'bad';

        tokenAcquired.textContent = formatBoolean(health.tokenAcquired);
        tokenAcquired.className = health.tokenAcquired ? 'ok' : 'bad';

        driveReachable.textContent = formatBoolean(health.driveReachable);
        driveReachable.className = health.driveReachable ? 'ok' : 'bad';

        driveName.textContent = health.driveName || '-';

        message.textContent = health.message || '-';
        message.className = health.driveReachable ? 'ok' : 'bad';
    }

    function formatBoolean(value) {
        return value ? 'Yes' : 'No';
    }

    function formatDateTime(value) {
        if (!value) {
            return '-';
        }

        const parsed = new Date(value);
        if (Number.isNaN(parsed.getTime())) {
            return '-';
        }

        return parsed.toLocaleString();
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }
})();
