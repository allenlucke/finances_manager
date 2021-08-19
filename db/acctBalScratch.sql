

--GET expenseItems by account/period
SELECT "expenseItem".id, "expenseItem"."transactionDate", 
"account".name AS "accountName" , "period".id AS "periodId", 
"expenseItem".name AS "expenseItemName", "account".id AS "accountId", 
"expenseItem".amount FROM "expenseItem"
JOIN "account" ON "expenseItem"."account_id" = "account".id
JOIN "budget_expenseCategory" ON "expenseItem"."budget_expenseCategory_id" = "budget_expenseCategory".id
JOIN "budget" ON "budget_expenseCategory"."budget_id" = "budget".id
JOIN "period" ON "budget".period_id = "period".id
JOIN "accountPeriod" ON "period".id = "accountPeriod".period_id
WHERE "expenseItem"."transactionDate" >= "period"."startDate" 
AND "expenseItem"."transactionDate" <= "period"."endDate"
AND "account".id =1
AND "period".id = 2
ORDER BY "expenseItem"."transactionDate";


--Get count of open periods prior to period's startdate
SELECT COUNT(*) FROM "period" 
JOIN "budget" ON "period".id = "budget".period_id
WHERE "startDate" < '08/01/2021'
AND "budget"."isClosed" = 'false';


--Get startdate of oldest unclosed period
SELECT "period"."id" as "periodId", "period"."name" AS "periodName",
"period"."startDate" AS "startDate", "period"."endDate" AS "endDate",
"period"."users_id" AS "usersId", "budget".id AS "budgetId",
"budget".name AS "budgetName", "budget"."isClosed" FROM "period" 
JOIN "budget" ON "period".id = "budget".period_id
WHERE "period"."startDate" < '05/01/2021'
AND "budget"."isClosed" = 'false'
ORDER BY "period"."startDate" ASC
LIMIT 1;

UPDATE "budget" SET "isClosed" = false WHERE "budget".id =1;

--Get beggining balance of oldest unclosed period
SELECT "accountPeriod"."beginningBalance", "period".id FROM "period" 
JOIN "budget" ON "period".id = "budget".period_id
JOIN "accountPeriod" ON "period".id = "accountPeriod".period_id
WHERE "period"."startDate" < '06/01/2021'
AND "budget"."isClosed" = 'false'
ORDER BY "period"."startDate" ASC
LIMIT 1;

--Get beginning balance by id
SELECT "accountPeriod"."beginningBalance", "period".id FROM "period" 
JOIN "budget" ON "period".id = "budget".period_id
JOIN "accountPeriod" ON "period".id = "accountPeriod".period_id
WHERE "period"."id" = 1;

--GET expenseItems by dates/account
SELECT "expenseItem".id, "expenseItem"."transactionDate", 
"account".name AS "accountName" , "period".id AS "periodId", 
"expenseItem".name AS "expenseItemName", "account".id AS "accountId", 
"expenseItem".amount FROM "expenseItem"
JOIN "account" ON "expenseItem"."account_id" = "account".id
JOIN "budget_expenseCategory" ON "expenseItem"."budget_expenseCategory_id" = "budget_expenseCategory".id
JOIN "budget" ON "budget_expenseCategory"."budget_id" = "budget".id
JOIN "period" ON "budget".period_id = "period".id
JOIN "accountPeriod" ON "period".id = "accountPeriod".period_id
WHERE "expenseItem"."transactionDate" >= "period"."startDate" 
AND "expenseItem"."transactionDate" <= "period"."endDate"
AND "account".id =1
AND "expenseItem"."transactionDate" >= '2021-05-01 00:00:00.0'
AND "expenseItem"."transactionDate" < '2021-06-01 00:00:00.0'
ORDER BY "expenseItem"."transactionDate";

--GET incomeItems by account/period
SELECT "incomeItem".id, "incomeItem"."receivedDate", 
"account".name AS "accountName" , "period".id AS "periodId", 
"incomeItem".name AS "incomeItemName", "account".id AS "accountId", 
"incomeItem"."amountReceived" FROM "incomeItem"
JOIN "account" ON "incomeItem"."account_id" = "account".id
JOIN "budget_incomeCategory" ON "incomeItem"."budget_incomeCategory_id" = "budget_incomeCategory".id
JOIN "budget" ON "budget_incomeCategory"."budget_id" = "budget".id
JOIN "period" ON "budget".period_id = "period".id
JOIN "accountPeriod" ON "period".id = "accountPeriod".period_id
WHERE "incomeItem"."receivedDate" >= "period"."startDate" 
AND "incomeItem"."receivedDate" <= "period"."endDate"
AND "account".id =1
AND "period".id = 1
ORDER BY "incomeItem"."receivedDate";

