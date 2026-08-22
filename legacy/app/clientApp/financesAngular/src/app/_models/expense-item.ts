export class ExpenseItem {
    id!: number;
    budgetExpenseCategoryId!: number;
    name!: string;
    transactionDate!: Date;
    amount!: number;
    paidWithCredit!: boolean;
    paymentToCreditAccount!: boolean;
    interestPaymentToCreditAccount!: boolean;
    accountId!: number;
    usersId!: number;
}
