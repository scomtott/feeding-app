(function () {
    const md = window.markdownit({
        html: false,
        linkify: true,
        breaks: true
    });

    const state = {
        currentDate: null
    };

    const entryDateInput = document.getElementById('entry-date');
    const monthPicker = document.getElementById('month-picker');
    const markdownEditor = document.getElementById('markdown-editor');
    const renderedEntry = document.getElementById('rendered-entry');
    const statusMessage = document.getElementById('status-message');
    const monthIndexContainer = document.getElementById('month-index');
    const imageInput = document.getElementById('image-input');

    document.getElementById('load-today').addEventListener('click', () => setCurrentDate(toIsoDate(new Date())));
    document.getElementById('prev-day').addEventListener('click', () => stepDay(-1));
    document.getElementById('next-day').addEventListener('click', () => stepDay(1));
    document.getElementById('save-entry').addEventListener('click', saveCurrentEntry);
    document.getElementById('upload-image').addEventListener('click', uploadImageForCurrentDate);

    entryDateInput.addEventListener('change', async () => {
        await setCurrentDate(entryDateInput.value);
    });

    monthPicker.addEventListener('change', async () => {
        await loadMonthIndex();
    });

    document.addEventListener('DOMContentLoaded', async () => {
        const today = toIsoDate(new Date());
        await setCurrentDate(today);
        await loadMonthIndex();
    });

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
        return date.toISOString().slice(0, 10);
    }

    function stepDay(days) {
        if (!state.currentDate) {
            return;
        }

        const current = new Date(state.currentDate + 'T00:00:00');
        current.setDate(current.getDate() + days);
        setCurrentDate(toIsoDate(current));
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

        const image = imageInput.files?.[0];
        if (!image) {
            setStatus('Choose an image file first.', '#c62828');
            return;
        }

        const formData = new FormData();
        formData.append('date', state.currentDate);
        formData.append('image', image);

        try {
            const response = await fetch('/api/journal/images', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                const errorBody = await safeReadJson(response);
                throw new Error(errorBody?.error || ('Upload failed: HTTP ' + response.status));
            }

            const data = await response.json();
            insertAtCursor(markdownEditor, data.markdown + '\n');
            renderMarkdown(markdownEditor.value);
            imageInput.value = '';
            setStatus('Image uploaded and markdown link inserted.', '#2e7d32');
        } catch (error) {
            window.appLogger?.error('Failed uploading journal image', { error: error.message, date: state.currentDate });
            setStatus(error.message, '#c62828');
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
        renderedEntry.innerHTML = window.DOMPurify.sanitize(dirtyHtml, {
            USE_PROFILES: { html: true }
        });
    }

    function setStatus(message, color) {
        statusMessage.textContent = message;
        statusMessage.style.color = color;
    }

    async function safeReadJson(response) {
        try {
            return await response.json();
        } catch (error) {
            return null;
        }
    }
})();
