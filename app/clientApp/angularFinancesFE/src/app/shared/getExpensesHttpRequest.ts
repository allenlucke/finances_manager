export interface GetExpensesHttpRequest{
    users_id: number;
    category_id: number;
    accounts_id: number;
    name: string;
    paid: string;
    recurring: string;
    amount_due: number;
    amount_paid: number;
    period: string;
    startdate : string;
    enddate : string;
}