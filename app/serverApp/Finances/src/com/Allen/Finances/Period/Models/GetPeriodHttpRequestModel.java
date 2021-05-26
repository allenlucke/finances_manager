package com.Allen.Finances.Period.Models;

public class GetPeriodHttpRequestModel {

	private int users_id;
	
	public GetPeriodHttpRequestModel() {}

	public int getUsers_id() {
		return users_id;
	}

	public void setUsers_id(int users_id) {
		this.users_id = users_id;
	}

	public GetPeriodHttpRequestModel(int users_id) {
		super();
		this.users_id = users_id;
	}

	@Override
	public String toString() {
		return "GetPeriodHttpRequestModel [users_id=" + users_id + "]";
	}
	
	
}
