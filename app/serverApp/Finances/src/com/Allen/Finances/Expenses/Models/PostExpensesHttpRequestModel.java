package com.Allen.Finances.Expenses.Models;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class PostExpensesHttpRequestModel {
	
    private String name;   
    private String paid;
    private Timestamp due_by;    
    private String recurring;
    private BigDecimal amount_due;
    private BigDecimal amount_paid;
    private String users_id;
    private String category_id;
    
    private String cat_name;
    private Timestamp cat_due_by;
    private Timestamp cat_paid_on;
    private BigDecimal cat_amount_paid;
    private String cat_recurring;
    private BigDecimal cat_amount_due;
    private String period;
    private String new_cat_bool;

    public PostExpensesHttpRequestModel() {}

    //Getters and Setters
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

	public Timestamp getDue_by() {
		return due_by;
	}

	public void setDue_by(Timestamp due_by) {
		this.due_by = due_by;
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

	public String getCat_name() {
		return cat_name;
	}

	public void setCat_name(String cat_name) {
		this.cat_name = cat_name;
	}

	public Timestamp getCat_due_by() {
		return cat_due_by;
	}

	public void setCat_due_by(Timestamp cat_due_by) {
		this.cat_due_by = cat_due_by;
	}

	public Timestamp getCat_paid_on() {
		return cat_paid_on;
	}

	public void setCat_paid_on(Timestamp cat_paid_on) {
		this.cat_paid_on = cat_paid_on;
	}

	public BigDecimal getCat_amount_paid() {
		return cat_amount_paid;
	}

	public void setCat_amount_paid(BigDecimal cat_amount_paid) {
		this.cat_amount_paid = cat_amount_paid;
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
	public PostExpensesHttpRequestModel(String name, String paid, Timestamp due_by, String recurring,
			BigDecimal amount_due, BigDecimal amount_paid, String users_id, String category_id, String cat_name,
			Timestamp cat_due_by, Timestamp cat_paid_on, BigDecimal cat_amount_paid, String cat_recurring,
			BigDecimal cat_amount_due, String period, String new_cat_bool) {
		super();
		this.name = name;
		this.paid = paid;
		this.due_by = due_by;
		this.recurring = recurring;
		this.amount_due = amount_due;
		this.amount_paid = amount_paid;
		this.users_id = users_id;
		this.category_id = category_id;
		this.cat_name = cat_name;
		this.cat_due_by = cat_due_by;
		this.cat_paid_on = cat_paid_on;
		this.cat_amount_paid = cat_amount_paid;
		this.cat_recurring = cat_recurring;
		this.cat_amount_due = cat_amount_due;
		this.period = period;
		this.new_cat_bool = new_cat_bool;
	}

	@Override
	public String toString() {
		return "PostExpensesHttpRequestModel [name=" + name + ", paid=" + paid + ", due_by=" + due_by + ", recurring="
				+ recurring + ", amount_due=" + amount_due + ", amount_paid=" + amount_paid + ", users_id=" + users_id
				+ ", category_id=" + category_id + ", cat_name=" + cat_name + ", cat_due_by=" + cat_due_by
				+ ", cat_paid_on=" + cat_paid_on + ", cat_amount_paid=" + cat_amount_paid + ", cat_recurring="
				+ cat_recurring + ", cat_amount_due=" + cat_amount_due + ", period=" + period + ", new_cat_bool="
				+ new_cat_bool + "]";
	}
       
}
