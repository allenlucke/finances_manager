package com.Allen.Finances.Expenses.Services;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.Allen.Finances.Bean.CatalinaSimpleLog;
import com.Allen.Finances.Bean.JsonConverter;
import com.Allen.Finances.Expenses.DAO.GetExpensesCategoriesDao;
import com.Allen.Finances.Expenses.DAO.GetExpensesSPDAO;
import com.Allen.Finances.Expenses.DAO.GetExpenses_CategoriesSPDAO;
import com.Allen.Finances.Expenses.Models.ExpensesCategoriesModel;
import com.Allen.Finances.Expenses.Models.ExpensesModel;
import com.Allen.Finances.Expenses.Models.GetExpensesHttpRequestModel;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path( "/SP" )
public class GetExpensesSPService {
	
	public static final String CLASS_NAME = GetExpensesSPService.class.getSimpleName();
	
	//REST service to GET expenses
	//Is technically considered a POST request due to the request body requirement.
	@POST
	@Path("/GetExpensesSP")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)	
	public Response getExpensesSP(@Context HttpServletRequest request) throws ServletException, IOException, NamingException, 
	SQLException, JsonMappingException{
			    

		final String methodName = "getExpensesSP()";
		
		CatalinaSimpleLog.log("INFO", CLASS_NAME, " In " + methodName);
		
		//Checks to ensure request is valid JSON
		if( !request.getContentType().equalsIgnoreCase(MediaType.APPLICATION_JSON)) {
			CatalinaSimpleLog.log("SEVERE", CLASS_NAME, methodName, "Unsuported request type " + request.getContentType() + "  Expected " + MediaType.APPLICATION_JSON);
			return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).build();
		}
		
		String requestBdyJson;	
		List<ExpensesModel> expResultList;
		List<ExpensesCategoriesModel> expCatResultList;
		String responseJSON;

		//Creates request body out of request input stream
		requestBdyJson = JsonConverter.toString(request.getInputStream(), "UTF-8");
		//Maps JSON object to java Expenses POJO
		ObjectMapper mapper = new ObjectMapper();
		GetExpensesHttpRequestModel requestPojo = mapper.readValue(requestBdyJson, GetExpensesHttpRequestModel.class);

		//Make call to GETExpensesSPDAO
		try {
			//Calls to GET method sending Expenses POJO
			GetExpensesSPDAO dao = new GetExpensesSPDAO(); 
			//Returns resultList from callGetExpenses
			expResultList = dao.callGetExpenses(requestPojo);
		}
		catch( Exception e ) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
		}
		
		//Make call to GETExpenses_CategoriesSPDAO
		try {		
			//Calls to GET method sending Expenses POJO
			GetExpenses_CategoriesSPDAO dao = new GetExpenses_CategoriesSPDAO(); 
			//Returns resultList from callGetExpenses
			expCatResultList = dao.callGetExpensesCategories(requestPojo);
		}
		catch( Exception e ) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
		}
		

		//Add expenses List to expenses_categories list	
		for (ExpensesCategoriesModel expCatObj: expCatResultList) {
			int catId = expCatObj.getId();
			
			List<ExpensesModel> matchingExpenseList = new ArrayList<ExpensesModel>();

			for (ExpensesModel expObj: expResultList) {
				
				int expid = expCatObj.getId();
				if( expCatObj.getId() ==  expObj.getCategory_id()) {
//					System.out.println("Do stuff");
//					System.out.println("expCatObj.getId()  " + expCatObj.getId());
//					System.out.println("expObj.getCategory_id()  " + expObj.getCategory_id());
					
					matchingExpenseList.add(expObj);
				}
				
			}
			expCatObj.setExpenses(matchingExpenseList);
			
		}
		
		responseJSON = mapper.writeValueAsString(expCatResultList);
		return Response.status( Response.Status.OK ).entity(responseJSON).build();
	}
	
}
