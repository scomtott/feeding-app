-- Creates the investment units table used to store holdings over time.
CREATE TABLE IF NOT EXISTS InvestmentUnitsEntry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    holdingDate TEXT NOT NULL,
    fundCode TEXT NOT NULL,
    fundName TEXT,
    unitsHeld REAL NOT NULL,
    updatedAt TEXT,
    CONSTRAINT uk_investment_units_fund_date UNIQUE (fundCode, holdingDate)
);

CREATE INDEX IF NOT EXISTS idx_investment_units_fund_date
    ON InvestmentUnitsEntry (fundCode, holdingDate);

CREATE INDEX IF NOT EXISTS idx_investment_units_date
    ON InvestmentUnitsEntry (holdingDate);
