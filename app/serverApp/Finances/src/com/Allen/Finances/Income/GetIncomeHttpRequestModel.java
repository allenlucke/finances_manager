package com.Allen.Finances.Income;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class GetIncomeHttpRequestModel {

	private String users_id;
    private String category_id;	
    private String accounts_id;
	private String name;
    private String recieved;
    private String recurring;
    private BigDecimal amount_expected;
    private BigDecimal amount_actual;
    private String period;
	private Timestamp startdate;
	private Timestamp enddate;
    
	public GetIncomeHttpRequestModel() {}
	
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

	public String getRecieved() {
		return recieved;
	}

	public void setRecieved(String recieved) {
		this.recieved = recieved;
	}

	public String getRecurring() {
		return recurring;
	}

	public void setRecurring(String recurring) {
		this.recurring = recurring;
	}

	public BigDecimal getAmount_expected() {
		return amount_expected;
	}

	public void setAmount_expected(BigDecimal amount_expected) {
		this.amount_expected = amount_expected;
	}

	public BigDecimal getAmount_actual() {
		return amount_actual;
	}

	public void setAmount_actual(BigDecimal amount_actual) {
		this.amount_actual = amount_actual;
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

	@Override
	public String toString() {
		return "GetIncomeRequestBean [users_id=" + users_id + ", category_id=" + category_id + ", accounts_id="
				+ accounts_id + ", name=" + name + ", recieved=" + recieved + ", recurring=" + recurring
				+ ", amount_expected=" + amount_expected + ", amount_actual=" + amount_actual + ", period=" + period
				+ ", startdate=" + startdate + ", enddate=" + enddate + "]";
	}	
	
	//Constructor
	public GetIncomeHttpRequestModel(String users_id, String category_id, String accounts_id, String name,
			String recieved, String recurring, BigDecimal amount_expected, BigDecimal amount_actual, String period,
			Timestamp startdate, Timestamp enddate) {
		super();
		this.users_id = users_id;
		this.category_id = category_id;
		this.accounts_id = accounts_id;
		this.name = name;
		this.recieved = recieved;
		this.recurring = recurring;
		this.amount_expected = amount_expected;
		this.amount_actual = amount_actual;
		this.period = period;
		this.startdate = startdate;
		this.enddate = enddate;
	}
	
	
	
}
