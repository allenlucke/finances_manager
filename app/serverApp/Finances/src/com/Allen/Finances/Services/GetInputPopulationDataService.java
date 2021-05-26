package com.Allen.Finances.Services;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.Allen.Finances.Bean.JsonConverter;
import com.Allen.Finances.DAO.GetInputPopulationDataDao;
import com.Allen.Finances.Period.Models.GetPeriodHttpRequestModel;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path("/InputPopData")
public class GetInputPopulationDataService {

	public static final String CLASS_NAME = GetInputPopulationDataService.class.getSimpleName();
	
	@GET
	@Path("/PeriodData")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPeriodData(@Context HttpServletRequest request) {
		
		final String methodName = "getPeriodData()";
		
		//Checks to ensure request is valid JSON
		if( !request.getContentType().equalsIgnoreCase(MediaType.APPLICATION_JSON)) {
			return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).build();
		}
		
		String requestBdyJson;		
		String responseJSON;
		
		try {
			//Creates request body out of request input stream
			requestBdyJson = JsonConverter.toString(request.getInputStream(), "UTF-8");
			
			//Maps JSON object to java Income POJO
			ObjectMapper mapper = new ObjectMapper();
			GetPeriodHttpRequestModel requestPojo = mapper.readValue(requestBdyJson, GetPeriodHttpRequestModel.class);
			
			//Calls to POST method sending Expense POJO
			GetInputPopulationDataDao dao = new GetInputPopulationDataDao(); 
			
			//Returns id of newly created income item
			List result = dao.getPeriodData(requestPojo);
			
			responseJSON = mapper.writeValueAsString(result);
		}
		catch( Exception e ) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
		}	
		
		return Response.status( Response.Status.OK ).entity(responseJSON).build();
	}

}
