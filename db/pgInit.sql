CREATE TABLE "users" (
	"id" SERIAL PRIMARY KEY,
	"firstName" VARCHAR(20) NOT NULL,
	"lastName" VARCHAR(30) NOT NULL,
    "username" VARCHAR (80) UNIQUE NOT NULL,
    "password" VARCHAR (1000) NOT NULL,
    "securityLevel" int default 1 NOT NULL,
    "email" VARCHAR (80),
    "role" VARCHAR (80),
	"created" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "last_active" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "isActive" BOOLEAN default TRUE
);


CREATE TABLE "period" (
	"id" SERIAL PRIMARY KEY,
	"name" VARCHAR(20) NOT NULL,
	"startDate" TIMESTAMP WITH TIME ZONE,
	"endDate" TIMESTAMP WITH TIME ZONE,
	"users_id" INT NOT NULL REFERENCES "users"
);

CREATE TABLE "budget" (
 "id" SERIAL PRIMARY KEY,
 "name" VARCHAR (80) UNIQUE NOT NULL,
 "period_id" INT NOT NULL REFERENCES "period",
 "isClosed" BOOLEAN DEFAULT FALSE
);

CREATE TABLE "expenseCategory" (
  "id" SERIAL PRIMARY KEY,
  "name" VARCHAR (80) UNIQUE NOT NULL
);

CREATE TABLE "budget_expenseCategory" (
 "id" SERIAL PRIMARY KEY,
 "budget_id" INT NOT NULL REFERENCES "budget",
 "expenseCategory_id" INT NOT NULL REFERENCES "expenseCategory",
 "amountBudgeted" NUMERIC(12,2) DEFAULT 0.00,
 "amountPaid" NUMERIC(12,2) DEFAULT 0.00,
 "dueDate" TIMESTAMP WITH TIME ZONE,
 "datePaidInFull" TIMESTAMP WITH TIME ZONE
);

CREATE TABLE "account" (
  "id" SERIAL PRIMARY KEY,
  "name" VARCHAR (80) UNIQUE NOT NULL,
  "balance" NUMERIC(12,2),
  "availBalance" NUMERIC(12,2),
  "isCredit" BOOLEAN DEFAULT FALSE
);

CREATE TABLE "expenseItems" (
  "id" SERIAL PRIMARY KEY,
  "budget_expenseCategory_id" INT REFERENCES "budget_expenseCategory",
  "name" VARCHAR NOT NULL,
  "datePaid" TIMESTAMP WITH TIME ZONE,
  "isPaid" BOOLEAN,
  "account_id" INT NOT NULL REFERENCES "account"
);

CREATE TABLE "account_period" (
	"id" SERIAL PRIMARY KEY,
	"account_id" INT REFERNCES "account",
	"period_id" INT REFERNCES "period",
	"beginningBalance" NUMERIC(12,2),
	"endingBalance" NUMERIC(12,2)
);
