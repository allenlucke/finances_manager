
--Post user    
INSERT INTO users(first_name, last_name, user_name, password, sec_lvl, email) 
    VALUES('allen', 'lucke', 'allenlucke', 'password', 100, 'allenlucke@gmail.com');
INSERT INTO users(first_name, last_name, user_name, password, sec_lvl, email) 
    VALUES('test', 'user', 'allenlucke', 'password', 1, 'allenlucke@gmail.com');
    
--POST Income
INSERT INTO income(name, due_on, recurring, amount_expected, users_id, category_id) 
    VALUES('martest1', '01-MAR-2021', 1, 80.60, 1, 1 );
INSERT INTO income(name, due_on, recurring, amount_expected, users_id, category_id) 
    VALUES('martest2', '01-MAR-2021', 1, 40.60, 1, 2 );
INSERT INTO income(name, due_on, recurring, amount_expected, users_id, category_id) 
    VALUES('aprtest1', '01-APR-2021', 1, 50.60, 1, 3 );
INSERT INTO income(name, due_on, recurring, amount_expected, users_id, category_id) 
    VALUES('aprtest2', '01-APR-2021', 1, 60.60, 1, 4 );
    
INSERT INTO income(name, due_on, recurring, amount_expected, users_id, category_id) 
    VALUES('martest3', '01-MAR-2021', 1, 80.60, 21, 21 );
INSERT INTO income(name, due_on, recurring, amount_expected, users_id, category_id) 
    VALUES('martest4', '01-MAR-2021', 1, 40.60, 21, 22 );
INSERT INTO income(name, due_on, recurring, amount_expected, users_id, category_id) 
    VALUES('aprtest3', '01-APR-2021', 1, 50.60, 21, 23 );
INSERT INTO income(name, due_on, recurring, amount_expected, users_id, category_id) 
    VALUES('aprtest4', '01-APR-2021', 1, 60.60, 21, 24 );
    
--Post Income_Categories
INSERT INTO income_categories(name, due_by, amount_due, users_id,  period_id)
	VALUES('mar category', '24-MAR-2021', 521.67, 1, 1);
INSERT INTO income_categories(name, due_by, amount_due, users_id,  period_id)
	VALUES('mar category2', '24-MAR-2021', 821.67, 1, 1);
INSERT INTO income_categories(name, due_by, amount_due, users_id,  period_id)
	VALUES('apr category', '24-APR-2021', 521.67, 1, 2);
INSERT INTO income_categories(name, due_by, amount_due, users_id,  period_id)
	VALUES('apr category2', '24-APR-2021', 821.67, 1, 2);
	
INSERT INTO income_categories(name, due_by, amount_due, users_id,  period_id)
	VALUES('mar category3', '24-MAR-2021', 521.67, 21, 21);
INSERT INTO income_categories(name, due_by, amount_due, users_id,  period_id)
	VALUES('mar category3', '24-MAR-2021', 821.67, 21, 21);
INSERT INTO income_categories(name, due_by, amount_due, users_id,  period_id)
	VALUES('apr category3', '24-APR-2021', 521.67, 21, 22);
INSERT INTO income_categories(name, due_by, amount_due, users_id,  period_id)
	VALUES('apr category4', '24-APR-2021', 821.67, 21, 22);
	
--Post Expenses
INSERT INTO expenses(name, due_by, amount_due, users_id, category_id) 
    VALUES('marexpense1', '01-MAR-2021', 100.60, 1, 1	);
INSERT INTO expenses(name, due_by, amount_due, users_id, category_id) 
    VALUES('mar_expense2', '02-MAR-2021', 150.60, 1, 2	);
INSERT INTO expenses(name, due_by, amount_due, users_id, category_id) 
    VALUES('apr_expense1', '01-APR-2021', 160.60, 1, 3	);
INSERT INTO expenses(name, due_by, amount_due, users_id, category_id) 
    VALUES('apr_expense2', '02-APR-2021', 170.60, 1, 4	);
    
