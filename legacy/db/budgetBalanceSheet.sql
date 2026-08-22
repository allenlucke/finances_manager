--Get budget expense cats by period
SELECT "expenseCategory".id AS "expenseCategoryId",
"expenseCategory".name AS "expenseCategoryName", "expenseCategory"."users_id" AS "usersId",
"budget_expenseCategory".id AS "budgetExpenseCategoryId", "budget".id AS "budgetId",
"budget"."period_id" AS "periodId", 
"budget_expenseCategory"."amountBudgeted" AS "amountBudgeted",
"budget"."isClosed" AS "isClosed" FROM "expenseCategory"
JOIN "budget_expenseCategory" ON "expenseCategory".id = "budget_expenseCategory"."expenseCategory_id"
JOIN "budget" ON "budget_expenseCategory"."budget_id" = "budget".id
WHERE "budget"."period_id" =1;

--Get budget expense cats by date range
SELECT "expenseCategory".id AS "expenseCategoryId",
"expenseCategory".name AS "expenseCategoryName", "expenseCategory"."users_id" AS "usersId",
"budget_expenseCategory".id AS "budgetExpenseCategoryId", "budget".id AS "budgetId",
"budget"."period_id" AS "periodId", 
"budget_expenseCategory"."amountBudgeted" AS "amountBudgeted",
"budget"."isClosed" AS "isClosed" FROM "expenseCategory"
JOIN "budget_expenseCategory" ON "expenseCategory".id = "budget_expenseCategory"."expenseCategory_id"
JOIN "budget" ON "budget_expenseCategory"."budget_id" = "budget".id
JOIN "period" ON "budget"."period_id" = "period".id 
WHERE "period"."startDate" >= '05/01/2021';

--Get budget expense cats by date range
SELECT "budget_expenseCategory"."expenseCategory_id" AS "budgetExpenseCategoryId", 
SUM("budget_expenseCategory"."amountBudgeted") AS "amountBudgeted" FROM "budget_expenseCategory"
GROUP BY "budget_expenseCategory"."expenseCategory_id"
ORDER BY "budget_expenseCategory"."expenseCategory_id";

--Get budget expense items by period
SELECT "expenseItem".id, "expenseItem"."budget_expenseCategory_id", "expenseItem".name,
"expenseItem"."transactionDate", "expenseItem"."amount", "expenseItem"."paidWithCredit",
"expenseItem"."paymentToCreditAccount", "expenseItem"."interestPaymentToCreditAccount",
"expenseItem"."account_id", "expenseItem"."users_id" FROM "expenseItem"
JOIN "budget_expenseCategory" ON "expenseItem"."budget_expenseCategory_id" = "budget_expenseCategory".id
JOIN "budget" ON "budget_expenseCategory"."budget_id" = "budget".id
WHERE "budget"."period_id" = 1;

