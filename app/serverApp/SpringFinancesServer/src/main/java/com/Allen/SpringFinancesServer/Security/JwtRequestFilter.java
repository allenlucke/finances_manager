package com.Allen.SpringFinancesServer.Security;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.Allen.SpringFinancesServer.User.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final String CLASS_NAME = "JwtRequestFilter --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JwtUserDetailsService jwtUserDetailsService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

//    @Autowired
//    private UserDao userDao;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String methodName = "doFilterInternal() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        final String requestTokenHeader = request.getHeader("Authorization");

        String username = null;
        String jwtToken = null;
        // JWT Token is in the form "Bearer token". Remove Bearer word and get
        // only the Token
        LOGGER.info(CLASS_NAME + methodName + "Preparing to parse JWT Token");
        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            jwtToken = requestTokenHeader.substring(7);
            try {
                username = jwtTokenUtil.getUsernameFromToken(jwtToken);
            } catch (IllegalArgumentException e) {
                LOGGER.warn(CLASS_NAME + methodName + "Unable to get JWT Token");
            } catch (ExpiredJwtException e) {
                LOGGER.warn(CLASS_NAME + methodName + "JWT Token has expired");
            }
        } else {
            LOGGER.warn(CLASS_NAME + methodName + requestTokenHeader);
            LOGGER.warn(CLASS_NAME + methodName + "JWT Token does not begin with Bearer String");
        }

        // Once we get the token validate it.
        LOGGER.info(CLASS_NAME + methodName + "Preparing to validate JWT Token");
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = this.jwtUserDetailsService.loadUserByUsername(username);

            // if token is valid configure Spring Security to manually set
            // authentication
            if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
                LOGGER.info(CLASS_NAME + methodName +
                        "Token is valid configure Spring Security to manually set authentication");
                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                usernamePasswordAuthenticationToken
                        .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // After setting the Authentication in the context, we specify
                // that the current user is authenticated. So it passes the
                // Spring Security Configurations successfully.
                LOGGER.info(CLASS_NAME + methodName + "User is authenticated");
                SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
            }
        }
        chain.doFilter(request, response);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
    }
}
