//package com.Allen.Finances.Security;
//
//import java.io.IOException;
//import java.lang.reflect.Method;
//import java.util.Arrays;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//import java.util.StringTokenizer;
//
//import javax.annotation.security.DenyAll;
//import javax.annotation.security.PermitAll;
//import javax.annotation.security.RolesAllowed;
//import javax.ws.rs.container.ContainerRequestContext;
//import javax.ws.rs.container.ResourceInfo;
//import javax.ws.rs.core.Context;
//import javax.ws.rs.core.MultivaluedMap;
//import javax.ws.rs.core.Response;
//import javax.ws.rs.ext.Provider;
//
//import org.glassfish.jersey.internal.util.Base64;
//
////import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
////import com.sun.org.apache.xml.internal.security.utils.Base64;
//
//@Provider
//public class BasicAuthFilter implements javax.ws.rs.container.ContainerRequestFilter
//{
//
//	private static final String CLASS_NAME = BasicAuthFilter.class.getSimpleName();
//	
//	@Context
//	private ResourceInfo resourceInfo;
//	
//	private static final String AUTHORIZATION_PROPERTY = "Authorization";
//	private static final String AUTHENTICATION_SCHEME = "Basic";
//
//	@Override
//	public void filter(ContainerRequestContext requestContext) throws IOException 
//	{
//		Method method = resourceInfo.getResourceMethod();
//		//Access allowed for all
//		if( ! method.isAnnotationPresent(PermitAll.class))
//		{
//			//Access denied for all
//			if(method.isAnnotationPresent(DenyAll.class))
//			{
//				requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).entity("Access blocked for all users !!").build());
//				
//				return;
//			}
//			
//			//Get Request headers
//			final MultivaluedMap<String, String> headers = requestContext.getHeaders();
//			
//			//Fetch authorization header
//			final List<String> authorization = headers.get(AUTHORIZATION_PROPERTY);
//			
//			//If no authorization info is present, block access
//			if(authorization == null || authorization.isEmpty())
//			{
//				requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).entity("You cannot access this resource").build());
//				return;
//			}
//			
//			//Get encoded username and password
//			final String encodedUserPassword = authorization.get(0).replaceFirst(AUTHENTICATION_SCHEME + " ", "");
//			
//			//Decode username and password tokens
//			String usernameAndPassword = new String(Base64.decode(encodedUserPassword.getBytes()));
//			
//			//Split username and password tokens
//			final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
//			final String username = tokenizer.nextToken();
//			final String password = tokenizer.nextToken();
//			
//			//Verifying username and password
//			System.out.println(username);
//			System.out.println(password);
//			
//			//Verify user access
//			if(method.isAnnotationPresent(RolesAllowed.class))
//			{
//				RolesAllowed rolesAnnotation = method.getAnnotation(RolesAllowed.class);
//				Set<String> rolesSet = new HashSet<String>(Arrays.asList(rolesAnnotation.value()));
//				
//				//Is user valid?
//				if( ! isUserAllowed(username, password, rolesSet))
//				{
//					requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).entity("You cannot access this resource").build());
//					return;
//				}
//			}
//		}
//	}
//	
//	private boolean isUserAllowed(final String username, final String password, final Set<String> rolesSet)
//	{
//		boolean isAllowed = false;
//		
//		//Step 1. Fetch password from DB and match with password in argument
//		//If both match then get the defined role for the user form the DB and continue, else return isAllowed [false]
//		//Access the DB and do this part yourself
//		//String userRole = userMgr.getUserRole(username);
//		
//		if(username.equals("tempUserName") && password.equals("password"))
//		{
//			String userRole = "ADMIN";
//			
//			//Step 2. Verify user role
//			if(rolesSet.contains(userRole))
//			{
//				isAllowed = true;
//			}
//		}
//		return isAllowed;
//	}
//	
//}
