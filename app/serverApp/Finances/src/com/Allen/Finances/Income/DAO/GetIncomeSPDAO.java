package com.Allen.Finances.Income.DAO;

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
import com.Allen.Finances.Income.Models.GetIncomeHttpRequestModel;
import com.Allen.Finances.Income.Models.IncomeModel;

import oracle.jdbc.OracleTypes;

public class GetIncomeSPDAO {
	
	public static final String CLASS_NAME = GetIncomeSPDAO.class.getSimpleName();

	private static final int USERS_ID_INDEX = 1;
	private static final int CATEGORY_ID_INDEX = 2;
	private static final int ACCOUNTS_ID_INDEX = 3;
	private static final int NAME_INDEX = 4;
	private static final int RECIEVED_INDEX = 5;
	private static final int RECURRING_INDEX = 6;
	private static final int AMOUNT_EXPECTED_INDEX = 7;
	private static final int AMOUNT_ACTUAL_INDEX = 8;
	private static final int PERIOD_INDEX = 9;
	private static final int STARTDATE_INDEX = 10;
	private static final int ENDDATE_INDEX = 11;
	private static final int CURSOR_INDEX = 12;
	
	private static final String DATASOURCE = "java:/comp/env/jdbc/finances";
	
	public GetIncomeSPDAO() {}
	
	public List<IncomeModel> callGetIncome(GetIncomeHttpRequestModel request) throws ServletException {
		
		CatalinaSimpleLog.log("INFO", CLASS_NAME, "In GetIncomeSPDAO");
			
			final String methodName = "callGetIncome()";
			
			final String query = "{call GETIncome(?,?,?,?,?,?,?,?,?,?,?,?)}";
			
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
				cs.setInt(RECIEVED_INDEX, BooleanValidator.stringToInt(request.getRecieved()));
				cs.setInt(RECURRING_INDEX, BooleanValidator.stringToInt(request.getRecurring()));
				cs.setBigDecimal(AMOUNT_EXPECTED_INDEX, request.getAmount_expected());
				cs.setBigDecimal(AMOUNT_ACTUAL_INDEX, request.getAmount_actual());
				cs.setString(PERIOD_INDEX, request.getPeriod());
				cs.setTimestamp(STARTDATE_INDEX, request.getStartdate());
				cs.setTimestamp(ENDDATE_INDEX, request.getEnddate());
				cs.registerOutParameter(CURSOR_INDEX, OracleTypes.CURSOR);
				
				CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Preparing to execute");
				cs.execute();
				
				rs = (ResultSet) cs.getObject(12);
				List<IncomeModel> incomeResultList = new ArrayList<IncomeModel>();
				
				CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Query executed");
				
				while(rs.next()) {
					    
				    IncomeModel obj = new IncomeModel ();
				    obj.setId(rs.getInt("id"));
				    obj.setName(rs.getString("name"));
				    obj.setRecieved(rs.getBoolean("recieved"));
				    obj.setDue_on(rs.getString("due_on"));
				    obj.setRecieved_on(rs.getTimestamp("recieved_on"));
				    obj.setRecurring(rs.getBoolean("recurring"));
				    obj.setAmount_expected(rs.getBigDecimal("amount_expected"));
				    obj.setAmount_actual(rs.getBigDecimal("amount_actual"));
				    obj.setUsers_id(rs.getInt("users_id"));
				    obj.setCategory_id(rs.getInt("category_id"));
				    obj.setAccounts_id(rs.getInt("accounts_id"));
				    
	            	incomeResultList.add(obj);
	            	CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Returning result set of "+String.valueOf(incomeResultList.size()));
				}
				return incomeResultList;
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
