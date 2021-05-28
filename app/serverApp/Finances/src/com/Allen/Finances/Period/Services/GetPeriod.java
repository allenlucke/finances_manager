package com.Allen.Finances.Period.Services;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.Allen.Finances.Bean.CatalinaSimpleLog;
import com.Allen.Finances.Period.DAO.GetPeriodDao;
import com.Allen.Finances.Period.Models.PeriodModel;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path("/InputPopData")
public class GetPeriod {

	public static final String CLASS_NAME = GetPeriod.class.getSimpleName();
	
	@GET
	@Path("/PeriodData")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPeriodData(@QueryParam("id") int users_id) {
		
		final String methodName = "getPeriodData()";
		
		CatalinaSimpleLog.log("INFO", CLASS_NAME, " In " + methodName);
				
		String responseJSON;
		
		try {

			ObjectMapper mapper = new ObjectMapper();

			GetPeriodDao dao = new GetPeriodDao(); 
			
			List<PeriodModel> result = dao.getPeriodData(users_id);
			
			responseJSON = mapper.writeValueAsString(result);
		}
		catch( Exception e ) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
		}	
		
		return Response.status( Response.Status.OK ).entity(responseJSON).build();
	}

}
