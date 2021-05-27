package com.Allen.Finances.Period.Services;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.Allen.Finances.Period.DAO.GetPeriodInputDataDao;
import com.Allen.Finances.Period.Models.PeriodModel;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path("/InputPopData")
public class GetPeriodInputDataService {

	public static final String CLASS_NAME = GetPeriodInputDataService.class.getSimpleName();
	
	@GET
	@Path("/PeriodData")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPeriodData(@QueryParam("id") int id) {
		
		final String methodName = "getPeriodData()";
				
		String responseJSON;
		
		try {

			ObjectMapper mapper = new ObjectMapper();

			GetPeriodInputDataDao dao = new GetPeriodInputDataDao(); 
			
			List<PeriodModel> result = dao.getPeriodData(id);
			
			responseJSON = mapper.writeValueAsString(result);
		}
		catch( Exception e ) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
		}	
		
		return Response.status( Response.Status.OK ).entity(responseJSON).build();
	}

}
