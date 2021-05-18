package com.Allen.MCORSFilter;



import javax.ws.rs.container.ContainerResponseFilter;




import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;

public class CorsResponseFilter implements ContainerResponseFilter {

@Override
public void filter(ContainerRequestContext requestContext,   ContainerResponseContext responseContext)
    throws IOException {
        responseContext.getHeaders().add("Access-Control-Allow-Origin","*");
        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");

  }
}