package com.Allen.Finances.Expenses.Models;

import java.math.BigDecimal;
import java.sql.Timestamp;


public class ExpensesModel {

    private int id;
    private String name;
    private Boolean paid;
    private String due_by;
    private Timestamp paid_on;
    private Boolean recurring;
    private BigDecimal amount_due;
    private BigDecimal amount_paid;
    private int users_id;
    private int category_id;
    private String category_name;
    private int accounts_id;

    
	public ExpensesModel() {}


    //Getters and Setters
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


	public Boolean getPaid() {
		return paid;
	}


	public void setPaid(Boolean paid) {
		this.paid = paid;
	}


	public String getDue_by() {
		return due_by;
	}


	public void setDue_by(String due_by) {
		this.due_by = due_by;
	}


	public Timestamp getPaid_on() {
		return paid_on;
	}


	public void setPaid_on(Timestamp paid_on) {
		this.paid_on = paid_on;
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


	public BigDecimal getAmount_paid() {
		return amount_paid;
	}


	public void setAmount_paid(BigDecimal amount_paid) {
		this.amount_paid = amount_paid;
	}


	public int getUsers_id() {
		return users_id;
	}


	public void setUsers_id(int users_id) {
		this.users_id = users_id;
	}


	public int getCategory_id() {
		return category_id;
	}


	public void setCategory_id(int category_id) {
		this.category_id = category_id;
	}

	public String getCategory_name() {
		return category_name;
	}


	public void setCategory_name(String category_name) {
		this.category_name = category_name;
	}

	public int getAccounts_id() {
		return accounts_id;
	}


	public void setAccounts_id(int accounts_id) {
		this.accounts_id = accounts_id;
	}

	//Constructor
	public ExpensesModel(int id, String name, Boolean paid, String due_by, Timestamp paid_on, Boolean recurring,
			BigDecimal amount_due, BigDecimal amount_paid, int users_id, int category_id, String category_name, int accounts_id) {
		super();
		this.id = id;
		this.name = name;
		this.paid = paid;
		this.due_by = due_by;
		this.paid_on = paid_on;
		this.recurring = recurring;
		this.amount_due = amount_due;
		this.amount_paid = amount_paid;
		this.users_id = users_id;
		this.category_id = category_id;
		this.category_name = category_name;
		this.accounts_id = accounts_id;
	}


	@Override
	public String toString() {
		return "ExpensesModel [id=" + id + ", name=" + name + ", paid=" + paid + ", due_by=" + due_by + ", paid_on="
				+ paid_on + ", recurring=" + recurring + ", amount_due=" + amount_due + ", amount_paid=" + amount_paid
				+ ", users_id=" + users_id + ", category_id=" + category_id + ", category_name=" + category_name
				+ ", accounts_id=" + accounts_id + "]";
	}

    
}