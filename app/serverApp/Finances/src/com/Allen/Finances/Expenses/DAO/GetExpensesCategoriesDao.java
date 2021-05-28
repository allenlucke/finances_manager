package com.Allen.Finances.Expenses.DAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.sql.DataSource;

import com.Allen.Finances.Bean.CatalinaSimpleLog;
import com.Allen.Finances.Expenses.Models.ExpensesCategoriesModel;


public class GetExpensesCategoriesDao {

	public static final String CLASS_NAME = GetExpensesCategoriesDao.class.getSimpleName();

	public GetExpensesCategoriesDao() {}
	
	private static final String DATASOURCE = "java:/comp/env/jdbc/finances";
	
	public List<ExpensesCategoriesModel> getCatData(int users_id) throws ServletException {
		
		
		final String methodName = "getCatData()";
		
		CatalinaSimpleLog.log("INFO", CLASS_NAME, " In " + methodName);
		
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String sqlQueryString = "SELECT id, name FROM expenses_categories WHERE users_id = ?";
        
		try{
			CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Creating connection");
  
            conn = createConnection();
            
            pstmt = conn.prepareStatement(sqlQueryString); 
            
            pstmt.setInt(1, users_id);
            
            CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Preparing to execute");
            
            rs=pstmt.executeQuery();
            
			CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Query executed");
            
            List<ExpensesCategoriesModel> expCatResultList = new ArrayList<ExpensesCategoriesModel>();
            
            while(rs.next()) {
			    
            	ExpensesCategoriesModel obj = new ExpensesCategoriesModel ();
			    obj.setId(rs.getInt("id"));
			    obj.setName(rs.getString("name"));
			    
			    expCatResultList.add(obj);
            	}
            
            CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Query returned a list of " + expCatResultList.size() + " results");
            
			return expCatResultList;
		}
		catch(SQLException | NamingException e) {
			CatalinaSimpleLog.log("SEVERE", CLASS_NAME, methodName, "Has thrown an exception: " + e.getMessage());
			throw new ServletException(e.getMessage());
		}
	            
        finally {
			cleanUpConnection(conn, pstmt, rs);
        }
		
	}
	//Closing connection objects, ignoring null provided parameters
	private void cleanUpConnection(Connection conn, PreparedStatement pstmt, ResultSet rs) {
		final String methodName = "cleanUpConnection()";
		try {
			if(conn!=null)
				conn.close();
		}
		catch(SQLException e){
			
		}
		try {
			if(pstmt != null)
				pstmt.close();
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
