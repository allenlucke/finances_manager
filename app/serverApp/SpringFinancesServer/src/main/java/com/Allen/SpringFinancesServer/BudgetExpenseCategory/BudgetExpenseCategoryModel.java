package com.Allen.SpringFinancesServer.BudgetExpenseCategory;

import java.math.BigDecimal;

public class BudgetExpenseCategoryModel {

    private int id;
    private int budgetId;
    private int expenseCategoryId;
    private BigDecimal amountBudgeted;

    public BudgetExpenseCategoryModel() {};

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(int budgetId) {
        this.budgetId = budgetId;
    }

    public int getExpenseCategoryId() {
        return expenseCategoryId;
    }

    public void setExpenseCategoryId(int expenseCategoryId) {
        this.expenseCategoryId = expenseCategoryId;
    }

    public BigDecimal getAmountBudgeted() {
        return amountBudgeted;
    }

    public void setAmountBudgeted(BigDecimal amountBudgeted) {
        this.amountBudgeted = amountBudgeted;
    }
}
