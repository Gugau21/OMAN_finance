-- The stocks the application tracks: their ticker and company name.
-- The seed is the Dow Jones Industrial Average as of 2026-06-29 (Alphabet replaced Verizon).
CREATE TABLE stock (
	id serial PRIMARY KEY,
	symbol varchar(16) NOT NULL UNIQUE,
	name varchar(128) NOT NULL
);

INSERT INTO stock (symbol, name) VALUES
	('AAPL', 'Apple Inc.'),
	('AMGN', 'Amgen Inc.'),
	('AMZN', 'Amazon.com, Inc.'),
	('AXP', 'American Express Company'),
	('BA', 'The Boeing Company'),
	('CAT', 'Caterpillar Inc.'),
	('CRM', 'Salesforce, Inc.'),
	('CSCO', 'Cisco Systems, Inc.'),
	('CVX', 'Chevron Corporation'),
	('DIS', 'The Walt Disney Company'),
	('GOOGL', 'Alphabet Inc.'),
	('GS', 'The Goldman Sachs Group, Inc.'),
	('HD', 'The Home Depot, Inc.'),
	('HON', 'Honeywell International Inc.'),
	('IBM', 'International Business Machines Corporation'),
	('JNJ', 'Johnson & Johnson'),
	('JPM', 'JPMorgan Chase & Co.'),
	('KO', 'The Coca-Cola Company'),
	('MCD', 'McDonald''s Corporation'),
	('MMM', '3M Company'),
	('MRK', 'Merck & Co., Inc.'),
	('MSFT', 'Microsoft Corporation'),
	('NKE', 'NIKE, Inc.'),
	('NVDA', 'NVIDIA Corporation'),
	('PG', 'The Procter & Gamble Company'),
	('SHW', 'The Sherwin-Williams Company'),
	('TRV', 'The Travelers Companies, Inc.'),
	('UNH', 'UnitedHealth Group Incorporated'),
	('V', 'Visa Inc.'),
	('WMT', 'Walmart Inc.');
