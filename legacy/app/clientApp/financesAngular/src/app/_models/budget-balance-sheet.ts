import { ExpenseItem } from "./expense-item";

export class BudgetBalanceSheet {
    expenseCategoryId!: number;
    expenseCategoryName!: string;
    usersId!: number;
    budgetExpenseCategoryId!: number;
    budgetId!: number;
    periodId!: number;
    amountBudgeted!: number;
    expenseItems!: Array<ExpenseItem>;
    amountSpent!: number;
    amountRemaining!: number;
    closed!: boolean;
}
