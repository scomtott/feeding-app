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
        return `
            <tr>
                <td>${fund.code}</td>
                <td>${fund.displayName}</td>
                <td>${enabledText}</td>
                <td>${fund.operationName}</td>
                <td>
                    <div class="row-actions">
                        <button type="button" class="btn btn-secondary" onclick="prefillSupportedFundForm('${fund.code}')">Edit</button>
                        <button type="button" class="btn btn-danger" onclick="deleteSupportedFund('${fund.code}')">Delete</button>
                    </div>
                </td>
            </tr>
        `;
    });

    setTableRows('adminFundsTableBody', rows, 5, 'No supported funds found.');

    window.__allSupportedFunds = data;
}

function prefillSupportedFundForm(code) {
    const allFunds = window.__allSupportedFunds || [];
    const fund = allFunds.find(item => item.code === code);
    if (!fund) {
        return;
    }

    document.getElementById('fundCode').value = fund.code || '';
    document.getElementById('displayName').value = fund.displayName || '';
    document.getElementById('fundEnabled').value = fund.enabled ? 'true' : 'false';
    document.getElementById('operationName').value = fund.operationName || '';
    document.getElementById('startDateVariable').value = fund.startDateVariable || 'startDate';
    document.getElementById('endDateVariable').value = fund.endDateVariable || 'endDate';
    document.getElementById('navItemsPath').value = fund.navItemsPath || 'data.funds[0].pricingDetails.navPrices.items';
    document.getElementById('marketGroupsPath').value = fund.marketGroupsPath || 'data.funds[0].pricingDetails.marketPrices.items';
    document.getElementById('marketItemsField').value = fund.marketItemsField || 'items';
    document.getElementById('graphqlQuery').value = fund.query || '';
    document.getElementById('variablesJson').value = fund.variablesJson || '{}';

    setStatus(`Loaded ${code} into form for editing.`);
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

    let parsedVariables = {};
    const variablesText = document.getElementById('variablesJson').value.trim();

    if (variablesText) {
        try {
            parsedVariables = JSON.parse(variablesText);
            if (typeof parsedVariables !== 'object' || Array.isArray(parsedVariables)) {
                throw new Error('Variables must be a JSON object.');
            }
        } catch (error) {
            throw new Error(`Invalid variables JSON: ${error.message}`);
        }
    }

    const payload = {
        code: document.getElementById('fundCode').value.trim(),
        displayName: document.getElementById('displayName').value.trim(),
        enabled: document.getElementById('fundEnabled').value === 'true',
        startDateVariable: document.getElementById('startDateVariable').value.trim(),
        endDateVariable: document.getElementById('endDateVariable').value.trim(),
        operationName: document.getElementById('operationName').value.trim(),
        query: document.getElementById('graphqlQuery').value,
        variables: parsedVariables,
        navItemsPath: document.getElementById('navItemsPath').value.trim(),
        marketGroupsPath: document.getElementById('marketGroupsPath').value.trim(),
        marketItemsField: document.getElementById('marketItemsField').value.trim()
    };

    await requestJson(supportedFundsEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });

    setStatus(`Saved supported fund ${payload.code.toUpperCase()}.`);
    await refreshFundData();
}

function initializeDefaults() {
    const today = todayIso();
    document.getElementById('historyStartDate').value = today;
    document.getElementById('historyEndDate').value = today;
    document.getElementById('unitsDate').value = today;

    const sampleQuery = 'query PriceDetailsQuery($portIds: [String!]!, $startDate: String!, $endDate: String!, $limit: Float) { funds(portIds: $portIds) { pricingDetails { navPrices(startDate: $startDate, endDate: $endDate, limit: $limit) { items { price asOfDate currencyCode } } marketPrices(startDate: $startDate, endDate: $endDate, limit: $limit) { items { portId items { price asOfDate currencyCode } } } } } }';

    document.getElementById('graphqlQuery').value = sampleQuery;
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
