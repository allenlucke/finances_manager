
--postExpenseItem to table, return id, post to accountTracker table
CREATE OR REPLACE PROCEDURE public.postExpenseItem(
	v_expCat int,
	v_name	varchar,
	v_transDate timestamp,
	v_amount numeric,
	v_accountId int,
	v_userId int,

	INOUT v_accId int
)
language plpgsql
as $$

BEGIN

INSERT INTO "expenseItem"
	("budget_expenseCategory_id", "name", "transactionDate", "amount", "account_id", "users_id")
VALUES
	(v_expCat, v_name, v_transDate, v_amount, v_accountId, v_userId)
	RETURNING id into v_accId;

    commit;

INSERT INTO "accountTracker"
	("account_id", "expenseItem_id", "dateTime")
VALUES
	(1, v_accId, v_transDate);
END
$$

call public.postExpenseItem(1, 'Evergy Payment - May', '05/21/2021', 156.38, 1, 1, null );

--updated sp
CREATE OR REPLACE PROCEDURE public.postExpenseItem(
	v_expCat int,
	v_name	varchar,
	v_transDate timestamp,
	v_amount numeric,
	v_accountId int,
	v_userId int,

	INOUT v_expItemId int,
	INOUT prevBalance numeric
)
language plpgsql
as $$

BEGIN

IF v_transDate IS NULL THEN
	v_transDate := now();
END IF;

INSERT INTO "expenseItem"
	("budget_expenseCategory_id", "name", "transactionDate", "amount", "account_id", "users_id")
VALUES
	(v_expCat, v_name, v_transDate, v_amount, v_accountId, v_userId)
	RETURNING id into v_expItemId;



INSERT INTO "accountTracker"
	("account_id", "expenseItem_id", "dateTime")
VALUES
	(1, v_expItemId, v_transDate);
END
$$

call public.postExpenseItem(1, 'Evergy Payment - May', NULL, 156.38, 1, 1, null, null );

SELECT "expenseItem".id, "expenseItem"."transactionDate" FROM "expenseItem"
JOIN "account" ON "expenseItem"."account_id" = "account".id
JOIN "budget_expenseCategory" ON "expenseItem"."budget_expenseCategory_id" = "budget_expenseCategory".id
JOIN "budget" ON "budget_expenseCategory".id = "budget".id
JOIN "period" ON "budget".period_id = "period".id
WHERE "expenseItem"."transactionDate" >= "period"."startDate" AND "expenseItem"."transactionDate" <= "period"."endDate"
AND "account".id =1;
