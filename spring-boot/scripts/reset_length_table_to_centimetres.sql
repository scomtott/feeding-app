-- Recreate length_entry with centimetre-based schema.
-- WARNING: This script deletes all data in length_entry.

PRAGMA foreign_keys = OFF;
BEGIN TRANSACTION;

DROP TABLE IF EXISTS length_entry;

CREATE TABLE length_entry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date DATE,
    length_in_centimetres INTEGER NOT NULL,
    location VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_length_date ON length_entry(date);

COMMIT;
PRAGMA foreign_keys = ON;
