package com.Allen.SpringFinancesServer.AccountBalanceSheet;

import java.sql.Timestamp;

public class AccountBalanceModel {

    private int periodId;
    private String periodName;
    private Timestamp startDate;
    private Timestamp endDate;
    private int usersId;
    private int budgetId;
    private String budgetName;
    private boolean isClosed;

    public AccountBalanceModel () {}

    public int getPeriodId() {
        return periodId;
    }

    public void setPeriodId(int periodId) {
        this.periodId = periodId;
    }

    public String getPeriodName() {
        return periodName;
    }

    public void setPeriodName(String periodName) {
        this.periodName = periodName;
    }

    public Timestamp getStartDate() {
        return startDate;
    }

    public void setStartDate(Timestamp startDate) {
        this.startDate = startDate;
    }

    public Timestamp getEndDate() {
        return endDate;
    }

    public void setEndDate(Timestamp endDate) {
        this.endDate = endDate;
    }

    public int getUsersId() {
        return usersId;
    }

    public void setUsersId(int usersId) {
        this.usersId = usersId;
    }

    public int getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(int budgetId) {
        this.budgetId = budgetId;
    }

    public String getBudgetName() {
        return budgetName;
    }

    public void setBudgetName(String budgetName) {
        this.budgetName = budgetName;
    }

    public boolean isClosed() {
        return isClosed;
    }

    public void setClosed(boolean closed) {
        isClosed = closed;
    }


    @Override
    public String toString() {
        return "AccountBalanceModel{" +
                "periodId=" + periodId +
                ", periodName='" + periodName + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", usersId=" + usersId +
                ", budgetId=" + budgetId +
                ", budgetName='" + budgetName + '\'' +
                ", isClosed=" + isClosed +
                '}';
    }
}
