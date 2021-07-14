package com.Allen.SpringFinancesServer.Budget;

public class BudgetModel {

    private int id;
    private String name;
    private int period_id;
    private boolean isClosed;

    public BudgetModel() {};

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPeriod_id() {
        return period_id;
    }

    public void setPeriod_id(int period_id) {
        this.period_id = period_id;
    }

    public boolean isClosed() {
        return isClosed;
    }

    public void setClosed(boolean closed) {
        isClosed = closed;
    }
}
