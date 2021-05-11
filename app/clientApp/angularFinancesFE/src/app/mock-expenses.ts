import { Expense } from './expense';

export const EXPENSES: Expense[] = [
    {id: 1, name: 'marexpense1', paid: false, due_by: 'mar-01-2021', paid_on: '', recurring: false, amount_due: 100.6, amount_paid: 0, users_id: 1, category_id: 1, accounts_id: 0},
    {id: 2, name: 'marexpense2', paid: false, due_by: 'mar-02-2021', paid_on: '', recurring: false, amount_due: 150.6, amount_paid: 0, users_id: 1, category_id: 2, accounts_id: 0},
    {id: 3, name: 'aprexpense1', paid: false, due_by: 'apr-01-2021', paid_on: '', recurring: false, amount_due: 160.6, amount_paid: 0, users_id: 1, category_id: 3, accounts_id: 0},
    {id: 4, name: 'aprexpense2', paid: false, due_by: 'apr-02-2021', paid_on: '', recurring: false, amount_due: 170.6, amount_paid: 0, users_id: 1, category_id: 4, accounts_id: 0}

];