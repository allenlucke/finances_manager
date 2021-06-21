
INSERT INTO "users"
    ("firstName", "lastName", "username", "password", "securityLevel", "email", "role")
VALUES
    ( 'dev', 'user', 'DEVUSER', '1234', 99, 'devuser@gmail.com', 'DevAdmin'),
    ( 'test', 'user1', 'TSTUSER1', '1234', 1, 'tstuser1@gmail.com', 'Test User'),
	( 'test', 'user2', 'TSTUSER2', '1234', 2, 'tstuser2@gmail.com', 'Test User');

INSERT INTO "period"
	("name", "startDate", "endDate", "users_id")
VALUES
	('May-2021', '05-01-2021', '05-31-2021', 1),
	('June-2021', '06-01-2021', '06-30-2021', 1),
	('July-2021', '07-01-2021', '07-31-2021', 1);

INSERT INTO "expenseCategory"
	("name")
VALUES
	('Electric Bill'),
	('Groceries'),
	('Day Care');

INSERT INTO "budget"
	("name", "period_id")
VALUES
	(' May-21 Test Budget', 1 );

INSERT INTO "account"
	("name", "balance", "availBalance", "isCredit")
VALUES
	('checking', 10000.00, 9879.32, false);

INSERT INTO "account"
	("name", "balance", "availBalance", "isCredit")
VALUES
	('credit card', 0, 0, true);

INSERT INTO "budget_expenseCategory"
	("budget_id", "expenseCategory_id", "amountBudgeted", "dueDate")
VALUES
	(1, 1, 250.00, '05-31-2021'),
	(1, 2, 500.00, '05-31-2021'),
	(1, 3, 1200.00, '05-31-2021');
