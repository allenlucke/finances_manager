package com.Allen.SpringFinancesServer.BudgetBalanceSheet;

import java.math.BigDecimal;

public class BudgetBalCatsModel {

    private int expenseCategoryId;
    private String expenseCategoryName;
    private int usersId;
    private int budgetExpenseCategoryId;
    private int budgetId;
    private int periodId;
    private BigDecimal amountBudgeted;
    private boolean isClosed;

    public BudgetBalCatsModel() {};

    public int getExpenseCategoryId() {
        return expenseCategoryId;
    }

    public void setExpenseCategoryId(int expenseCategoryId) {
        this.expenseCategoryId = expenseCategoryId;
    }

    public String getExpenseCategoryName() {
        return expenseCategoryName;
    }

    public void setExpenseCategoryName(String expenseCategoryName) {
        this.expenseCategoryName = expenseCategoryName;
    }

    public int getUsersId() {
        return usersId;
    }

    public void setUsersId(int usersId) {
        this.usersId = usersId;
    }

    public int getBudgetExpenseCategoryId() {
        return budgetExpenseCategoryId;
    }

    public void setBudgetExpenseCategoryId(int budgetExpenseCategoryId) {
        this.budgetExpenseCategoryId = budgetExpenseCategoryId;
    }

    public int getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(int budgetId) {
        this.budgetId = budgetId;
    }

    public int getPeriodId() {
        return periodId;
    }

    public void setPeriodId(int periodId) {
        this.periodId = periodId;
    }

    public BigDecimal getAmountBudgeted() {
        return amountBudgeted;
    }

    public void setAmountBudgeted(BigDecimal amountBudgeted) {
        this.amountBudgeted = amountBudgeted;
    }

    public boolean isClosed() {
        return isClosed;
    }

    public void setClosed(boolean closed) {
        isClosed = closed;
    }
}
