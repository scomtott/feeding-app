const supportedFundsEndpoint = '/api/investments/vanguard/supported-funds';
const supportedFundsAllEndpoint = '/api/investments/vanguard/supported-funds/all';
const importDailyEndpoint = '/api/investments/vanguard/import-daily';
const importHistoricalEndpoint = '/api/investments/vanguard/import-historical';
const holdingsEndpoint = '/api/investments/holdings/units';
const pricesEndpoint = '/api/investments/prices';

let enabledFunds = [];

function setStatus(message, isError = false) {
    const banner = document.getElementById('statusBanner');
    banner.textContent = message;
    banner.style.background = isError ? 'rgba(120, 18, 18, 0.48)' : 'rgba(0, 0, 0, 0.24)';
}

function toJsonText(value) {
    return JSON.stringify(value, null, 2);
}

function todayIso() {
    return new Date().toISOString().split('T')[0];
}

async function requestJson(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        let details = '';
        try {
            details = await response.text();
        } catch {
            details = '';
        }
        throw new Error(`HTTP ${response.status}${details ? ` - ${details}` : ''}`);
    }
    if (response.status === 204) {
        return null;
    }
    return response.json();
}

function populateFundSelect(selectId, includeAllOption = false) {
    const select = document.getElementById(selectId);
    if (!select) {
        return;
    }

    const current = select.value;
    const options = [];

    if (includeAllOption) {
        options.push('<option value="">All enabled funds</option>');
    } else {
        options.push('<option value="" disabled selected>Select fund</option>');
    }

    for (const fund of enabledFunds) {
        options.push(`<option value="${fund.code}">${fund.code} - ${fund.displayName}</option>`);
    }

    select.innerHTML = options.join('');

    if (enabledFunds.some(fund => fund.code === current)) {
        select.value = current;
    }
}

function renderEnabledFundsPills() {
    const holder = document.getElementById('enabledFundsPills');
    if (!enabledFunds.length) {
        holder.innerHTML = '<span class="muted">No enabled supported funds yet.</span>';
        return;
    }

    holder.innerHTML = enabledFunds
        .map(fund => `<span class="pill">${fund.code}</span>`)
        .join('');
}

function setTableRows(tbodyId, rows, emptyColspan, emptyMessage) {
    const body = document.getElementById(tbodyId);
    if (!rows.length) {
        body.innerHTML = `<tr><td colspan="${emptyColspan}" class="muted">${emptyMessage}</td></tr>`;
        return;
    }
    body.innerHTML = rows.join('');
}

async function loadEnabledFunds() {
    enabledFunds = await requestJson(supportedFundsEndpoint);
    renderEnabledFundsPills();

    populateFundSelect('unitsFundCode', false);
    populateFundSelect('holdingsFundCode', true);
    populateFundSelect('pricesFundCode', true);
}

async function loadAllSupportedFunds() {
    const data = await requestJson(supportedFundsAllEndpoint);

    const rows = data.map(fund => {
        const enabledText = fund.enabled ? 'true' : 'false';
        const portIds = extractPortIds(fund.variablesJson).join(', ');
        return `
            <tr>
                <td>${fund.code}</td>
                <td>${fund.displayName}</td>
                <td>${fund.indexCode || ''}</td>
                <td>${enabledText}</td>
                <td>${portIds || '-'}</td>
                <td>
                    <div class="row-actions">
                        <button type="button" class="btn btn-secondary" onclick="prefillSupportedFundForm('${fund.code}')">Edit</button>
                        <button type="button" class="btn btn-danger" onclick="deleteSupportedFund('${fund.code}')">Delete</button>
                    </div>
                </td>
            </tr>
        `;
    });

    setTableRows('adminFundsTableBody', rows, 6, 'No supported funds found.');

    window.__allSupportedFunds = data;
}

