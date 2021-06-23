CREATE TABLE "users" (
	"id" SERIAL PRIMARY KEY,
	"firstName" VARCHAR(40) NOT NULL,
	"lastName" VARCHAR(60) NOT NULL,
	"username" VARCHAR (100) UNIQUE NOT NULL,
	"password" VARCHAR (100) NOT NULL,
	"securityLevel" INT DEFAULT 1 NOT NULL,
	"email" VARCHAR (120),
	"role" VARCHAR (60),
	"isActive" BOOLEAN default TRUE
);

CREATE TABLE "period" (
	"id" SERIAL PRIMARY KEY,
	"name" VARCHAR(80) NOT NULL,
	"startDate" TIMESTAMP WITH TIME ZONE,
	"endDate" TIMESTAMP WITH TIME ZONE,
	"users_id" INT NOT NULL REFERENCES "users"
);

CREATE TABLE "budget" (
	"id" SERIAL PRIMARY KEY,
	"name" VARCHAR (80) NOT NULL,
	"period_id" INT NOT NULL REFERENCES "period",
	"isClosed" BOOLEAN DEFAULT FALSE
);

CREATE TABLE "expenseCategory" (
	"id" SERIAL PRIMARY KEY,
	"name" VARCHAR (200) NOT NULL,
	"users_id" INT NOT NULL REFERENCES "users"
);

CREATE TABLE "budget_expenseCategory" (
	"id" SERIAL PRIMARY KEY,
	"budget_id" INT NOT NULL REFERENCES "budget",
	"expenseCategory_id" INT NOT NULL REFERENCES "expenseCategory",
	"amountBudgeted" NUMERIC(12,2) DEFAULT 0.00
);

CREATE TABLE "account" (
	"id" SERIAL PRIMARY KEY,
	"name" VARCHAR (120) NOT NULL,
	"users_id" INT NOT NULL REFERENCES "users",
	"isCredit" BOOLEAN DEFAULT FALSE
);

CREATE TABLE "expenseItem" (
	"id" SERIAL PRIMARY KEY,
	"budget_expenseCategory_id" INT REFERENCES "budget_expenseCategory",
	"name" VARCHAR (200) NOT NULL,
	"transactionDate" TIMESTAMP WITH TIME ZONE,
	"amount" NUMERIC(12,2) DEFAULT 0.00,
	"paidWithCredit" BOOLEAN DEFAULT FALSE,
	"paymentToCreditAccount" BOOLEAN DEFAULT FALSE,
	"interestPaymentToCreditAccount" BOOLEAN DEFAULT FALSE,
	"account_id" INT NOT NULL REFERENCES "account"
);

CREATE TABLE "incomeCategory" (
	"id" SERIAL PRIMARY KEY,
	"name" VARCHAR (200) NOT NULL,
	"users_id" INT NOT NULL REFERENCES "users"
);

CREATE TABLE "budget_incomeCategory" (
	"id" SERIAL PRIMARY KEY,
	"budget_id" INT NOT NULL REFERENCES "budget",
	"incomeCategory_id" INT NOT NULL REFERENCES "incomeCategory",
	"amountBudgeted" NUMERIC(12,2) DEFAULT 0.00
);

CREATE TABLE "incomeItem" (
	"id" SERIAL PRIMARY KEY,
	"budget_incomeCategory_id" INT REFERENCES "budget_incomeCategory",
	"name" VARCHAR (200) NOT NULL,
	"recievedDate" TIMESTAMP WITH TIME ZONE,
	"amountExpected" NUMERIC(12,2) DEFAULT 0.00,
	"amountRecieved" NUMERIC(12,2) DEFAULT 0.00,
	"account_id" INT NOT NULL REFERENCES "account"
);

CREATE TABLE "accountTracker" (
	"id" SERIAL PRIMARY KEY,
	"account_id" INT REFERENCES "account",
	"incomeItem_id" INT REFERENCES "incomeItem",
	"expenseItem_id" INT REFERENCES "expenseItem",
	"beginningBalance" NUMERIC(12,2),
	"endingBalance" NUMERIC(12,2),
	"dateTime" TIMESTAMP WITH TIME ZONE
);
