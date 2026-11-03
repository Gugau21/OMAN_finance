CREATE TABLE IF NOT EXISTS "Stock" (
	"id" serial NOT NULL UNIQUE,
	"symbol" varchar(16) NOT NULL UNIQUE,
	PRIMARY KEY ("id")
);