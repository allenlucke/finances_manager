package com.Allen.SpringFinancesServer.AccountBalance;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class ExpItemModel {

    private int id;
    private Timestamp transactionDate;
    private String accountName;
    private int PeriodId;
    private String expenseItemName;
    private int accountId;
    private BigDecimal amount;

    public ExpItemModel() {};

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Timestamp getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Timestamp transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public int getPeriodId() {
        return PeriodId;
    }

    public void setPeriodId(int periodId) {
        PeriodId = periodId;
    }

    public String getExpenseItemName() {
        return expenseItemName;
    }

    public void setExpenseItemName(String expenseItemName) {
        this.expenseItemName = expenseItemName;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