INSERT INTO expenses(name, due_by, amount_due, users_id, category_id) 
    VALUES('marexpense1', '01-MAR-2021', 100.60, 21, 21);
INSERT INTO expenses(name, due_by, amount_due, users_id, category_id) 
    VALUES('mar_expense2', '02-MAR-2021', 150.60, 21, 22);
INSERT INTO expenses(name, due_by, amount_due, users_id, category_id) 
    VALUES('apr_expense1', '01-APR-2021', 160.60, 21, 23);
INSERT INTO expenses(name, due_by, amount_due, users_id, category_id) 
    VALUES('apr_expense2', '02-APR-2021', 170.60, 21, 24);

--Post Expenses_Categories
INSERT INTO expenses_categories(name, due_by, amount_due, users_id, period_id)
	VALUES('mar category1', '28-MAR-2021', 121.67, 1, 1);
INSERT INTO expenses_categories(name, due_by, amount_due, users_id, period_id)
	VALUES('mar category2', '28-MAR-2021', 131.67, 1, 1);
INSERT INTO expenses_categories(name, due_by, amount_due, users_id, period_id)
	VALUES('apr category1', '28-APR-2021', 141.67, 1, 2);
INSERT INTO expenses_categories(name, due_by, amount_due, users_id, period_id)
	VALUES('apr category2', '28-APR-2021', 151.67, 1, 2);
	
INSERT INTO expenses_categories(name, due_by, amount_due, users_id, period_id)
	VALUES('mar category3', '28-MAR-2021', 121.67, 21, 21);
INSERT INTO expenses_categories(name, due_by, amount_due, users_id, period_id)
	VALUES('mar category4', '28-MAR-2021', 131.67, 21, 21);
INSERT INTO expenses_categories(name, due_by, amount_due, users_id, period_id)
	VALUES('apr category3', '28-APR-2021', 141.67, 21, 22);
INSERT INTO expenses_categories(name, due_by, amount_due, users_id, period_id)
	VALUES('apr category4', '28-APR-2021', 151.67, 21, 22);
		
--Post period
INSERT INTO period(start_date, end_date, users_id)
    VALUES('01-MAR-2021', '31-MAR-2021', 1);
INSERT INTO period(start_date, end_date, users_id)
    VALUES('01-APR-2021', '30-APR-2021', 1);
INSERT INTO period(start_date, end_date, users_id)
    VALUES('01-MAY-2021', '31-MAY-2021', 1);
INSERT INTO period(start_date, end_date, users_id)
    VALUES('01-JUN-2021', '30-JUN-2021', 1);
INSERT INTO period(start_date, end_date, users_id)
    VALUES('01-JUL-2021', '31-JUL-2021', 1);
  
INSERT INTO period(start_date, end_date, users_id)
    VALUES('01-MAR-2021', '31-MAR-2021', 21);
INSERT INTO period(start_date, end_date, users_id)
    VALUES('01-APR-2021', '30-APR-2021', 21);
INSERT INTO period(start_date, end_date, users_id)
    VALUES('01-MAY-2021', '31-MAY-2021', 21);
INSERT INTO period(start_date, end_date, users_id)
    VALUES('01-JUN-2021', '30-JUN-2021', 21);
INSERT INTO period(start_date, end_date, users_id)
    VALUES('01-JUL-2021', '31-JUL-2021', 21);

--Post account
INSERT INTO account(name, balance, users_id)
	VALUES('testChecking', 10000, 1);
	VALUES('testSaving', 100001.99, 1);
	
	
--GET INCOME querry	
SELECT * FROM income INNER JOIN income_categories ON income_categories.id = income.category_id
INNER JOIN period ON period.id = income_categories.period_id
INNER JOIN users ON users.id = period.users_id
--WHERE income.category_id = 1
WHERE users.id = 21
AND period.id = 22;