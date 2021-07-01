
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
	('test savings account', 1, FALSE );

--Create test budget_expenseCategorys for user id#1
INSERT INTO "budget_expenseCategory"
	("budget_id", "expenseCategory_id", "amountBudgeted" )
VALUES
	(1, 1, 250.00 ),
	(1, 2, 500.00 ),
	(1, 3, 1200.00 ),
	(1, 4, 100.00);

--BELOW, STILL UNDER CONSTRUCTION

--Initial account tracker values
--Will not have incomeItem or expenseItem ID's,
--This is to simulate an existing account
INSERT INTO "accountTracker"
	("account_id", "beginningBalance", "endingBalance", "dateTime")
VALUES
	(1, 10000.00, 10000.00);

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