function prefillSupportedFundForm(code) {
    const allFunds = window.__allSupportedFunds || [];
    const fund = allFunds.find(item => item.code === code);
    if (!fund) {
        return;
    }

    const variables = parseVariablesJson(fund.variablesJson);
    const portIds = Array.isArray(variables.portIds) ? variables.portIds : [];

    document.getElementById('adminIndexCode').value = fund.indexCode || '';
    document.getElementById('adminPortIds').value = portIds.join(', ');
    document.getElementById('adminStartDate').value = variables.startDate || '';
    document.getElementById('adminEndDate').value = variables.endDate || '';

    setStatus(`Loaded ${code} into admin form.`);
}

function parseVariablesJson(variablesJson) {
    if (!variablesJson) {
        return {};
    }

    try {
        const parsed = JSON.parse(variablesJson);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            return {};
        }
        return parsed;
    } catch {
        return {};
    }
}

function extractPortIds(variablesJson) {
    const parsed = parseVariablesJson(variablesJson);
    return Array.isArray(parsed.portIds) ? parsed.portIds : [];
}

async function deleteSupportedFund(code) {
    const shouldDelete = window.confirm(`Delete supported fund ${code}?`);
    if (!shouldDelete) {
        return;
    }

    try {
        await requestJson(`/api/investments/vanguard/supported-funds/${encodeURIComponent(code)}`, {
            method: 'DELETE'
        });

        setStatus(`Deleted supported fund ${code}.`);
        await refreshFundData();
    } catch (error) {
        setStatus(`Delete failed: ${error.message}`, true);
    }
}

async function refreshFundData() {
    await Promise.all([loadEnabledFunds(), loadAllSupportedFunds()]);
}

function buildQueryString(params) {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value !== null && value !== undefined && value !== '') {
            query.set(key, value);
        }
    });
    const encoded = query.toString();
    return encoded ? `?${encoded}` : '';
}

async function runDailyImport() {
    const result = await requestJson(importDailyEndpoint, {
        method: 'POST'
    });
    document.getElementById('importResult').textContent = toJsonText(result);
}

async function runHistoricalImport(event) {
    event.preventDefault();

    const startDate = document.getElementById('historyStartDate').value;
    const endDate = document.getElementById('historyEndDate').value;
    const codeText = document.getElementById('historyFundCodes').value;

    const codeList = codeText
        .split(',')
        .map(code => code.trim())
        .filter(Boolean);

    const payload = {
        startDate,
        endDate,
        fundCodes: codeList.length ? codeList : null
    };

    const result = await requestJson(importHistoricalEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });

    document.getElementById('importResult').textContent = toJsonText(result);
}

async function saveUnitsHeld(event) {
    event.preventDefault();

    const payload = {
        date: document.getElementById('unitsDate').value,
        fundCode: document.getElementById('unitsFundCode').value,
        unitsHeld: Number(document.getElementById('unitsHeld').value)
    };

    const result = await requestJson(holdingsEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });

    document.getElementById('unitsResult').textContent = toJsonText(result);
    setStatus(`Saved units for ${payload.fundCode} on ${payload.date}.`);
}

async function queryHoldings(event) {
    event.preventDefault();

    const query = buildQueryString({
        fundCode: document.getElementById('holdingsFundCode').value,
        startDate: document.getElementById('holdingsStartDate').value,
        endDate: document.getElementById('holdingsEndDate').value
    });

    const data = await requestJson(`${holdingsEndpoint}${query}`);

    const rows = data.map(item => `
        <tr>
            <td>${item.holdingDate || ''}</td>
            <td>${item.fundCode || ''}</td>
            <td>${Number(item.unitsHeld || 0).toLocaleString(undefined, { maximumFractionDigits: 6 })}</td>
            <td>${item.updatedAt || ''}</td>
        </tr>
    `);

    setTableRows('holdingsTableBody', rows, 4, 'No holdings found for filters.');
}

