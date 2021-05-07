package com.Allen.Finances.Expenses.Services;

import java.io.IOException;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.Allen.Finances.Bean.CatalinaSimpleLog;
import com.Allen.Finances.Bean.JsonConverter;
import com.Allen.Finances.Expenses.DAO.PostExpensesSPDAO;
import com.Allen.Finances.Expenses.Models.PostExpensesHttpRequestModel;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path("SP")
public class PostExpensesSPService {


	public static final String CLASS_NAME = PostExpensesSPService.class.getSimpleName();
	
	//REST Service to Post Income
	@POST
	@Path("/PostExpensesSP")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	
	public Response postExpensesStP(@Context HttpServletRequest request)throws ServletException, IOException, NamingException, 
	SQLException, JsonMappingException{
			    
		CatalinaSimpleLog.log("INFO", CLASS_NAME, "In PostExpensesStP");
		
		final String methodName = "postExpensesStP()";
		
		//Checks to ensure request is valid JSON
		if( !request.getContentType().equalsIgnoreCase(MediaType.APPLICATION_JSON)) {
			CatalinaSimpleLog.log("SEVERE", CLASS_NAME, methodName, "Unsuported request type " + request.getContentType() + "  Expected " + MediaType.APPLICATION_JSON);
			return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).build();
		}
		String requestBdyJson;		
		String responseJSON;
		
		try {
			//Creates request body out of request input stream
			requestBdyJson = JsonConverter.toString(request.getInputStream(), "UTF-8");
			
			//Maps JSON object to java Income POJO
			ObjectMapper mapper = new ObjectMapper();
			PostExpensesHttpRequestModel requestPojo = mapper.readValue(requestBdyJson, PostExpensesHttpRequestModel.class);
			
			//Calls to POST method sending Expense POJO
			PostExpensesSPDAO dao = new PostExpensesSPDAO(); 
			
			//Returns id of newly created income item
			int result = dao.callPostExpenses(requestPojo);
			
			responseJSON = mapper.writeValueAsString(result);
		}
		catch( Exception e ) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
		}	
		
		return Response.status( Response.Status.OK ).entity(responseJSON).build();
	}
}
