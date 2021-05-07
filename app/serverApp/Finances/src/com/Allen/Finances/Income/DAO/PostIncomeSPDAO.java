package com.Allen.Finances.Income.DAO;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.sql.DataSource;

import com.Allen.Finances.Bean.BooleanValidator;
import com.Allen.Finances.Bean.CatalinaSimpleLog;
import com.Allen.Finances.Income.Models.PostIncomeHttpRequestModel;

import oracle.jdbc.OracleTypes;

public class PostIncomeSPDAO {

	public static final String CLASS_NAME = PostIncomeSPDAO.class.getSimpleName();
	
	private static final int NAME_INDEX = 1;
	private static final int DUE_ON_INDEX = 2;
	private static final int RECURRING_INDEX = 3;
	private static final int AMOUNT_EXPECTED_INDEX = 4;
	private static final int USERS_ID_INDEX = 5;	
	private static final int CATEGORY_ID_INDEX = 6;	
	private static final int CAT_NAME_INDEX = 7;
	private static final int CAT_DUE_BY_INDEX = 8;
	private static final int CAT_RECURRING_INDEX = 9;	
	private static final int CAT_AMOUNT_DUE_INDEX = 10;
	private static final int PERIOD_INDEX = 11;
	private static final int NEW_CAT_BOOL_INDEX = 12;
	
	private static final int RETURNING_ID_INDEX = 13;
    
	
	private static final String DATASOURCE = "java:/comp/env/jdbc/finances";
	
	public PostIncomeSPDAO() {}
	
	public int callPostIncome(PostIncomeHttpRequestModel request) throws ServletException {
//	public List<IncomeModel> callPostIncome(PostIncomeHttpRequestModel request) throws ServletException {
		
		CatalinaSimpleLog.log("INFO", CLASS_NAME, "In PostIncomeSPDAO");
		
		final String methodName = "callPostIncome()";
		
		final String query = "{call POSTIncome(?,?,?,?,?,?,?,?,?,?,?,?,?)}";

		Connection conn = null;
		CallableStatement cs = null;
//		ResultSet rs = null;
		int result;
		
		try{
			CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Creating connection");
			conn = createConnection();
		
			cs = conn.prepareCall(query);
			cs.setString(NAME_INDEX, request.getName());
			cs.setTimestamp(DUE_ON_INDEX, request.getDue_on());							
			cs.setInt(RECURRING_INDEX, BooleanValidator.stringToInt(request.getRecurring()));				
			cs.setBigDecimal(AMOUNT_EXPECTED_INDEX, request.getAmount_expected());
			cs.setString(USERS_ID_INDEX, request.getUsers_id());
			cs.setString(CATEGORY_ID_INDEX, request.getCategory_id());
			cs.setString(CAT_NAME_INDEX, request.getCategory_name());
			cs.setTimestamp(CAT_DUE_BY_INDEX, request.getCat_due_by());			
			cs.setInt(CAT_RECURRING_INDEX, BooleanValidator.stringToInt(request.getCat_recurring()));
			cs.setBigDecimal(CAT_AMOUNT_DUE_INDEX, request.getCat_amount_due());		
			cs.setString(PERIOD_INDEX, request.getPeriod());
			cs.setInt(NEW_CAT_BOOL_INDEX, BooleanValidator.stringToInt(request.getNew_cat_bool()));
			cs.registerOutParameter(RETURNING_ID_INDEX, OracleTypes.NUMERIC);
			
			CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Preparing to execute");
//			cs.execute();
			cs.executeUpdate();
			
//			rs = (ResultSet) cs.getObject(13);
			
			result = cs.getInt(13);
//			List<IncomeModel> incomeResultList = new ArrayList<IncomeModel>();
			
			CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Query executed");
			
			
			
//			while(rs.next()) {
//			    
//			    IncomeModel obj = new IncomeModel ();
//			    obj.setId(rs.getInt("id"));
//			    obj.setId(rs.getInt("income_id"));
//			    obj.setName(rs.getString("name"));
//			    obj.setRecieved(rs.getBoolean("recieved"));
//			    obj.setDue_on(rs.getString("due_on"));
//			    obj.setRecieved_on(rs.getTimestamp("recieved_on"));
//			    obj.setRecurring(rs.getBoolean("recurring"));
//			    obj.setAmount_expected(rs.getBigDecimal("amount_expected"));
//			    obj.setAmount_actual(rs.getBigDecimal("amount_actual"));
//			    obj.setUsers_id(rs.getInt("users_id"));
//			    obj.setCategory_id(rs.getInt("category_id"));
//			    obj.setAccounts_id(rs.getInt("accounts_id"));
//			    
//            	incomeResultList.add(obj);
//            	CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Returning result set of "+String.valueOf(incomeResultList.size()));
//			}
//			return incomeResultList;
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
