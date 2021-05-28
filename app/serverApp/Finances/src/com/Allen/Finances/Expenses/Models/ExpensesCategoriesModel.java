package com.Allen.Finances.Expenses.Models;

import java.math.BigDecimal;


public class ExpensesCategoriesModel {
	
	private int id;
	private String name;
	private String due_by;
	private BigDecimal amount_paid;
	private Boolean recurring;
	private BigDecimal amount_due;
	private int users_id;
	private int period_id;
	
	public ExpensesCategoriesModel() {}

	//Fetters and Setters
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

	public String getDue_by() {
		return due_by;
	}

	public void setDue_by(String due_by) {
		this.due_by = due_by;
	}

	public BigDecimal getAmount_paid() {
		return amount_paid;
	}

	public void setAmount_paid(BigDecimal amount_paid) {
		this.amount_paid = amount_paid;
	}

	public Boolean getRecurring() {
		return recurring;
	}

	public void setRecurring(Boolean recurring) {
		this.recurring = recurring;
	}

	public BigDecimal getAmount_due() {
		return amount_due;
	}

	public void setAmount_due(BigDecimal amount_due) {
		this.amount_due = amount_due;
	}

	public int getUsers_id() {
		return users_id;
	}

	public void setUsers_id(int users_id) {
		this.users_id = users_id;
	}

	public int getPeriod_id() {
		return period_id;
	}

	public void setPeriod_id(int period_id) {
		this.period_id = period_id;
	}

	//Constructor
	public ExpensesCategoriesModel(int id, String name, String due_by, BigDecimal amount_paid, Boolean recurring,
			BigDecimal amount_due, int users_id, int period_id) {
		super();
		this.id = id;
		this.name = name;
		this.due_by = due_by;
		this.amount_paid = amount_paid;
		this.recurring = recurring;
		this.amount_due = amount_due;
		this.users_id = users_id;
		this.period_id = period_id;
	}

	@Override
	public String toString() {
		return "ExpensesCategoriesModel [id=" + id + ", name=" + name + ", due_by=" + due_by + ", amount_paid="
				+ amount_paid + ", recurring=" + recurring + ", amount_due=" + amount_due + ", users_id=" + users_id
				+ ", period_id=" + period_id + "]";
	}

}
