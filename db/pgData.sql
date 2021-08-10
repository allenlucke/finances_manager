
--Create Test Users
INSERT INTO "users"
    ("firstName", "lastName", "username", "password", "securityLevel", "email", "role")
VALUES
    ( 'dev', 'testUser', 'DEVTSTUSER', '1234', 99, 'devuser@gmail.com', 'DevAdmin'),
    ( 'test', 'user1', 'TSTUSER1', '1234', 1, 'tstuser1@gmail.com', 'Test User'),
    ( 'test', 'user2', 'TSTUSER2', '1234', 2, 'tstuser2@gmail.com', 'Test User');

--Create test periods for user id#1
INSERT INTO "period"
	("name", "startDate", "endDate", "users_id")
VALUES
	('May-2021', '05-01-2021', '05-31-2021', 1),
	('June-2021', '06-01-2021', '06-30-2021', 1),
	('July-2021', '07-01-2021', '07-31-2021', 1),
	('August-2021', '08-01-2021', '08-31-2021', 1),
	('September-2021', '09-01-2021', '09-30-2021', 1),
	('October-2021', '10-01-2021', '10-31-2021', 1),
	('November-2021', '11-01-2021', '11-30-2021', 1),
	('December-2021', '12-01-2021', '12-31-2021', 1);

--Create test categories for user id#1
INSERT INTO "expenseCategory"
	("name", "users_id")
VALUES
	('Electric Bill', 1),
	('Groceries', 1),
	('Day Care', 1),
	('Gas Bill', 1);

--Create test budgets for user id#1
INSERT INTO "budget"
	("name", "period_id")
VALUES
	(' May-21 Test Budget', 1 ),
	(' June-21 Test Budget', 2 ),
	(' July-21 Test Budget', 3 );

--Create test accounts for user id#1
INSERT INTO "account"
	("name", "users_id", "isCredit" )
VALUES
	('test Checking', 1, FALSE ),
	('test credit card',  1, TRUE ),
	('test savings account', 1, FALSE);

--Create test budget_expenseCategories for user id#1
INSERT INTO "budget_expenseCategory"
	("budget_id", "expenseCategory_id", "amountBudgeted" )
VALUES
	(1, 1, 250.00 ),
	(1, 2, 500.00 ),
	(1, 3, 1200.00 ),
	(1, 4, 100.00);

--Create test budget_expenseCategories for user id#1
INSERT INTO "budget_expenseCategory"
	("budget_id", "expenseCategory_id", "amountBudgeted" )
VALUES
	(2, 1, 250.00 ),
	(2, 2, 500.00 ),
	(2, 3, 1200.00 ),
	(2, 4, 100.00);

--Create test data expenseItem for user id#1
INSERT INTO "expenseItem"
	("budget_expenseCategory_id", "name", "transactionDate", "amount", "paymentToCreditAccount", "interestPaymentToCreditAccount", "paidWithCredit", "account_id", "users_id")
VALUES
	(1, 'Evergy Payment - May', '05/21/2021', 156.38, false, false, false, 1, 1 ),
	(2, 'Wal-Mart Groceries - May(1)', '05/22/2021', 131.08, false, false, false, 1, 1 ),
	(2, 'Wal-Mart Groceries - May(2)', '05/29/2021', 147.08, false, false, false, 1, 1 ),
	(3, 'Day Care (1) - May', '05/01/2021', 200, false, false, false, 1, 1 ),
	(3, 'Day Care (1) - May', '05/08/2021', 200, false, false, false, 1, 1 ),
	(4, 'KS Gas May', '05/04/2021', 87.95, false, false, false, 1, 1 );

--
--BELOW This line, may be unstable
--

--Initial period test data user id#1
INSERT INTO "accountPeriod"
	("account_id", "period_id",	"beginningBalance")
VALUES
	(1, 1, 10000.00);

INSERT INTO "accountPeriod"
	("account_id", "period_id")
VALUES
	(1, 2);

--incomeCat test data
INSERT INTO "incomeCategory"
	("name", "users_id")
VALUES
	('testUsser Paycheck', 1);

--budget_IncomeCat Test dat user id#1
INSERT INTO "budget_incomeCategory"
	("budget_id", "incomeCategory_id", "amountBudgeted")
VALUES
	(1, 1, 6500.00 );

--Income item test data
INSERT INTO "incomeItem"
	("budget_incomeCategory_id", "name", "recievedDate", "amountExpected", "amountRecieved","account_id", "users_id")
VALUES
	(1, 'May Pacheck -1', '05/02/2021', 1200.00, 1200.00, 1, 1),
	(1, 'May Pacheck -2', '05/16/2021', 1200.00, 1200.00, 1, 1);



--GET expenseItems by account/period
SELECT "expenseItem".id, "expenseItem"."transactionDate", "account".name AS "accountName" , "period".id AS "periodId",
"expenseItem".name AS "expenseItemName", "account".id AS "accountId", "expenseItem".amount FROM "expenseItem"
JOIN "account" ON "expenseItem"."account_id" = "account".id
JOIN "budget_expenseCategory" ON "expenseItem"."budget_expenseCategory_id" = "budget_expenseCategory".id
JOIN "budget" ON "budget_expenseCategory".id = "budget".id
JOIN "period" ON "budget".period_id = "period".id
JOIN "accountPeriod" ON "period".id = "accountPeriod".period_id
WHERE "expenseItem"."transactionDate" >= "period"."startDate" AND "expenseItem"."transactionDate" <= "period"."endDate"
AND "account".id =1
AND "period".id = 1
ORDER BY "expenseItem"."transactionDate";

--Get count of open periods prior to period's startdate
SELECT COUNT(*) FROM "period"
JOIN "budget" ON "period".id = "budget".period_id
WHERE "startDate" < '08/01/2021'
AND "budget"."isClosed" = 'false';

--Get startdate of last unclosed period
SELECT * FROM "period"
JOIN "budget" ON "period".id = "budget".period_id
WHERE "period"."startDate" < '08/01/2021'
AND "budget"."isClosed" = 'false'
ORDER BY "period"."startDate" ASC
LIMIT 1;
