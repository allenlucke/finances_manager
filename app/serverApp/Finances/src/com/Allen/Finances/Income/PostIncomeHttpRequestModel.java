package com.Allen.Finances.Income;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class PostIncomeHttpRequestModel {
	
	private String name;
	private Timestamp due_on;
    private String recurring;
    private BigDecimal amount_expected;    
	private String users_id;
	
	private String category_id;
    private String category_name;
    private Timestamp cat_due_by;
    private String cat_recurring;
    private BigDecimal cat_amount_due;
    private String period;
    private String new_cat_bool;
    
	public PostIncomeHttpRequestModel() {}
	
	//Getters and Setters
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Timestamp getDue_on() {
		return due_on;
	}
	public void setDue_on(Timestamp due_on) {
		this.due_on = due_on;
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
	public String getCategory_name() {
		return category_name;
	}
	public void setCategory_name(String category_name) {
		this.category_name = category_name;
	}
	public Timestamp getCat_due_by() {
		return cat_due_by;
	}
	public void setCat_due_by(Timestamp cat_due_by) {
		this.cat_due_by = cat_due_by;
	}
	public String getCat_recurring() {
		return cat_recurring;
	}
	public void setCat_recurring(String cat_recurring) {
		this.cat_recurring = cat_recurring;
	}
	public BigDecimal getCat_amount_due() {
		return cat_amount_due;
	}
	public void setCat_amount_due(BigDecimal cat_amount_due) {
		this.cat_amount_due = cat_amount_due;
	}
	public String getPeriod() {
		return period;
	}
	public void setPeriod(String period) {
		this.period = period;
	}
	public String getNew_cat_bool() {
		return new_cat_bool;
	}
	public void setNew_cat_bool(String new_cat_bool) {
		this.new_cat_bool = new_cat_bool;
	}

	//Constructor
	public PostIncomeHttpRequestModel(String name, Timestamp due_on, String recurring, BigDecimal amount_expected,
			String users_id, String category_id, String category_name, Timestamp cat_due_by, String cat_recurring,
			BigDecimal cat_amount_due, String period, String new_cat_bool) {
		super();
		this.name = name;
		this.due_on = due_on;
		this.recurring = recurring;
		this.amount_expected = amount_expected;
		this.users_id = users_id;
		this.category_id = category_id;
		this.category_name = category_name;
		this.cat_due_by = cat_due_by;
		this.cat_recurring = cat_recurring;
		this.cat_amount_due = cat_amount_due;
		this.period = period;
		this.new_cat_bool = new_cat_bool;
	}

	@Override
	public String toString() {
		return "PostIncomeHttpRequestModel [name=" + name + ", due_on=" + due_on + ", recurring=" + recurring
				+ ", amount_expected=" + amount_expected + ", users_id=" + users_id + ", category_id=" + category_id
				+ ", category_name=" + category_name + ", cat_due_by=" + cat_due_by + ", cat_recurring=" + cat_recurring
				+ ", cat_amount_due=" + cat_amount_due + ", period=" + period + ", new_cat_bool=" + new_cat_bool + "]";
	}
	
}
