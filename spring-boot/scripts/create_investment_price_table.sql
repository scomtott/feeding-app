-- Creates the investment price table used by the Vanguard ingestion service.
-- Keep in sync with InvestmentPriceEntry JPA mapping.
CREATE TABLE IF NOT EXISTS InvestmentPriceEntry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fundCode TEXT NOT NULL,
    fundName TEXT,
    provider TEXT,
    priceType TEXT NOT NULL,
    priceDate TEXT NOT NULL,
    currencyCode TEXT NOT NULL,
    price REAL NOT NULL,
    fetchedAt TEXT,
    CONSTRAINT uk_investment_price_fund_type_date_currency
        UNIQUE (fundCode, priceType, priceDate, currencyCode)
);

CREATE INDEX IF NOT EXISTS idx_investment_price_fund_date
    ON InvestmentPriceEntry (fundCode, priceDate);

CREATE INDEX IF NOT EXISTS idx_investment_price_date
    ON InvestmentPriceEntry (priceDate);
