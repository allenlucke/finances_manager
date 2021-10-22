package com.Allen.SpringFinancesServer.AccountBalanceSheet;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class AccountBalanceSheetModel {

    private int expenseItemId;
    private int incomeItemId;
    private String transactionDate;
    private String accountName;
    private int periodId;
    private String expenseItemName;
    private String incomeItemName;
    private int accountId;
    private BigDecimal amount;
    private BigDecimal preBalance;
    private BigDecimal postBalance;

    public AccountBalanceSheetModel() {};

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

    public String getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(String transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public int getPeriodId() {
        return periodId;
    }

    public void setPeriodId(int periodId) {
        this.periodId = periodId;
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


    public AccountBalanceSheetModel(int expenseItemId, int incomeItemId,
                                    String transactionDate, String accountName,
                                    int periodId, String expenseItemName,
                                    String incomeItemName, int accountId,
                                    BigDecimal amount, BigDecimal preBalance,
                                    BigDecimal postBalance) {
        this.expenseItemId = expenseItemId;
        this.incomeItemId = incomeItemId;
        this.transactionDate = transactionDate;
        this.accountName = accountName;
        this.periodId = periodId;
        this.expenseItemName = expenseItemName;
        this.incomeItemName = incomeItemName;
        this.accountId = accountId;
        this.amount = amount;
        this.preBalance = preBalance;
        this.postBalance = postBalance;
    }

    @Override
    public String toString() {
        return "BalanceSheetModel{" +
                "expenseItemId=" + expenseItemId +
                ", incomeItemId=" + incomeItemId +
                ", transactionDate=" + transactionDate +
                ", accountName='" + accountName + '\'' +
                ", PeriodId=" + periodId +
                ", expenseItemName='" + expenseItemName + '\'' +
                ", incomeItemName='" + incomeItemName + '\'' +
                ", accountId=" + accountId +
                ", amount=" + amount +
                ", preBalance=" + preBalance +
                ", postBalance=" + postBalance +
                '}';
    }
}
