package com.Allen.Finances.Services;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.Allen.Finances.Bean.CatalinaSimpleLog;

@Path("/hello")
public class HelloWorld {
	public static final String CLASS_NAME = HelloWorld.class.getSimpleName();

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response sayHtmlHello() {
		
		CatalinaSimpleLog.log("INFO", CLASS_NAME, "In hello world");
		
		
		String hello = "{\n" + 
				"    \"hello\": \"Hello from finances server!\"\n" + 
				"}";
		
		return Response.status( Response.Status.OK ).entity(hello).build();
	}
}

