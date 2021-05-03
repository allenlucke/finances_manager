package com.Allen.Finances.Expenses;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.sql.DataSource;

import com.Allen.Finances.Bean.BooleanValidator;
import com.Allen.Finances.Bean.CatalinaSimpleLog;


import oracle.jdbc.OracleTypes;

public class GetExpensesSPDAO {
	
	public static final String CLASS_NAME = GetExpensesSPDAO.class.getSimpleName();

	private static final int USERS_ID_INDEX = 1;
	private static final int CATEGORY_ID_INDEX = 2;
	private static final int ACCOUNTS_ID_INDEX = 3;
	private static final int NAME_INDEX = 4;
	private static final int PAID_INDEX = 5;
	private static final int RECURRING_INDEX = 6;
	private static final int AMOUNT_DUE_INDEX = 7;
	private static final int AMOUNT_PAID_INDEX = 8;
	private static final int PERIOD_INDEX = 9;
	private static final int STARTDATE_INDEX = 10;
	private static final int ENDDATE_INDEX = 11;
	private static final int CURSOR_INDEX = 12;
	
	private static final String DATASOURCE = "java:/comp/env/jdbc/finances";
	
	public GetExpensesSPDAO() {}
	
	public List<ExpensesModel> callGetExpenses(GetExpensesHttpRequestModel request) throws ServletException {
		
		CatalinaSimpleLog.log("INFO", CLASS_NAME, "In GetExpensesSPDAO");
			
			final String methodName = "callGetExpenses()";
			
			final String query = "{call GETExpenses(?,?,?,?,?,?,?,?,?,?,?,?)}";
			
			Connection conn = null;
			CallableStatement cs = null;
			ResultSet rs = null;
			
			try{
				CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Creating connection");
				conn = createConnection();
				
				cs = conn.prepareCall(query);
				cs.setString(USERS_ID_INDEX, request.getUsers_id());
				cs.setString(CATEGORY_ID_INDEX, request.getCategory_id());							
				cs.setString(ACCOUNTS_ID_INDEX, request.getAccounts_id());				
				cs.setString(NAME_INDEX, request.getName());
				cs.setInt(PAID_INDEX, BooleanValidator.stringToInt(request.getPaid()));
				cs.setInt(RECURRING_INDEX, BooleanValidator.stringToInt(request.getRecurring()));
				cs.setBigDecimal(AMOUNT_DUE_INDEX, request.getAmount_due());
				cs.setBigDecimal(AMOUNT_PAID_INDEX, request.getAmount_paid());
				cs.setString(PERIOD_INDEX, request.getPeriod());
				cs.setTimestamp(STARTDATE_INDEX, request.getStartdate());
				cs.setTimestamp(ENDDATE_INDEX, request.getEnddate());
				cs.registerOutParameter(CURSOR_INDEX, OracleTypes.CURSOR);
				
				CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Preparing to execute");
				cs.execute();
				
				rs = (ResultSet) cs.getObject(12);
				List<ExpensesModel> expensesResultList = new ArrayList<ExpensesModel>();
				
				CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Query executed");
				
				while(rs.next()) {
					
				    ExpensesModel obj = new ExpensesModel ();
				    obj.setId(rs.getInt("id"));
				    obj.setName(rs.getString("name"));
				    obj.setPaid(rs.getBoolean("paid"));
				    obj.setDue_by(rs.getString("due_by"));
				    obj.setPaid_on(rs.getTimestamp("paid_on"));
				    obj.setRecurring(rs.getBoolean("recurring"));
				    obj.setAmount_due(rs.getBigDecimal("amount_due"));
				    obj.setAmount_paid(rs.getBigDecimal("amount_paid"));
				    obj.setUsers_id(rs.getInt("users_id"));
				    obj.setCategory_id(rs.getInt("category_id"));
				    obj.setAccounts_id(rs.getInt("accounts_id"));
				    
	            	expensesResultList.add(obj);
	            	CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Returning result set of "+String.valueOf(expensesResultList.size()));
				}
				return expensesResultList;
			}
			catch(NamingException | SQLException e) {
            	CatalinaSimpleLog.log("SEVERE", CLASS_NAME, methodName, "Has thrown an exception: " + e.getMessage());
				throw new ServletException(e.getMessage());
			}
			finally {
				cleanUpConnection(conn, cs, null);
			}
			
		}
		
		//Closing connection objects, ignoring null provided parameters
		private void cleanUpConnection(Connection conn, CallableStatement cs, ResultSet rs) {
			final String methodName = "cleanUpConnection()";
			try {
				if(conn!=null)
					conn.close();
			}
			catch(SQLException e){
				
			}
			try {
				if(cs != null)
					cs.close();
			}
			catch(SQLException e) {
				
			}
			try {
				if(rs != null)
					rs.close();
			}
			catch(SQLException e) {
				
			}
		}
		
		//Opens connection to the jbdc defined by DATASOURCE
		private Connection createConnection() throws NamingException, SQLException {
			final Context ctx = new InitialContext();
			final DataSource ds = (DataSource) ctx.lookup(DATASOURCE);
			final Connection conn = ds.getConnection();
			
			return conn;
		}
}
