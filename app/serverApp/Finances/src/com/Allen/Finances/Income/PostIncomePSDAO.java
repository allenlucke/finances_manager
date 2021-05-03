package com.Allen.Finances.Income;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;
import javax.servlet.ServletException;

public class PostIncomePSDAO {

	public PostIncomePSDAO () {}
	
	
	public int callPostIncomePSt(IncomeModel income) throws ServletException, NamingException {
			
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String sqlQueryString = "INSERT INTO income(name, due_on, recurring, amount_expected, users_id, category_id) VALUES( ?,?,?,?,?,? )";
			
        String INPUTincome_name = income.getName();
        String INPUTdue_on = income.getDue_on();
        Boolean INPUTrecurring = income.getRecurring();
        BigDecimal INPUTamount_expected = income.getAmount_expected();
        int INPUTusers_id = income.getUsers_id();
        int INPUTcategory_id = income.getCategory_id();
        
			try{
				//DriverManager.registerDriver (new oracle.jdbc.OracleDriver());
	            Class.forName("oracle.jdbc.driver.OracleDriver");

	            connection = DriverManager.getConnection("jdbc:oracle:thin:@localhost:11521:xe","system","testing12345");
	            
	             
	            pstmt = connection.prepareStatement(sqlQueryString);
	
	            
	            pstmt.setString(1, INPUTincome_name);
	            pstmt.setString(2, INPUTdue_on);
	            pstmt.setBoolean(3, INPUTrecurring);
	            pstmt.setBigDecimal(4, INPUTamount_expected);
	            pstmt.setInt(5, INPUTusers_id);
	            pstmt.setInt(6, INPUTcategory_id);
	            
	            
	            //rs=pstmt.executeQuery();
	            int status = pstmt.executeUpdate();
	            
	            List<IncomeModel> incomeResultList = new ArrayList<IncomeModel>();
	            
				return status;
			}
			catch(SQLException | ClassNotFoundException e) {
				throw new ServletException(e.getMessage());
			}
		            
	        finally {
	            try {
	                pstmt.close();
	                connection.close();
	                
	            } catch (Exception e) {
	            	
	                e.printStackTrace();
	            }
	        }
	    }
	}

	
