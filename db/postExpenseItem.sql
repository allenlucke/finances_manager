--Posts expense item, then accountTracker 
CREATE OR REPLACE PROCEDURE public.postExpenseItem( 
	v_expCat int,
	v_name	varchar,
	v_transDate timestamp,
	v_amount numeric,
	v_accountId int,
	v_userId int,
	
	--Should always be null
	INOUT v_expItemId int,
	INOUT v_prevBalance numeric,
	INOUT v_newBalance numeric
)
language plpgsql    
as $$

BEGIN

--If transaction date is null, use current datetime
IF v_transDate IS NULL THEN
	v_transDate := now();
END IF;

--Post expensItem, return id to be used in the accountTracker Table
INSERT INTO "expenseItem"
	("budget_expenseCategory_id", "name", "transactionDate", "amount", "account_id", "users_id")
VALUES
	(v_expCat, v_name, v_transDate, v_amount, v_accountId, v_userId)
	RETURNING id into v_expItemId;
	
--Get the ending balance of the last post to the accountTracker Table
--Assign it as the previosu balance
SELECT * FROM (SELECT "endingBalance" FROM "accountTracker" WHERE "dateTime" <= v_transDate
ORDER BY id DESC LIMIT 1) AS foo
INTO v_prevBalance;

--Subtract the amount from the previous balance
--to generate the ending balance
IF v_newBalance IS NULL THEN
	v_newBalance := SUM(v_prevBalance - v_amount);
END IF;

--Post to account Tracker table
INSERT INTO "accountTracker"
	("account_id", "expenseItem_id", "beginningBalance", "endingBalance", "dateTime")
VALUES
	(1, v_expItemId, v_prevBalance, v_newBalance, v_transDate);	
END	
$$

--Test call
call public.postExpenseItem(1, 'Evergy Payment - May', NULL, 156.38, 1, 1, null, null, null );
