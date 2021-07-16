package com.Allen.SpringFinancesServer.ExpenseItem;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class ExpenseItemModel {

    private int id;
    private int budgetExpenseCategoryId;
    private String name;
    private Timestamp transactionDate;
    private BigDecimal amount;
    private Boolean paidWithCredit;
    private Boolean paymentToCreditAccount;
    private Boolean interestPaymentToCreditAccount;
    private int accountId;
    private int usersId;

    ExpenseItemModel() {};

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBudgetExpenseCategoryId() {
        return budgetExpenseCategoryId;
    }

    public void setBudgetExpenseCategoryId(int budgetExpenseCategoryId) {
        this.budgetExpenseCategoryId = budgetExpenseCategoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Timestamp getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Timestamp transactionDate) {
        this.transactionDate = transactionDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Boolean getPaidWithCredit() {
        return paidWithCredit;
    }

    public void setPaidWithCredit(Boolean paidWithCredit) {
        this.paidWithCredit = paidWithCredit;
    }

    public Boolean getPaymentToCreditAccount() {
        return paymentToCreditAccount;
    }

    public void setPaymentToCreditAccount(Boolean paymentToCreditAccount) {
        this.paymentToCreditAccount = paymentToCreditAccount;
    }

    public Boolean getInterestPaymentToCreditAccount() {
        return interestPaymentToCreditAccount;
    }

    public void setInterestPaymentToCreditAccount(Boolean interestPaymentToCreditAccount) {
        this.interestPaymentToCreditAccount = interestPaymentToCreditAccount;
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
