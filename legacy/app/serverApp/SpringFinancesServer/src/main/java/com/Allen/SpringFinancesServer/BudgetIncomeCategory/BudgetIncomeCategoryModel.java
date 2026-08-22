package com.Allen.SpringFinancesServer.BudgetIncomeCategory;

import java.math.BigDecimal;

public class BudgetIncomeCategoryModel {

    private int id;
    private int budgetId;
    private int incomeCategoryId;
    private BigDecimal amountBudgeted;
    private int usersId;

    public BudgetIncomeCategoryModel() {};

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

    public int getIncomeCategoryId() {
        return incomeCategoryId;
    }

    public void setIncomeCategoryId(int incomeCategoryId) {
        this.incomeCategoryId = incomeCategoryId;
    }

    public BigDecimal getAmountBudgeted() {
        return amountBudgeted;
    }

    public void setAmountBudgeted(BigDecimal amountBudgeted) {
        this.amountBudgeted = amountBudgeted;
    }

    public int getUsersId() {
        return usersId;
    }

    public void setUsersId(int usersId) {
        this.usersId = usersId;
    }
}
