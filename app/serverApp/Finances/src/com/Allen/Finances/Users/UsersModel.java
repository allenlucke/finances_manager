package com.Allen.Finances.Users;

import java.sql.Timestamp;

public class UsersModel {

	private long id;
    private String first_name;
    private String last_name;
    private Timestamp acct_created;
    private Timestamp last_active;
	private String user_name;
    private String password;
    private String email;
    private int sec_lvl;
    private boolean is_active;
	
    //Getters and Setters
    public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public String getFirst_name() {
		return first_name;
	}
	public void setFirst_name(String first_name) {
		this.first_name = first_name;
	}
	public String getLast_name() {
		return last_name;
	}
	public void setLast_name(String last_name) {
		this.last_name = last_name;
	}
	public Timestamp getAcct_created() {
		return acct_created;
	}
	public void setAcct_created(Timestamp acct_created) {
		this.acct_created = acct_created;
	}
	public Timestamp getLast_active() {
		return last_active;
	}
	public void setLast_active(Timestamp last_active) {
		this.last_active = last_active;
	}
    public String getUser_name() {
		return user_name;
	}
	public void setUser_name(String user_name) {
		this.user_name = user_name;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public int getSec_lvl() {
		return sec_lvl;
	}
	public void setSec_lvl(int sec_lvl) {
		this.sec_lvl = sec_lvl;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public boolean getIs_active() {
		return is_active;
	}
	public void setIs_active(boolean is_active) {
		this.is_active = is_active;
	}

	//Constructor
	public UsersModel(long id, String first_name, String last_name, Timestamp acct_created, Timestamp last_active,
			String user_name, String password, String email, int sec_lvl, boolean is_active) {
		this.id = id;
		this.first_name = first_name;
		this.last_name = last_name;
		this.acct_created = acct_created;
		this.last_active = last_active;
		this.user_name = user_name;
		this.password = password;
		this.email = email;
		this.sec_lvl = sec_lvl;
		this.is_active = is_active;
	}

	@Override
	public String toString() {
		return "Users [id=" + id + ", first_name=" + first_name + ", last_name=" + last_name + ", acct_created="
				+ acct_created + ", last_active=" + last_active + ", user_name=" + user_name + ", password=" + password
				+ ", email=" + email + ", sec_lvl=" + sec_lvl + ", is_active=" + is_active + "]";
	}
    
}
