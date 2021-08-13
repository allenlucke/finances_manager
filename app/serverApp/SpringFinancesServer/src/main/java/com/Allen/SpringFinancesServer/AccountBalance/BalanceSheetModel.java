package com.Allen.SpringFinancesServer.AccountBalance;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class BalanceSheetModel {

    private int expenseItemId;
    private int incomeItemId;
    private Timestamp transactionDate;
    private String accountName;
    private int PeriodId;
    private String expenseItemName;
    private String incomeItemName;
    private int accountId;
    private BigDecimal amount;
    private BigDecimal preBalance;
    private BigDecimal postBalance;

    public BalanceSheetModel() {};

    public int getExpenseItemId() {
        return expenseItemId;
    }

    public void setExpenseItemId(int expenseItemId) {
        this.expenseItemId = expenseItemId;
    }

    public int getIncomeItemId() {
        return incomeItemId;
    }

    public void setIncomeItemId(int incomeItemId) {
        this.incomeItemId = incomeItemId;
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

    public String getIncomeItemName() {
        return incomeItemName;
    }

    public void setIncomeItemName(String incomeItemName) {
        this.incomeItemName = incomeItemName;
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

    public BigDecimal getPreBalance() {
        return preBalance;
    }

    public void setPreBalance(BigDecimal preBalance) {
        this.preBalance = preBalance;
    }

    public BigDecimal getPostBalance() {
        return postBalance;
    }

    public void setPostBalance(BigDecimal postBalance) {
        this.postBalance = postBalance;
    }
}
