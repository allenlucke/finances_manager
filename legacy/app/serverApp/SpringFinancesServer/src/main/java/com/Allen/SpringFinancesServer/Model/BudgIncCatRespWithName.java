package com.Allen.SpringFinancesServer.Model;

import java.math.BigDecimal;

public class BudgIncCatRespWithName {

    private int id;
    private int budgetId;
    private int incomeCategoryId;
    private BigDecimal amountBudgeted;
    private int usersId;
    private String name;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
