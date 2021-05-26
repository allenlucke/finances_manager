package com.Allen.Finances.Period.Models;

public class PeriodModel {

	private int id;
	private String start_date;
	private String end_date;
	private int users_id;
	
	public PeriodModel() {}

	//Getters and Setters

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getStart_date() {
		return start_date;
	}

	public void setStart_date(String start_date) {
		this.start_date = start_date;
	}

	public String getEnd_date() {
		return end_date;
	}

	public void setEnd_date(String end_date) {
		this.end_date = end_date;
	}

	public int getUsers_id() {
		return users_id;
	}

	public void setUsers_id(int users_id) {
		this.users_id = users_id;
	}
	
	//Constructor
	public PeriodModel(int id, String start_date, String end_date, int users_id) {
		super();
		this.id = id;
		this.start_date = start_date;
		this.end_date = end_date;
		this.users_id = users_id;
	}

	@Override
	public String toString() {
		return "PeriodModel [id=" + id + ", start_date=" + start_date + ", end_date=" + end_date + ", users_id="
				+ users_id + "]";
	}


}
