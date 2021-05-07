package com.Allen.Finances.Expenses.DAO;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.sql.DataSource;

import com.Allen.Finances.Bean.BooleanValidator;
import com.Allen.Finances.Bean.CatalinaSimpleLog;
import com.Allen.Finances.Expenses.Models.PostExpensesHttpRequestModel;

import oracle.jdbc.OracleTypes;


public class PostExpensesSPDAO {

	public static final String CLASS_NAME = PostExpensesSPDAO.class.getSimpleName();
	
	private static final int NAME_INDEX = 1;
	private static final int PAID_INDEX = 2;
	private static final int DUE_BY_INDEX = 3;
	private static final int RECURRING_INDEX = 4;
	private static final int AMOUNT_DUE_INDEX = 5;
	private static final int AMOUNT_PAID_INDEX = 6;
	private static final int USERS_ID_INDEX = 7;
	private static final int CATEGORY_ID_INDEX = 8;	
		
	private static final int CAT_NAME_INDEX = 9;
	private static final int CAT_DUE_BY_INDEX = 10;
	private static final int CAT_PAID_ON_INDEX = 11;
	private static final int CAT_AMOUNT_PAID_INDEX = 12;
	private static final int CAT_RECURRING_INDEX = 13;
	private static final int CAT_AMOUNT_DUE_INDEX = 14;
	private static final int PERIOD_INDEX = 15;
	private static final int NEW_CAT_BOOL_INDEX = 16;
	
	private static final int RETURNING_ID_INDEX = 17;
    
	
	private static final String DATASOURCE = "java:/comp/env/jdbc/finances";
	
	public PostExpensesSPDAO() {}
	
	public int callPostExpenses(PostExpensesHttpRequestModel request) throws ServletException {
	
		CatalinaSimpleLog.log("INFO", CLASS_NAME, "In PostExpensesSPDAO");
		
		final String methodName = "callPostExpenses()";
		
		final String query = "{call POSTExpenses(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}";
		
		Connection conn = null;
		CallableStatement cs = null;
		int result;
		
		try{
			CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Creating connection");
			conn = createConnection();
			
			cs = conn.prepareCall(query);
			
			cs.setString(NAME_INDEX, request.getName());
			cs.setInt(PAID_INDEX, BooleanValidator.stringToInt(request.getPaid()));	
			cs.setTimestamp(DUE_BY_INDEX, request.getDue_by());
			cs.setInt(RECURRING_INDEX, BooleanValidator.stringToInt(request.getRecurring()));			
			cs.setBigDecimal(AMOUNT_DUE_INDEX, request.getAmount_due());
			cs.setBigDecimal(AMOUNT_PAID_INDEX, request.getAmount_paid());		
			cs.setString(USERS_ID_INDEX, request.getUsers_id());			
			cs.setString(CATEGORY_ID_INDEX, request.getCategory_id());
			
			cs.setString(CAT_NAME_INDEX, request.getCat_name());
			cs.setTimestamp(CAT_DUE_BY_INDEX, request.getCat_due_by());	
			cs.setTimestamp(CAT_PAID_ON_INDEX, request.getCat_paid_on());	
			cs.setBigDecimal(CAT_AMOUNT_PAID_INDEX, request.getAmount_paid());			
			cs.setInt(CAT_RECURRING_INDEX, BooleanValidator.stringToInt(request.getCat_recurring()));			
			cs.setBigDecimal(CAT_AMOUNT_DUE_INDEX, request.getCat_amount_due());		
			cs.setString(PERIOD_INDEX, request.getPeriod());		
			cs.setInt(NEW_CAT_BOOL_INDEX, BooleanValidator.stringToInt(request.getNew_cat_bool()));
			
			cs.registerOutParameter(RETURNING_ID_INDEX, OracleTypes.NUMERIC);
			
			CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Preparing to execute");
			
			
			cs.executeUpdate();
			
			result = cs.getInt(17);
			
			CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Query executed");
			
			return result;
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
