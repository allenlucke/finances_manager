package com.Allen.SpringFinancesServer.Security;

import java.io.IOException;
import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint, Serializable {

    private static final String CLASS_NAME = "JwtAuthenticationEntryPoint --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    private static final long serialVersionUID = -7858869558953243875L;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        final String methodName = "commence() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }
}

