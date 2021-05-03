//package com.Allen.Finances.Expenses;
//
//import java.math.BigDecimal;
//import java.sql.CallableStatement;
//import java.sql.Connection;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.sql.Timestamp;
//import java.util.ArrayList;
//import java.util.List;
//
//import javax.naming.Context;
//import javax.naming.InitialContext;
//import javax.naming.NamingException;
//import javax.servlet.ServletException;
//import javax.sql.DataSource;
//
//import oracle.jdbc.OracleTypes;
//
//public class CallPostExpenseDAO {
//
//	private static final int EXPENSES_NAME_INDEX = 1;
//	private static final int PAID_INDEX = 2;
//	private static final int DUE_BY_INDEX = 3;
//	private static final int PAID_ON_INDEX = 4;
//	private static final int RECURRING_INDEX = 5;
//	private static final int AMOUNT_DUE_INDEX = 6;
//	private static final int CURSOR_INDEX = 7;
//	
//	private static final String DATASOURCE = "java:/comp/env/jdbc/finances";
//	
//	public CallPostExpenseDAO() {}
//	
//	public List<ExpensesModel> callPostExpense(ExpensesModel expense) throws ServletException {
//		
//		final String methodName = "callPostExpense()";
//		
//		final String query = "{call POSTExpense(?,?,?,?,?,?)}";
//		
//		Connection conn = null;
//		CallableStatement cs = null;
//		ResultSet rs = null;
//	
//		try{
//			conn = createConnection();
//			
//			cs = conn.prepareCall(query);
//			cs.setString(EXPENSES_NAME_INDEX, expense.getName());
//			cs.setBoolean(PAID_INDEX, expense.getPaid());
//			cs.setTimestamp(DUE_BY_INDEX, expense.getDue_by());
//			cs.setTimestamp(PAID_ON_INDEX, expense.getPaid_on());
//			cs.setBoolean(RECURRING_INDEX, expense.getRecurring());
//			cs.setBigDecimal(AMOUNT_DUE_INDEX, expense.getAmount_due());
//			cs.registerOutParameter(CURSOR_INDEX, OracleTypes.CURSOR);
//			
//			cs.execute();
//			
//			rs = (ResultSet) cs.getObject(7);
//			List<ExpensesModel> expenseResultList = new ArrayList<ExpensesModel>();
//			
//			//Loops over db results
//			while(rs.next()) {
//			    long id = rs.getLong("id");
//			    String name = rs.getString("name");
//			    Boolean paid = rs.getBoolean("paid");
//			    Timestamp due_by = rs.getTimestamp("due_by");
//			    Timestamp paid_on = rs.getTimestamp("paid_on");
//			    Boolean recurring = rs.getBoolean("recurring");
//			    BigDecimal amount_due = rs.getBigDecimal("amount_due");
//			    
//			    ExpensesModel obj = new ExpensesModel ();
//			    obj.setId(id);
//			    obj.setName(name);
//			    obj.setPaid(paid);
//			    obj.setDue_by(due_by);
//			    obj.setPaid_on(paid_on);
//			    obj.setRecurring(recurring);
//			    obj.setAmount_due(amount_due);
//			    
//			    expenseResultList.add(obj);
//			}
//			return expenseResultList;
//		}
//		catch(NamingException | SQLException e) {
//			throw new ServletException(e.getMessage());
//			//throw new ServletException(e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
//		}
//		finally {
//			cleanUpConnection(conn, cs, null);
//		}
//	
//	
//	}
//	//Closing connection objects, ignoring null provided parameters
//	private void cleanUpConnection(Connection conn, CallableStatement cs, ResultSet rs) {
//		final String methodName = "cleanUpConnection()";
//		try {
//			if(conn!=null)
//				conn.close();
//		}
//		catch(SQLException e){
//			
//		}
//		try {
//			if(cs != null)
//				cs.close();
//		}
//		catch(SQLException e) {
//			
//		}
//		try {
//			if(rs != null)
//				rs.close();
//		}
//		catch(SQLException e) {
//			
//		}
//	}
//	
//	//Opens connection to the jbdc defined by DATASOURCE
//	private Connection createConnection() throws NamingException, SQLException {
//		final Context ctx = new InitialContext();
//		final DataSource ds = (DataSource) ctx.lookup(DATASOURCE);
//		final Connection conn = ds.getConnection();
//		
//		return conn;
//	}
//
//}

