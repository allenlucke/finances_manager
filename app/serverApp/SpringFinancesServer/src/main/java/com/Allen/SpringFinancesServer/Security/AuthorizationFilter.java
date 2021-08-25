package com.Allen.SpringFinancesServer.Security;

import com.Allen.SpringFinancesServer.User.UserDao;
import com.Allen.SpringFinancesServer.User.UserModel;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletException;
import java.io.IOException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;


@Service
public class AuthorizationFilter {

    private static final String CLASS_NAME = "AuthorizationFilter --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JwtUserDetailsService jwtUserDetailsService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private UserDao userDao;

    public boolean doFilterByUserIdOrSecurityLevel(final String requestTokenHeader, int userId) throws ServletException, IOException {

        final String methodName = "doFilterByUserIdOrSecurityLevel() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String username = null;
        String jwtToken = null;
        // JWT Token is in the form "Bearer token". Remove Bearer word and get
        // only the Token
            jwtToken = requestTokenHeader.substring(7);
            try {
                username = jwtTokenUtil.getUsernameFromToken(jwtToken);
            } catch (IllegalArgumentException e) {
                LOGGER.warn(CLASS_NAME + methodName + "Unable to get JWT Token");
            } catch (ExpiredJwtException e) {
                LOGGER.warn(CLASS_NAME + methodName + "JWT Token has expired");
            }
            UserModel userFromToken = userDao.getUserByUsername(username);
            int userIdFromToken = userFromToken.getId();
            int userSecLvlFromToken = userFromToken.getSecurityLevel();
            String userRoleFromToken = userFromToken.getRole();

            if(userId == userIdFromToken) {
                LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName + "User Id matches token");
                return true;
            }
            else if (userSecLvlFromToken > 49){
                LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName + "User is admin");
                return true;
            }
            else{
                LOGGER.warn(CLASS_NAME + METHOD_EXITING + methodName + "User does not have valid authorization");
            return false;
            }
        }

    public boolean doFilterBySecurityLevel(final String requestTokenHeader) {

        final String methodName = "doFilterBySecurityLevel() ";

        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String username = null;
        String jwtToken = null;
        // JWT Token is in the form "Bearer token". Remove Bearer word and get
        // only the Token
        jwtToken = requestTokenHeader.substring(7);
        try {
            username = jwtTokenUtil.getUsernameFromToken(jwtToken);
        } catch (IllegalArgumentException e) {
            LOGGER.warn(CLASS_NAME + methodName + "Unable to get JWT Token");
        } catch (ExpiredJwtException e) {
            LOGGER.warn(CLASS_NAME + methodName + "JWT Token has expired");
        }
        UserModel userFromToken = userDao.getUserByUsername(username);
        int userIdFromToken = userFromToken.getId();
        int userSecLvlFromToken = userFromToken.getSecurityLevel();
        String userRoleFromToken = userFromToken.getRole();

        if (userSecLvlFromToken > 49){
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName + "User is admin");
            return true;
        }
        else{
            LOGGER.warn(CLASS_NAME + METHOD_EXITING + methodName + "User does not have valid authorization");
            return false;
        }
    }

    public int getUserIdFromToken(final String requestTokenHeader) {

        final String methodName = "getUserIdFromToken() ";

        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String username = null;
        String jwtToken = null;
        // JWT Token is in the form "Bearer token". Remove Bearer word and get
        // only the Token
        jwtToken = requestTokenHeader.substring(7);
        try {
            username = jwtTokenUtil.getUsernameFromToken(jwtToken);
            LOGGER.info(CLASS_NAME + methodName + "User id acquired from JWT token");
        } catch (IllegalArgumentException e) {
            LOGGER.warn(CLASS_NAME + methodName + "Unable to get JWT Token");
        } catch (ExpiredJwtException e) {
            LOGGER.warn(CLASS_NAME + methodName + "JWT Token has expired");
        }
        UserModel userFromToken = userDao.getUserByUsername(username);
        int userIdFromToken = userFromToken.getId();

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return userIdFromToken;
    }
}
