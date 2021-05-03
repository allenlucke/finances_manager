package com.Allen.Finances.Expenses;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class GetExpensesHttpRequestModel {

    private String users_id;
    private String category_id;	
    private String accounts_id;
    private String name;
    private String paid;
    private String recurring;
    private BigDecimal amount_due;
    private BigDecimal amount_paid;
    private String period;
	private Timestamp startdate;
	private Timestamp enddate;
    
    public GetExpensesHttpRequestModel() {}

    //Getters and Setters
	public String getUsers_id() {
		return users_id;
	}

	public void setUsers_id(String users_id) {
		this.users_id = users_id;
	}

	public String getCategory_id() {
		return category_id;
	}

	public void setCategory_id(String category_id) {
		this.category_id = category_id;
	}

	public String getAccounts_id() {
		return accounts_id;
	}

	public void setAccounts_id(String accounts_id) {
		this.accounts_id = accounts_id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPaid() {
		return paid;
	}

	public void setPaid(String paid) {
		this.paid = paid;
	}

	public String getRecurring() {
		return recurring;
	}

	public void setRecurring(String recurring) {
		this.recurring = recurring;
	}

	public BigDecimal getAmount_due() {
		return amount_due;
	}

	public void setAmount_due(BigDecimal amount_due) {
		this.amount_due = amount_due;
	}

	public BigDecimal getAmount_paid() {
		return amount_paid;
	}

	public void setAmount_paid(BigDecimal amount_paid) {
		this.amount_paid = amount_paid;
	}

	public String getPeriod() {
		return period;
	}

	public void setPeriod(String period) {
		this.period = period;
	}

	public Timestamp getStartdate() {
		return startdate;
	}

	public void setStartdate(Timestamp startdate) {
		this.startdate = startdate;
	}

	public Timestamp getEnddate() {
		return enddate;
	}

	public void setEnddate(Timestamp enddate) {
		this.enddate = enddate;
	}
    
    //Constructor
	public GetExpensesHttpRequestModel(String users_id, String category_id, String accounts_id, String name,
			String paid, String recurring, BigDecimal amount_due, BigDecimal amount_paid,
			String period, Timestamp startdate, Timestamp enddate) {
		super();
		this.users_id = users_id;
		this.category_id = category_id;
		this.accounts_id = accounts_id;
		this.name = name;
		this.paid = paid;
		this.recurring = recurring;
		this.amount_due = amount_due;
		this.amount_paid = amount_paid;
		this.period = period;
		this.startdate = startdate;
		this.enddate = enddate;
	}

	@Override
	public String toString() {
		return "GetExpensesHttpRequestModel [users_id=" + users_id + ", category_id=" + category_id + ", accounts_id="
				+ accounts_id + ", name=" + name + ", paid=" + paid + ", recurring=" + recurring +  ", amount_due=" + amount_due + ", amount_paid=" + amount_paid + ", period="
				+ period + ", startdate=" + startdate + ", enddate=" + enddate + "]";
	}
    
}
