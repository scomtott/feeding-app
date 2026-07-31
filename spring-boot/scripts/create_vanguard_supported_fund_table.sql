-- Stores supported Vanguard funds and request metadata in the database.
CREATE TABLE IF NOT EXISTS VanguardSupportedFundEntry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    displayName TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    startDateVariable TEXT NOT NULL DEFAULT 'startDate',
    endDateVariable TEXT NOT NULL DEFAULT 'endDate',
    operationName TEXT NOT NULL,
    query TEXT NOT NULL,
    variablesJson TEXT NOT NULL DEFAULT '{}',
    navItemsPath TEXT NOT NULL DEFAULT 'data.funds[0].pricingDetails.navPrices.items',
    marketGroupsPath TEXT NOT NULL DEFAULT 'data.funds[0].pricingDetails.marketPrices.items',
    marketItemsField TEXT NOT NULL DEFAULT 'items'
);

CREATE INDEX IF NOT EXISTS idx_vanguard_supported_fund_code
    ON VanguardSupportedFundEntry (code);

CREATE INDEX IF NOT EXISTS idx_vanguard_supported_fund_enabled
    ON VanguardSupportedFundEntry (enabled);
