package com.Allen.Finances.Expenses.Services;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.Allen.Finances.Bean.CatalinaSimpleLog;
import com.Allen.Finances.Bean.JsonConverter;
import com.Allen.Finances.Expenses.DAO.GetExpensesSPDAO;
import com.Allen.Finances.Expenses.Models.ExpensesModel;
import com.Allen.Finances.Expenses.Models.GetExpensesHttpRequestModel;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path( "/SP" )
public class GetExpensesSPService {
	
	public static final String CLASS_NAME = GetExpensesSPService.class.getSimpleName();
	
	//REST service to GET expenses
	@GET
	@Path("/GetExpensesSP")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	
	public Response getIncomeStP(@Context HttpServletRequest request) throws ServletException, IOException, NamingException, 
	SQLException, JsonMappingException{
			    
		CatalinaSimpleLog.log("INFO", CLASS_NAME, "In GetExpensesSp");

		
		final String methodName = "getExpensesStP()";
		
		//Checks to ensure request is valid JSON
		if( !request.getContentType().equalsIgnoreCase(MediaType.APPLICATION_JSON)) {
			CatalinaSimpleLog.log("SEVERE", CLASS_NAME, methodName, "Unsuported request type " + request.getContentType() + "  Expected " + MediaType.APPLICATION_JSON);
			return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).build();
		}
		
		String requestBdyJson;		
		String responseJSON;

//		List <Expenses> response;
		
		try {
			//Creates request body out of request input stream
			requestBdyJson = JsonConverter.toString(request.getInputStream(), "UTF-8");
			
			//Maps JSON object to java Expenses POJO
			ObjectMapper mapper = new ObjectMapper();
			GetExpensesHttpRequestModel requestPojo = mapper.readValue(requestBdyJson, GetExpensesHttpRequestModel.class);
			
			//Calls to GET method sending Expenses POJO
			GetExpensesSPDAO dao = new GetExpensesSPDAO(); 
			//Returns resultList from callGetExpenses
			List<ExpensesModel> resultList = dao.callGetExpenses(requestPojo);
			

			responseJSON = mapper.writeValueAsString(resultList);
		}
		catch( Exception e ) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
		}
		
		
		return Response.status( Response.Status.OK ).entity(responseJSON).build();
	}
}
