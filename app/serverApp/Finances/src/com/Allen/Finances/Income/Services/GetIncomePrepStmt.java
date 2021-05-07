package com.Allen.Finances.Income.Services;
//package com.Allen.Finances.Income;
//
//import java.io.IOException;
//import java.sql.SQLException;
//import java.util.List;
//
//import javax.naming.NamingException;
//import javax.servlet.ServletException;
//import javax.servlet.http.HttpServletRequest;
//import javax.ws.rs.Consumes;
//import javax.ws.rs.GET;
//import javax.ws.rs.Path;
//import javax.ws.rs.Produces;
//import javax.ws.rs.core.Context;
//import javax.ws.rs.core.MediaType;
//import javax.ws.rs.core.Response;
//
//import com.Allen.Finances.Bean.JsonConverter;
//import com.fasterxml.jackson.databind.JsonMappingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//@Path("/PS")
//public class GetIncomePrepStmt {
//	
//	public static final String CLASS_NAME = GetIncomePrepStmt.class.getSimpleName();
//
//	@GET
//	@Path("/GetIncomePst")
//	@Consumes(MediaType.APPLICATION_JSON)
//	@Produces(MediaType.APPLICATION_JSON)
//	public Response getIncomePst(@Context HttpServletRequest request) throws ServletException, IOException, NamingException, 
//	SQLException, JsonMappingException /*SAXException*/ {
//
//	    
//		final String methodName = "getIncomePst()";
//		
//		//Checks to ensure request is valid JSON
////		if( !request.getContentType().equalsIgnoreCase(MediaType.APPLICATION_JSON)) {
////			return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).build();
////		}
//		
//		String requestBdyJson;
//		
//		String responseJSON;
//
////		List <Income> response;
//		
//		try {
//			//Creates request body out of request input stream
//			requestBdyJson = JsonConverter.toString(request.getInputStream(), "UTF-8");
//			
//			//Maps JSON object to java Income POJO
//			ObjectMapper mapper = new ObjectMapper();
//			//Income requestPojo = mapper.readValue(requestBdyJson, Income.class);
//			
//			//Calls to POST method sending Income POJO
//			GetIncomePSDAO dao = new GetIncomePSDAO(); 
//			//Returns resultList from callPostIncome
//			List<IncomeModel> resultList = dao.callGetAllIncomePSt();
//			
//
//			responseJSON = mapper.writeValueAsString(resultList);
//		}
//		catch( Exception e ) {
//			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).build();
//		}
//		
//		
//		return Response.status( Response.Status.OK ).entity(responseJSON).build();
//		
//    }
//}

