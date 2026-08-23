(function () {
    const exportForm = document.getElementById('export-form');
    const fromDateInput = document.getElementById('from-date');
    const toDateInput = document.getElementById('to-date');
    const startRangeExportButton = document.getElementById('start-range-export');
    const startAllExportButton = document.getElementById('start-all-export');
    const downloadCompletedButton = document.getElementById('download-completed');
    const statusMessage = document.getElementById('status-message');
    const activeJobMessage = document.getElementById('active-job');

    const JOB_STORAGE_KEY = 'journalPdfExportJobId';
    const JOB_POLL_INTERVAL_MS = 3000;

    let activeJobId = null;
    let pollingTimer = null;
    let activeDownloadable = false;

    exportForm.addEventListener('submit', async event => {
        event.preventDefault();
        await startExportJob(false);
    });

    startAllExportButton.addEventListener('click', async () => {
        fromDateInput.value = '';
        toDateInput.value = '';
        await startExportJob(true);
    });

    downloadCompletedButton.addEventListener('click', async () => {
        if (!activeJobId || !activeDownloadable) {
            setStatus('No completed export is ready to download yet.', '#c62828');
            return;
        }
        await downloadCompletedExport(activeJobId);
    });

    document.addEventListener('DOMContentLoaded', async () => {
        const savedJobId = window.localStorage.getItem(JOB_STORAGE_KEY);
        if (!savedJobId) {
            setStatus('Ready to start a new export.', '#555');
            setActiveJobDisplay(null);
            return;
        }

        activeJobId = savedJobId;
        setActiveJobDisplay(activeJobId);
        const state = await refreshJobStatus(activeJobId);
        if (state === 'PENDING' || state === 'RUNNING') {
            startPolling(activeJobId);
        }
    });

    function setStatus(message, color) {
        statusMessage.textContent = message;
        statusMessage.style.color = color;
    }

    function setActiveJobDisplay(jobId, state) {
        if (!jobId) {
            activeJobMessage.textContent = '';
            return;
        }

        const stateSuffix = state ? ' (' + state + ')' : '';
        activeJobMessage.textContent = 'Active export job: ' + jobId + stateSuffix;
    }

    function setActionButtonsEnabled(enabled) {
        startRangeExportButton.disabled = !enabled;
        startAllExportButton.disabled = !enabled;
    }

    function setDownloadReady(downloadable) {
        activeDownloadable = downloadable;
        downloadCompletedButton.disabled = !downloadable;
    }

    function buildStartJobUrl(fromDate, toDate) {
        const params = new URLSearchParams();
        if (fromDate) {
            params.set('from', fromDate);
        }
        if (toDate) {
            params.set('to', toDate);
        }

        const query = params.toString();
        return query ? '/api/journal/export/pdf/jobs?' + query : '/api/journal/export/pdf/jobs';
    }

    function buildStatusUrl(jobId) {
        return '/api/journal/export/pdf/jobs/' + encodeURIComponent(jobId);
    }

    function buildDownloadUrl(jobId) {
        return '/api/journal/export/pdf/jobs/' + encodeURIComponent(jobId) + '/download';
    }

    async function startExportJob(allExport) {
        const fromDate = fromDateInput.value;
        const toDate = toDateInput.value;
        if (fromDate && toDate && fromDate > toDate) {
            setStatus('"From" date must be before or equal to "To" date.', '#c62828');
            return;
        }

        setActionButtonsEnabled(false);
        setDownloadReady(false);
        const scopeLabel = allExport ? 'all journal entries' : 'selected range';
        setStatus('Starting export job for ' + scopeLabel + '...', '#1565c0');

        try {
            const response = await fetch(buildStartJobUrl(fromDate, toDate), { method: 'POST' });
            if (!response.ok) {
                throw new Error(await getErrorMessage(response));
            }

            const data = await response.json();
            activeJobId = data.jobId;
            window.localStorage.setItem(JOB_STORAGE_KEY, activeJobId);
            setActiveJobDisplay(activeJobId, data.state || 'PENDING');
            setStatus('Export job started. You can leave this page and come back later.', '#2e7d32');

            const state = await refreshJobStatus(activeJobId);
            if (state === 'PENDING' || state === 'RUNNING') {
                startPolling(activeJobId);
            }
        } catch (error) {
            window.appLogger?.error('Failed starting journal PDF export job', {
                error: error.message,
                fromDate,
                toDate
            });
            setStatus('Could not start export job: ' + error.message, '#c62828');
        } finally {
            setActionButtonsEnabled(true);
        }
    }

    function startPolling(jobId) {
        stopPolling();
        pollingTimer = window.setInterval(async () => {
            await refreshJobStatus(jobId);
        }, JOB_POLL_INTERVAL_MS);
    }

    function stopPolling() {
        if (pollingTimer !== null) {
            window.clearInterval(pollingTimer);
            pollingTimer = null;
        }
    }

    async function refreshJobStatus(jobId) {
        try {
            const response = await fetch(buildStatusUrl(jobId));
            if (response.status === 404) {
                window.localStorage.removeItem(JOB_STORAGE_KEY);
                if (activeJobId === jobId) {
                    activeJobId = null;
                }
                stopPolling();
                setDownloadReady(false);
                setActiveJobDisplay(null);
                setStatus('Saved export job was not found (server may have restarted).', '#c62828');
                return null;
            }
            if (!response.ok) {
                throw new Error(await getErrorMessage(response));
            }

            const status = await response.json();
            setActiveJobDisplay(jobId, status.state);
            applyJobStatus(status);
            return status.state || null;
        } catch (error) {
            window.appLogger?.warn('Failed polling journal export status', { jobId, error: error.message });
            setStatus('Could not refresh export status: ' + error.message, '#c62828');
            return null;
        }
    }

    function applyJobStatus(status) {
        if (!status || !status.state) {
            return;
        }

        if (status.state === 'PENDING' || status.state === 'RUNNING') {
            setDownloadReady(false);
            setStatus(status.message || 'Export in progress...', '#1565c0');
            return;
        }

        stopPolling();

        if (status.state === 'COMPLETED') {
            setDownloadReady(Boolean(status.downloadable));
            const fileNameText = status.fileName ? ' ' + status.fileName : '';
            setStatus('Export completed.' + fileNameText + ' Click "Download Completed PDF".', '#2e7d32');
            return;
        }

        if (status.state === 'FAILED') {
            setDownloadReady(false);
            setStatus('Export failed: ' + (status.message || 'Unknown error'), '#c62828');
            return;
        }

        setDownloadReady(false);
        setStatus(status.message || ('Export state: ' + status.state), '#555');
    }

    async function downloadCompletedExport(jobId) {
        setDownloadReady(false);
        setStatus('Downloading completed PDF...', '#1565c0');

        try {
            const response = await fetch(buildDownloadUrl(jobId), {
                method: 'GET',
                headers: { Accept: 'application/pdf' }
            });

            if (!response.ok) {
                throw new Error(await getErrorMessage(response));
            }

            const blob = await response.blob();
            const fileName = extractDownloadFilename(response.headers.get('Content-Disposition')) || 'journal-export.pdf';
            triggerDownload(fileName, blob);
            setStatus('Download started: ' + fileName, '#2e7d32');
        } catch (error) {
            window.appLogger?.error('Failed downloading completed journal export', { jobId, error: error.message });
            setStatus('Could not download completed export: ' + error.message, '#c62828');
        } finally {
            setDownloadReady(true);
        }
    }

    function triggerDownload(fileName, blob) {
        const downloadUrl = window.URL.createObjectURL(blob);
        try {
            const anchor = document.createElement('a');
            anchor.href = downloadUrl;
            anchor.download = fileName;
            document.body.appendChild(anchor);
            anchor.click();
            document.body.removeChild(anchor);
        } finally {
            window.URL.revokeObjectURL(downloadUrl);
        }
    }

    function extractDownloadFilename(contentDispositionHeader) {
        if (!contentDispositionHeader) {
            return null;
        }

        const filenameMatch = contentDispositionHeader.match(/filename\*?=(?:UTF-8''|")?([^\";]+)/i);
        if (!filenameMatch || filenameMatch.length < 2) {
            return null;
        }

        return decodeURIComponent(filenameMatch[1].replace(/"/g, '').trim());
    }

    async function getErrorMessage(response) {
        try {
            const data = await response.json();
            if (data) {
                if (typeof data.error === 'string' && data.error.trim()) {
                    return data.error.trim();
                }
                if (typeof data.detail === 'string' && data.detail.trim()) {
                    return data.detail.trim();
                }
                if (typeof data.message === 'string' && data.message.trim()) {
                    return data.message.trim();
                }
            }
        } catch (error) {
            window.appLogger?.warn('Failed to parse error response JSON', { error: error.message });
        }

        return 'HTTP ' + response.status;
    }
})();
