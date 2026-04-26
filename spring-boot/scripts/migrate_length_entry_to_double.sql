-- Migration: Convert length_in_centimetres column from INTEGER to REAL (double precision)
-- This script handles the schema change without losing existing data

PRAGMA foreign_keys = OFF;

BEGIN TRANSACTION;

-- Create temporary table with new schema (REAL instead of INTEGER for length_in_centimetres)
CREATE TABLE length_entry_temp (
    id BIGINT PRIMARY KEY,
    date DATE NOT NULL,
    length_in_centimetres REAL NOT NULL,
    location VARCHAR(255) NOT NULL
);

-- Copy data from old table to temp table (values remain the same, just stored as REAL now)
INSERT INTO length_entry_temp (id, date, length_in_centimetres, location)
SELECT id, date, length_in_centimetres, location FROM length_entry;

-- Drop old table
DROP TABLE length_entry;

-- Rename temp table to original name
ALTER TABLE length_entry_temp RENAME TO length_entry;

-- Recreate indexes
CREATE INDEX idx_length_date ON length_entry(date);

COMMIT;

PRAGMA foreign_keys = ON;
