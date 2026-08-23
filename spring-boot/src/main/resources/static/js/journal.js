(function () {
    const md = window.markdownit({
        html: false,
        linkify: true,
        breaks: true
    });

    const state = {
        currentDate: null,
        mode: 'browse'
    };
    const MAX_MEDIA_UPLOAD_BYTES = 50 * 1024 * 1024;
    const ALLOWED_UPLOAD_MEDIA_TYPES = new Set([
        'image/jpeg',
        'image/png',
        'image/gif',
        'image/webp',
        'image/bmp',
        'video/mp4',
        'video/webm',
        'video/ogg',
        'video/quicktime',
        'video/x-m4v'
    ]);
    const VIDEO_FILE_EXTENSIONS = new Set(['.mp4', '.webm', '.ogg', '.ogv', '.mov', '.m4v']);

    const entryDateInput = document.getElementById('entry-date');
    const monthPicker = document.getElementById('month-picker');
    const markdownEditor = document.getElementById('markdown-editor');
    const renderedEntry = document.getElementById('rendered-entry');
    const statusMessage = document.getElementById('status-message');
    const monthIndexContainer = document.getElementById('month-index');
    const imageInput = document.getElementById('image-input');
    const previewSection = document.getElementById('preview-section');
    const editorSection = document.getElementById('editor-section');
    const browseModeButton = document.getElementById('browse-mode-button');
    const editModeButton = document.getElementById('edit-mode-button');
    const backupState = document.getElementById('backup-state');
    const backupLastRun = document.getElementById('backup-last-run');
    const backupLastSuccess = document.getElementById('backup-last-success');
    const backupMessage = document.getElementById('backup-message');
    const backupFilesScanned = document.getElementById('backup-files-scanned');
    const backupFilesUploaded = document.getElementById('backup-files-uploaded');
    const triggerBackupButton = document.getElementById('trigger-backup');
    const uploadImageButton = document.getElementById('upload-image');
    const dayStepButtons = document.querySelectorAll('.day-step-button');

    document.getElementById('load-today').addEventListener('click', () => setCurrentDate(toIsoDate(new Date())));
    document.getElementById('save-entry').addEventListener('click', saveCurrentEntry);
    uploadImageButton.addEventListener('click', uploadImageForCurrentDate);
    triggerBackupButton.addEventListener('click', triggerBackupNow);
    browseModeButton.addEventListener('click', () => setMode('browse'));
    editModeButton.addEventListener('click', () => setMode('edit'));
    dayStepButtons.forEach(button => {
        button.addEventListener('click', async () => {
            const dayStep = Number(button.dataset.dayStep);
            if (!Number.isInteger(dayStep) || dayStep === 0) {
                return;
            }

            await stepDay(dayStep);
        });
    });

    entryDateInput.addEventListener('change', async () => {
        await setCurrentDate(entryDateInput.value);
    });

    monthPicker.addEventListener('change', async () => {
        await loadMonthIndex();
    });

    document.addEventListener('DOMContentLoaded', async () => {
        setMode('browse');
        const today = toIsoDate(new Date());
        await setCurrentDate(today);
        await loadMonthIndex();
        await loadBackupStatus();
        window.setInterval(loadBackupStatus, 60000);
    });

    async function loadBackupStatus() {
        try {
            const response = await fetch('/api/journal/backup/status');
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }

            const data = await response.json();
            renderBackupStatus(data);
        } catch (error) {
            window.appLogger?.error('Failed loading backup status', { error: error.message });
            backupState.textContent = 'UNKNOWN';
            backupState.className = 'backup-state-failed';
            backupMessage.textContent = 'Could not load backup status';
        }
    }

    async function triggerBackupNow() {
        triggerBackupButton.disabled = true;
        backupMessage.textContent = 'Starting backup...';

        try {
            const response = await fetch('/api/journal/backup/trigger', {
                method: 'POST'
            });
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }

            const data = await response.json();
            renderBackupStatus(data);
            setTimeout(loadBackupStatus, 1200);
        } catch (error) {
            window.appLogger?.error('Failed triggering backup', { error: error.message });
            backupMessage.textContent = 'Could not trigger backup';
            backupState.textContent = 'FAILED';
            backupState.className = 'backup-state-failed';
        } finally {
            triggerBackupButton.disabled = false;
        }
    }

    function renderBackupStatus(status) {
        const state = status.state || 'UNKNOWN';
        backupState.textContent = state;
        backupState.className = mapBackupStateClass(state);

        backupLastRun.textContent = formatDateTime(status.lastRunAt);
        backupLastSuccess.textContent = formatDateTime(status.lastSuccessAt);
        backupMessage.textContent = status.message || '-';
        backupFilesScanned.textContent = String(status.filesScanned ?? 0);
        backupFilesUploaded.textContent = String(status.filesUploaded ?? 0);
    }

    function mapBackupStateClass(state) {
        switch (state) {
            case 'SUCCESS':
                return 'backup-state-success';
            case 'FAILED':
                return 'backup-state-failed';
            case 'RUNNING':
                return 'backup-state-running';
            case 'DISABLED':
                return 'backup-state-disabled';
            default:
                return '';
        }
    }

    function formatDateTime(value) {
        if (!value) {
            return '-';
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return '-';
        }

        return date.toLocaleString();
    }

    function setMode(mode) {
        state.mode = mode;

        const isBrowseMode = mode === 'browse';
        previewSection.classList.toggle('hidden-section', !isBrowseMode);
        editorSection.classList.toggle('hidden-section', isBrowseMode);

        browseModeButton.classList.toggle('active', isBrowseMode);
        editModeButton.classList.toggle('active', !isBrowseMode);
        browseModeButton.setAttribute('aria-pressed', String(isBrowseMode));
        editModeButton.setAttribute('aria-pressed', String(!isBrowseMode));
    }

    async function setCurrentDate(dateString) {
        if (!dateString) {
            return;
        }

        state.currentDate = dateString;
        entryDateInput.value = dateString;
        monthPicker.value = dateString.slice(0, 7);

        await loadEntry(dateString);
        await loadMonthIndex();
    }

    function toIsoDate(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return year + '-' + month + '-' + day;
    }

    async function stepDay(days) {
        if (!state.currentDate) {
            return;
        }

        const current = new Date(state.currentDate + 'T00:00:00');
        current.setDate(current.getDate() + days);
        await setCurrentDate(toIsoDate(current));
    }

    async function loadEntry(dateString) {
        try {
            const response = await fetch('/api/journal/entry?date=' + encodeURIComponent(dateString));
            if (!response.ok) {
                throw new Error('Failed to load entry: HTTP ' + response.status);
            }

            const data = await response.json();
            markdownEditor.value = data.markdown || '';
            renderMarkdown(markdownEditor.value);

            if (data.exists) {
                setStatus('Loaded entry for ' + dateString, '#2e7d32');
            } else {
                setStatus('No entry exists for ' + dateString + '. Start writing to create one.', '#555');
            }
        } catch (error) {
            window.appLogger?.error('Failed loading journal entry', { error: error.message, dateString });
            setStatus(error.message, '#c62828');
        }
    }

    async function saveCurrentEntry() {
        if (!state.currentDate) {
            setStatus('Choose a date first.', '#c62828');
            return;
        }

        try {
            const response = await fetch('/api/journal/entry?date=' + encodeURIComponent(state.currentDate), {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ markdown: markdownEditor.value })
            });

            if (!response.ok) {
                const errorBody = await safeReadJson(response);
                throw new Error(errorBody?.error || ('Failed to save: HTTP ' + response.status));
            }

            renderMarkdown(markdownEditor.value);
            setStatus('Saved entry for ' + state.currentDate, '#2e7d32');
            await loadMonthIndex();
        } catch (error) {
            window.appLogger?.error('Failed saving journal entry', { error: error.message, date: state.currentDate });
            setStatus(error.message, '#c62828');
        }
    }

    async function loadMonthIndex() {
        const monthValue = monthPicker.value;
        if (!monthValue) {
            monthIndexContainer.innerHTML = '<span class="empty-hint">Select a month.</span>';
            return;
        }

        const [year, month] = monthValue.split('-').map(Number);
        if (!year || !month) {
            monthIndexContainer.innerHTML = '<span class="empty-hint">Invalid month.</span>';
            return;
        }

        try {
            const response = await fetch('/api/journal/month?year=' + year + '&month=' + month);
            if (!response.ok) {
                throw new Error('Failed to load month index: HTTP ' + response.status);
            }

            const data = await response.json();
            const dates = data.datesWithEntries || [];

            if (dates.length === 0) {
                monthIndexContainer.innerHTML = '<span class="empty-hint">No entries for this month yet.</span>';
                return;
            }

            monthIndexContainer.innerHTML = '';
            dates.forEach(date => {
                const button = document.createElement('button');
                button.type = 'button';
                button.className = 'month-day';
                button.textContent = date;
                button.addEventListener('click', () => {
                    setCurrentDate(date);
                });
                monthIndexContainer.appendChild(button);
            });
        } catch (error) {
            window.appLogger?.error('Failed loading month index', { error: error.message, monthValue });
            monthIndexContainer.innerHTML = '<span class="empty-hint">Failed to load month entries.</span>';
            setStatus(error.message, '#c62828');
        }
    }

    async function uploadImageForCurrentDate() {
        if (!state.currentDate) {
            setStatus('Choose a date first.', '#c62828');
            return;
        }

        const mediaFiles = Array.from(imageInput.files || []);
        if (mediaFiles.length === 0) {
            setStatus('Choose one or more files first.', '#c62828');
            return;
        }

        for (const mediaFile of mediaFiles) {
            const validationError = getUploadValidationError(mediaFile);
            if (validationError) {
                setStatus('"' + getImageDisplayName(mediaFile) + '": ' + validationError, '#c62828');
                return;
            }
        }

        uploadImageButton.disabled = true;
        imageInput.disabled = true;

        try {
            for (let index = 0; index < mediaFiles.length; index += 1) {
                const mediaFile = mediaFiles[index];
                const imageName = getImageDisplayName(mediaFile);
                setStatus(
                    'Uploading file '
                        + (index + 1)
                        + ' of '
                        + mediaFiles.length
                        + ': "'
                        + imageName
                        + '" ('
                        + formatFileSize(mediaFile.size)
                        + ')...',
                    '#1565c0'
                );

                const formData = new FormData();
                formData.append('date', state.currentDate);
                formData.append('image', mediaFile);

                const response = await fetch('/api/journal/images', {
                    method: 'POST',
                    body: formData
                });

                if (!response.ok) {
                    const errorBody = await safeReadJson(response);
                    throw new Error('Failed on "' + imageName + '": ' + getUploadErrorMessage(response.status, errorBody));
                }
                const data = await response.json();
                insertAtCursor(markdownEditor, data.markdown + '\n');
            }

            renderMarkdown(markdownEditor.value);
            imageInput.value = '';
            if (mediaFiles.length === 1) {
                setStatus('File uploaded and markdown link inserted.', '#2e7d32');
            } else {
                setStatus(String(mediaFiles.length) + ' files uploaded and markdown links inserted in selection order.', '#2e7d32');
            }
        } catch (error) {
            window.appLogger?.error('Failed uploading journal media', { error: error.message, date: state.currentDate });
            setStatus(error.message, '#c62828');
        } finally {
            uploadImageButton.disabled = false;
            imageInput.disabled = false;
        }
    }

    function insertAtCursor(textarea, text) {
        const start = textarea.selectionStart || 0;
        const end = textarea.selectionEnd || 0;
        const before = textarea.value.slice(0, start);
        const after = textarea.value.slice(end);

        textarea.value = before + text + after;
        const cursor = start + text.length;
        textarea.setSelectionRange(cursor, cursor);
        textarea.focus();
    }

    function renderMarkdown(markdown) {
        if (!markdown || markdown.trim() === '') {
            renderedEntry.innerHTML = '<p class="empty-hint">No content to render for this day.</p>';
            return;
        }

        const dirtyHtml = md.render(markdown);
        const htmlWithVideoSupport = convertVideoLinksToPlayers(dirtyHtml);
        renderedEntry.innerHTML = window.DOMPurify.sanitize(htmlWithVideoSupport, {
            USE_PROFILES: { html: true },
            ADD_TAGS: ['video', 'source'],
            ADD_ATTR: ['controls', 'preload', 'playsinline', 'src', 'type']
        });
    }

    function setStatus(message, color) {
        statusMessage.textContent = message;
        statusMessage.style.color = color;
    }

    function getUploadValidationError(image) {
        if (!image || image.size === 0) {
            return 'File is empty.';
        }

        const imageType = (image.type || '').toLowerCase();
        if (!ALLOWED_UPLOAD_MEDIA_TYPES.has(imageType)) {
            return 'Unsupported file type. Use JPG, PNG, GIF, WEBP, BMP, MP4, WEBM, OGG, MOV, or M4V.';
        }

        if (image.size > MAX_MEDIA_UPLOAD_BYTES) {
            return 'File is too large (' + formatFileSize(image.size) + '). Max file size is 50MB.';
        }

        return null;
    }

    function getImageDisplayName(image) {
        if (!image) {
            return 'image';
        }

        return image.name || 'image';
    }

    function getUploadErrorMessage(status, errorBody) {
        if (errorBody?.error) {
            return errorBody.error;
        }

        if (status === 413) {
            return 'Upload failed: file is too large. Max file size is 50MB.';
        }

        if (status === 415 || status === 400) {
            return 'Upload failed: unsupported or invalid file. Use JPG, PNG, GIF, WEBP, BMP, MP4, WEBM, OGG, MOV, or M4V.';
        }

        return 'Upload failed: HTTP ' + status;
    }

    function formatFileSize(bytes) {
        if (!Number.isFinite(bytes) || bytes < 0) {
            return '0 B';
        }

        const mb = bytes / (1024 * 1024);
        if (mb >= 1) {
            return mb.toFixed(mb >= 10 ? 0 : 1) + ' MB';
        }

        const kb = bytes / 1024;
        if (kb >= 1) {
            return kb.toFixed(1) + ' KB';
        }

        return bytes + ' B';
    }

    async function safeReadJson(response) {
        try {
            return await response.json();
        } catch (error) {
            return null;
        }
    }

    function convertVideoLinksToPlayers(html) {
        const template = document.createElement('template');
        template.innerHTML = html;

        template.content.querySelectorAll('img[src]').forEach(imageElement => {
            const sourceUrl = imageElement.getAttribute('src');
            if (!isVideoUrl(sourceUrl)) {
                return;
            }

            imageElement.replaceWith(createVideoElement(sourceUrl));
        });

        template.content.querySelectorAll('a[href]').forEach(anchorElement => {
            const href = anchorElement.getAttribute('href');
            if (!isVideoUrl(href) || !href.includes('/journal-media/')) {
                return;
            }

            const container = document.createElement('div');
            container.className = 'journal-video-link';
            container.appendChild(createVideoElement(href));

            const link = document.createElement('a');
            link.href = href;
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
            link.textContent = anchorElement.textContent?.trim() || 'Open video in new tab';
            container.appendChild(link);

            anchorElement.replaceWith(container);
        });

        return template.innerHTML;
    }

    function createVideoElement(sourceUrl) {
        const videoElement = document.createElement('video');
        videoElement.controls = true;
        videoElement.preload = 'metadata';
        videoElement.playsInline = true;

        const sourceElement = document.createElement('source');
        sourceElement.src = sourceUrl;
        const contentType = getVideoContentTypeFromUrl(sourceUrl);
        if (contentType) {
            sourceElement.type = contentType;
        }
        videoElement.appendChild(sourceElement);
        return videoElement;
    }

    function isVideoUrl(url) {
        if (!url) {
            return false;
        }

        const normalizedUrl = url.split('#')[0].split('?')[0].toLowerCase();
        for (const extension of VIDEO_FILE_EXTENSIONS) {
            if (normalizedUrl.endsWith(extension)) {
                return true;
            }
        }

        return false;
    }

    function getVideoContentTypeFromUrl(url) {
        if (!url) {
            return null;
        }

        const normalizedUrl = url.split('#')[0].split('?')[0].toLowerCase();
        if (normalizedUrl.endsWith('.mp4') || normalizedUrl.endsWith('.m4v')) {
            return 'video/mp4';
        }
        if (normalizedUrl.endsWith('.webm')) {
            return 'video/webm';
        }
        if (normalizedUrl.endsWith('.ogg') || normalizedUrl.endsWith('.ogv')) {
            return 'video/ogg';
        }
        if (normalizedUrl.endsWith('.mov')) {
            return 'video/quicktime';
        }

        return null;
    }
})();
