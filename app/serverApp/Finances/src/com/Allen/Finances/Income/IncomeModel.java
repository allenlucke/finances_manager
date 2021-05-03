package com.Allen.Finances.Income;


import java.math.BigDecimal;
//import javax.persistence.*;
import java.sql.Timestamp;


public class IncomeModel {

    private int id;
    private String name;
    private Boolean recieved;
    private String due_on;
    private Timestamp recieved_on;
    private Boolean recurring;
    private BigDecimal amount_expected;
    private BigDecimal amount_actual;
    private int users_id;
    private int category_id;	
    private int accounts_id;


	public IncomeModel() {}


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


	public Boolean getRecieved() {
		return recieved;
	}


	public void setRecieved(Boolean recieved) {
		this.recieved = recieved;
	}


	public String getDue_on() {
		return due_on;
	}


	public void setDue_on(String due_on) {
		this.due_on = due_on;
	}


	public Timestamp getRecieved_on() {
		return recieved_on;
	}


	public void setRecieved_on(Timestamp recieved_on) {
		this.recieved_on = recieved_on;
	}


	public Boolean getRecurring() {
		return recurring;
	}


	public void setRecurring(Boolean recurring) {
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
	
	
	public int getAccounts_id() {
		return accounts_id;
	}


	public void setAccounts_id(int accounts_id) {
		this.accounts_id = accounts_id;
	}

	//Constructor
	public IncomeModel(int id, String name, Boolean recieved, String due_on, Timestamp recieved_on, Boolean recurring,
			BigDecimal amount_expected, BigDecimal amount_actual, int users_id, int category_id, int accounts_id) {
		super();
		this.id = id;
		this.name = name;
		this.recieved = recieved;
		this.due_on = due_on;
		this.recieved_on = recieved_on;
		this.recurring = recurring;
		this.amount_expected = amount_expected;
		this.amount_actual = amount_actual;
		this.users_id = users_id;
		this.category_id = category_id;
		this.accounts_id = accounts_id;
	}


	@Override
	public String toString() {
		return "Income [id=" + id + ", name=" + name + ", recieved=" + recieved + ", due_on=" + due_on
				+ ", recieved_on=" + recieved_on + ", recurring=" + recurring + ", amount_expected=" + amount_expected
				+ ", amount_actual=" + amount_actual + ", users_id=" + users_id + "category_id=" + category_id  
				+ ", accounts_id=" + accounts_id + "]";
	}

    
}