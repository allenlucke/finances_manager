package com.Allen.Finances.Income;

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

import java.util.logging.*;

import org.glassfish.hk2.utilities.reflection.Logger;

import com.Allen.Finances.Bean.CatalinaSimpleLog;
import com.Allen.Finances.Bean.JsonConverter;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path( "/SP" )
public class GetIncomeSPService {
	
	public static final String CLASS_NAME = GetIncomeSPService.class.getSimpleName();
	
	//REST service to GET income
	@GET
	@Path("/GetIncomeSP")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	
	public Response getIncomeStP(@Context HttpServletRequest request) throws ServletException, IOException, NamingException, 
	SQLException, JsonMappingException{
			    
		CatalinaSimpleLog.log("INFO", CLASS_NAME, "In GetIncomeSp");

		
		final String methodName = "getIncomeStP()";
		
		//Checks to ensure request is valid JSON
		if( !request.getContentType().equalsIgnoreCase(MediaType.APPLICATION_JSON)) {
			CatalinaSimpleLog.log("SEVERE", CLASS_NAME, methodName, "Unsuported request type " + request.getContentType() + "  Expected " + MediaType.APPLICATION_JSON);
			return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).build();
		}
		
		String requestBdyJson;		
		String responseJSON;

//		List <Income> response;
		
		try {
			//Creates request body out of request input stream
			requestBdyJson = JsonConverter.toString(request.getInputStream(), "UTF-8");
			
			//Maps JSON object to java Income POJO
			ObjectMapper mapper = new ObjectMapper();
			GetIncomeHttpRequestModel requestPojo = mapper.readValue(requestBdyJson, GetIncomeHttpRequestModel.class);
			
			//Calls to GET method sending Income POJO
			GetIncomeSPDAO dao = new GetIncomeSPDAO(); 
			//Returns resultList from callGetIncome
			List<IncomeModel> resultList = dao.callGetIncome(requestPojo);
			

			responseJSON = mapper.writeValueAsString(resultList);
		}
		catch( Exception e ) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
		}
		
		
		return Response.status( Response.Status.OK ).entity(responseJSON).build();
	}
}
