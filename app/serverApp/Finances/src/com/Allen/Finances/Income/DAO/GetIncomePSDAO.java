package com.Allen.Finances.Income.DAO;
//package com.Allen.Finances.Income;
//
//import java.math.BigDecimal;
////import java.sql.CallableStatement;
//import java.sql.Connection;
//import java.sql.DriverManager;
//import java.sql.PreparedStatement;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.sql.Timestamp;
//import java.util.ArrayList;
//import java.util.List;
//
//import javax.naming.NamingException;
//import javax.servlet.ServletException;
//
//public class GetIncomePSDAO {
//	
//	public GetIncomePSDAO () {}
//	
//	
//	public List<IncomeModel> callGetAllIncomePSt() throws ServletException, NamingException {
//			
//        Connection connection = null;
//        PreparedStatement pstmt = null;
//        ResultSet rs = null;
//        String sqlQueryString = "SELECT * FROM income";
//			
//			try{
//				//DriverManager.registerDriver (new oracle.jdbc.OracleDriver());
//	            Class.forName("oracle.jdbc.driver.OracleDriver");
//
//	            connection = DriverManager.getConnection("jdbc:oracle:thin:@localhost:11521:xe","system","testing12345");
//	                         
//	            pstmt = connection.prepareStatement(sqlQueryString);
//	            
//	            rs=pstmt.executeQuery();
//	            
//	            List<IncomeModel> incomeResultList = new ArrayList<IncomeModel>();
//	
//	            while(rs.next()) {
//				    
//				    IncomeModel obj = new IncomeModel ();
//				    obj.setId(rs.getInt("id"));
//				    obj.setName(rs.getString("name"));
//				    obj.setRecieved(rs.getBoolean("recieved"));
//				    obj.setDue_on(rs.getString("due_on"));
//				    obj.setRecieved_on(rs.getTimestamp("recieved_on"));
//				    obj.setRecurring(rs.getBoolean("recurring"));
//				    obj.setAmount_expected(rs.getBigDecimal("amount_expected"));
//				    obj.setAmount_actual(rs.getBigDecimal("amount_actual"));
//				    obj.setUsers_id(rs.getInt("users_id"));
//				    
//	            	incomeResultList.add(obj);
//	            	}
//	            
//				return incomeResultList;
//			}
//			catch(SQLException | ClassNotFoundException e) {
//				throw new ServletException(e.getMessage());
//			}
//		            
//	        finally {
//	            try {
//	                pstmt.close();
//	                connection.close();
//	                
//	            } catch (Exception e) {
//	            	
//	                e.printStackTrace();
//	            }
//	        }
//	    }
//	}

