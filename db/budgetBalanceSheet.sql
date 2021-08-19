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