async function queryPrices(event) {
    event.preventDefault();

    const query = buildQueryString({
        fundCode: document.getElementById('pricesFundCode').value,
        priceType: document.getElementById('priceType').value,
        startDate: document.getElementById('pricesStartDate').value,
        endDate: document.getElementById('pricesEndDate').value
    });

    const data = await requestJson(`${pricesEndpoint}${query}`);

    const rows = data.map(item => `
        <tr>
            <td>${item.priceDate || ''}</td>
            <td>${item.fundCode || ''}</td>
            <td>${item.priceType || ''}</td>
            <td>${Number(item.price || 0).toLocaleString(undefined, { maximumFractionDigits: 4 })}</td>
            <td>${item.currencyCode || ''}</td>
        </tr>
    `);

    setTableRows('pricesTableBody', rows, 5, 'No prices found for filters.');
}

async function upsertSupportedFund(event) {
    event.preventDefault();

    const indexCode = document.getElementById('adminIndexCode').value.trim();
    if (!indexCode) {
        throw new Error('Index code is required.');
    }

    const portIds = document.getElementById('adminPortIds').value
        .split(',')
        .map(value => value.trim())
        .filter(Boolean);

    if (!portIds.length) {
        throw new Error('At least one port ID is required.');
    }

    const payload = {
        indexCode,
        startDate: document.getElementById('adminStartDate').value,
        endDate: document.getElementById('adminEndDate').value,
        portIds
    };

    await requestJson(supportedFundsEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });

    setStatus(`Saved supported fund using port IDs: ${portIds.join(', ')}.`);
    await refreshFundData();
}

function initializeDefaults() {
    const today = todayIso();
    document.getElementById('historyStartDate').value = today;
    document.getElementById('historyEndDate').value = today;
    document.getElementById('unitsDate').value = today;
    document.getElementById('adminStartDate').value = today;
    document.getElementById('adminEndDate').value = today;
}

async function initializePage() {
    try {
        initializeDefaults();

        document.getElementById('importDailyBtn').addEventListener('click', async () => {
            try {
                await runDailyImport();
                setStatus('Daily import completed.');
            } catch (error) {
                setStatus(`Daily import failed: ${error.message}`, true);
            }
        });

        document.getElementById('historicalImportForm').addEventListener('submit', async (event) => {
            try {
                await runHistoricalImport(event);
                setStatus('Historical import completed.');
            } catch (error) {
                setStatus(`Historical import failed: ${error.message}`, true);
            }
        });

        document.getElementById('unitsForm').addEventListener('submit', async (event) => {
            try {
                await saveUnitsHeld(event);
            } catch (error) {
                setStatus(`Save units failed: ${error.message}`, true);
            }
        });

        document.getElementById('holdingsQueryForm').addEventListener('submit', async (event) => {
            try {
                await queryHoldings(event);
                setStatus('Holdings loaded.');
            } catch (error) {
                setStatus(`Holdings query failed: ${error.message}`, true);
            }
        });

        document.getElementById('pricesQueryForm').addEventListener('submit', async (event) => {
            try {
                await queryPrices(event);
                setStatus('Prices loaded.');
            } catch (error) {
                setStatus(`Price query failed: ${error.message}`, true);
            }
        });

        document.getElementById('supportedFundForm').addEventListener('submit', async (event) => {
            try {
                await upsertSupportedFund(event);
            } catch (error) {
                setStatus(`Save supported fund failed: ${error.message}`, true);
            }
        });

        document.getElementById('refreshSupportedBtn').addEventListener('click', async () => {
            try {
                await loadEnabledFunds();
                setStatus('Enabled funds refreshed.');
            } catch (error) {
                setStatus(`Refresh failed: ${error.message}`, true);
            }
        });

        document.getElementById('refreshAdminBtn').addEventListener('click', async () => {
            try {
                await loadAllSupportedFunds();
                setStatus('Admin fund data refreshed.');
            } catch (error) {
                setStatus(`Admin refresh failed: ${error.message}`, true);
            }
        });

        window.prefillSupportedFundForm = prefillSupportedFundForm;
        window.deleteSupportedFund = deleteSupportedFund;

        await refreshFundData();
        setStatus('Investments dashboard ready.');
    } catch (error) {
        setStatus(`Failed to initialize page: ${error.message}`, true);
        window.appLogger?.error('Investment page initialization failed', { error: error.message });
    }
}

document.addEventListener('DOMContentLoaded', initializePage);
