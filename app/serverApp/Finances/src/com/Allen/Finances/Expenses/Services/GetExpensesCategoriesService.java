package com.Allen.Finances.Expenses.Services;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.Allen.Finances.Bean.CatalinaSimpleLog;
import com.Allen.Finances.Expenses.DAO.GetExpensesCategoriesDao;
import com.Allen.Finances.Expenses.Models.ExpensesCategoriesModel;
import com.fasterxml.jackson.databind.ObjectMapper;


@Path("/InputPopData")
public class GetExpensesCategoriesService {

	public static final String CLASS_NAME = GetExpensesCategoriesService.class.getSimpleName();
	
	@GET
	@Path("/ExpenseCatData")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getExpenseCategoriesData(@QueryParam("id") int users_id) {
		
		final String methodName = "GetExpensesCategoriesService()";
		
		CatalinaSimpleLog.log("INFO", CLASS_NAME, " In " + methodName);
		
		String responseJSON;
		
		try {

			ObjectMapper mapper = new ObjectMapper();

			GetExpensesCategoriesDao dao = new GetExpensesCategoriesDao(); 
			
			List<ExpensesCategoriesModel> result = dao.getCatData(users_id);
			
			responseJSON = mapper.writeValueAsString(result);
		}
		catch( Exception e ) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
		}	
		
		return Response.status( Response.Status.OK ).entity(responseJSON).build();
	}

}
