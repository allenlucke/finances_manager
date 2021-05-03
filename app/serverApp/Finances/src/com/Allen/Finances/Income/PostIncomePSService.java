package com.Allen.Finances.Income;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

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
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;


@Path( "/PS" )
public class PostIncomePSService {


	public static final String CLASS_NAME = PostIncomePSService.class.getSimpleName();
	
	//REST service to add income
	@POST
	@Path("/AddIncome")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response postIncomePst(@Context HttpServletRequest request) throws ServletException, IOException, NamingException, 
			SQLException, JsonMappingException {
		
		CatalinaSimpleLog.log("INFO", CLASS_NAME, "AddIncomePrepStmt");
		
		final String methodName = "postIncomePst()";
		
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
			IncomeModel requestPojo = mapper.readValue(requestBdyJson, IncomeModel.class);
			
			//Calls to POST method sending Income POJO
			PostIncomePSDAO dao = new PostIncomePSDAO(); 
			//Returns resultList from callPostIncome
			int status = dao.callPostIncomePSt(requestPojo);
			//List<Income> resultList = dao.callPostIncomePSt(requestPojo);
			

//			responseJSON = mapper.writeValueAsString(resultList);
			responseJSON = mapper.writeValueAsString(status);
		}
		catch( Exception e ) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
		}
		
		
		return Response.status( Response.Status.OK ).entity(responseJSON).build();
	}
	
}

