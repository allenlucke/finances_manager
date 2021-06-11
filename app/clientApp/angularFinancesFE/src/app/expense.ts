import { Timestamp } from "rxjs";

export interface Expense {
    id: number, 
    name: string, 
    paid: boolean, /*Boolean*/ 
    due_by: string, /*Timestamp*/
    paid_on: string, /*Timestamp*/ 
    recurring: boolean, /*Boolean*/ 
    amount_due: number, 
    amount_paid: number, 
    users_id: number, 
    category_id: number,
    category_name: string, 
    accounts_id: number,
}