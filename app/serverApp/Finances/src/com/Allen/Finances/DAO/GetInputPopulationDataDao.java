package com.Allen.Finances.DAO;

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
import com.Allen.Finances.Period.Models.GetPeriodHttpRequestModel;
import com.Allen.Finances.Period.Models.PeriodModel;


public class GetInputPopulationDataDao {

	public static final String CLASS_NAME = GetInputPopulationDataDao.class.getSimpleName();

	public GetInputPopulationDataDao () {}
	
	private static final String DATASOURCE = "java:/comp/env/jdbc/finances";
	
	public List<PeriodModel> getPeriodData(GetPeriodHttpRequestModel request) throws ServletException {
		
		CatalinaSimpleLog.log("INFO", CLASS_NAME, "In GetInputPopulationDataDao");
		
		final String methodName = "getPeriodData()";
		
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String sqlQueryString = "SELECT * FROM period WHERE users_id = ?";
        
		try{
			CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Creating connection");
  
            conn = createConnection();
            
            pstmt = conn.prepareStatement(sqlQueryString); 
            
            pstmt.setInt(1, request.getUsers_id());
            
            CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Preparing to execute");
            
            rs=pstmt.executeQuery();
            
			CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Query executed");
            
            List<PeriodModel> periodResultList = new ArrayList<PeriodModel>();
            
            while(rs.next()) {
			    
            	PeriodModel obj = new PeriodModel ();
			    obj.setId(rs.getInt("id"));
			    obj.setStart_date(rs.getString("start_date"));
			    obj.setEnd_date(rs.getString("end_date"));
			    obj.setUsers_id(rs.getInt("users_id"));
			    
            	periodResultList.add(obj);
            	}
            
            CatalinaSimpleLog.log("INFO", CLASS_NAME, methodName, "Query returned a list of " + periodResultList.size() + " results");
            
			return periodResultList;
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
