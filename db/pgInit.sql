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
	"startDate" TIMESTAMP WITH TIME ZONE NOT NULL,
	"endDate" TIMESTAMP WITH TIME ZONE NOT NULL,
	"users_id" INT NOT NULL REFERENCES "users"
);

CREATE TABLE "budget" (
	"id" SERIAL PRIMARY KEY,
	"name" VARCHAR (80) NOT NULL,
	"period_id" INT NOT NULL REFERENCES "period",
	"isClosed" BOOLEAN DEFAULT FALSE,
	"users_id" INT NOT NULL REFERENCES "users"
);

CREATE TABLE "expenseCategory" (
	"id" SERIAL PRIMARY KEY,
	"name" VARCHAR (200) NOT NULL,
	"users_id" INT NOT NULL REFERENCES "users"
);

--Junction table used to represent the one to many relationship
--from expenseCategory - to - budget
CREATE TABLE "budget_expenseCategory" (
	"id" SERIAL PRIMARY KEY,
	"budget_id" INT NOT NULL REFERENCES "budget",
	"expenseCategory_id" INT NOT NULL REFERENCES "expenseCategory",
	"amountBudgeted" NUMERIC(12,2) DEFAULT 0.00,
	"users_id" INT NOT NULL REFERENCES "users"
);

CREATE TABLE "account" (
	"id" SERIAL PRIMARY KEY,
	"name" VARCHAR (120) NOT NULL,
	"users_id" INT NOT NULL REFERENCES "users",
	"isCredit" BOOLEAN DEFAULT FALSE,
	"isActive" BOOLEAN DEFAULT TRUE,
);

CREATE TABLE "expenseItem" (
	"id" SERIAL PRIMARY KEY,
	--budget_expenseCategory_id can be nullable,
	--typically use case scenario of a null value
	--would be a payment made towards a credit card  balance
	"budget_expenseCategory_id" INT REFERENCES "budget_expenseCategory",
	"name" VARCHAR (200) NOT NULL,
	"transactionDate" TIMESTAMP WITH TIME ZONE NOT NULL,
	"amount" NUMERIC(12,2) DEFAULT 0.00,
	--Denotes item was paid by credit card
	"paidWithCredit" BOOLEAN DEFAULT FALSE,
	--Denotes Item was paymnet on a credit incomeCategory_id
	--These items Should NOT have a "budget_expenseCategory_id"
	--As that was assigned at the time of the purchase,
	--and doing so would essentially double the expenditure
	--from the standpoint of the budget allocation
	"paymentToCreditAccount" BOOLEAN DEFAULT FALSE,
	--Denotes interest payment on a credit card,
	--This should be allocated to the period in
	-- which the card was paid off
	"interestPaymentToCreditAccount" BOOLEAN DEFAULT FALSE,
	"account_id" INT NOT NULL REFERENCES "account",
	"users_id" INT NOT NULL REFERENCES "users"
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
	"amountBudgeted" NUMERIC(12,2) DEFAULT 0.00,
	"users_id" INT NOT NULL REFERENCES "users"
);

CREATE TABLE "incomeItem" (
	"id" SERIAL PRIMARY KEY,
	"budget_incomeCategory_id" INT NOT NULL REFERENCES "budget_incomeCategory",
	"name" VARCHAR (200) NOT NULL,
	"receivedDate" TIMESTAMP WITH TIME ZONE,
	"amountExpected" NUMERIC(12,2) DEFAULT 0.00,
	"amountReceived" NUMERIC(12,2) DEFAULT 0.00,
	"account_id" INT NOT NULL REFERENCES "account",
	"users_id" INT NOT NULL REFERENCES "users"
);


CREATE TABLE "accountPeriod" (
	"id" SERIAL PRIMARY KEY,
	"account_id" INT NOT NULL REFERENCES "account",
	"period_id" INT NOT NULL REFERENCES "period",
	"beginningBalance" NUMERIC(12,2),
	"endingBalance" NUMERIC(12,2),
	"users_id" INT NOT NULL REFERENCES "users"
);
