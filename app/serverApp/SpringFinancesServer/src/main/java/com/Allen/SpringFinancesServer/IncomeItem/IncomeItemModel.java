package com.Allen.SpringFinancesServer.IncomeItem;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class IncomeItemModel {

    private int id;
    private int budgetIncomeCategoryId;
    private String name;
    private Timestamp receivedDate;
    private BigDecimal amountExpected;
    private BigDecimal amountReceived;
    private int accountId;
    private int usersId;

    public IncomeItemModel() {};

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBudgetIncomeCategoryId() {
        return budgetIncomeCategoryId;
    }

    public void setBudgetIncomeCategoryId(int budgetIncomeCategoryId) {
        this.budgetIncomeCategoryId = budgetIncomeCategoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Timestamp getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(Timestamp receivedDate) {
        this.receivedDate = receivedDate;
    }

    public BigDecimal getAmountExpected() {
        return amountExpected;
    }

    public void setAmountExpected(BigDecimal amountExpected) {
        this.amountExpected = amountExpected;
    }

    public BigDecimal getAmountReceived() {
        return amountReceived;
    }

    public void setAmountReceived(BigDecimal amountReceived) {
        this.amountReceived = amountReceived;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public int getUsersId() {
        return usersId;
    }

    public void setUsersId(int usersId) {
        this.usersId = usersId;
    }
}
