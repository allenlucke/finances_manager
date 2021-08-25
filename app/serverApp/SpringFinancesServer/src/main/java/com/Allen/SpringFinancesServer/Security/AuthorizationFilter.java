package com.Allen.SpringFinancesServer.Security;

import com.Allen.SpringFinancesServer.User.UserDao;
import com.Allen.SpringFinancesServer.User.UserModel;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletException;
import java.io.IOException;


@Service
public class AuthorizationFilter {

    @Autowired
    private JwtUserDetailsService jwtUserDetailsService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private UserDao userDao;

    public boolean doFilterByUserIdOrSecurityLevel(final String requestTokenHeader, int userId) throws ServletException, IOException {

        String username = null;
        String jwtToken = null;
        // JWT Token is in the form "Bearer token". Remove Bearer word and get
        // only the Token
            jwtToken = requestTokenHeader.substring(7);
            try {
                username = jwtTokenUtil.getUsernameFromToken(jwtToken);
            } catch (IllegalArgumentException e) {
                System.out.println("Unable to get JWT Token");
            } catch (ExpiredJwtException e) {
                System.out.println("JWT Token has expired");
            }
            UserModel userFromToken = userDao.getUserByUsername(username);
            int userIdFromToken = userFromToken.getId();
            int userSecLvlFromToken = userFromToken.getSecurityLevel();
            String userRoleFromToken = userFromToken.getRole();

            if(userId == userIdFromToken) {
                return true;
            }
            else if (userSecLvlFromToken > 49){
                return true;
            }
            else{
            return false;
            }
        }

    public boolean doFilterBySecurityLevel(final String requestTokenHeader) {
        String username = null;
        String jwtToken = null;
        // JWT Token is in the form "Bearer token". Remove Bearer word and get
        // only the Token
        jwtToken = requestTokenHeader.substring(7);
        try {
            username = jwtTokenUtil.getUsernameFromToken(jwtToken);
        } catch (IllegalArgumentException e) {
            System.out.println("Unable to get JWT Token");
        } catch (ExpiredJwtException e) {
            System.out.println("JWT Token has expired");
        }
        UserModel userFromToken = userDao.getUserByUsername(username);
        int userIdFromToken = userFromToken.getId();
        int userSecLvlFromToken = userFromToken.getSecurityLevel();
        String userRoleFromToken = userFromToken.getRole();

        if (userSecLvlFromToken > 49){
            return true;
        }
        else{
            return false;
        }
    }

    public int getUserIdFromToken(final String requestTokenHeader) {
        String username = null;
        String jwtToken = null;
        // JWT Token is in the form "Bearer token". Remove Bearer word and get
        // only the Token
        jwtToken = requestTokenHeader.substring(7);
        try {
            username = jwtTokenUtil.getUsernameFromToken(jwtToken);
        } catch (IllegalArgumentException e) {
            System.out.println("Unable to get JWT Token");
        } catch (ExpiredJwtException e) {
            System.out.println("JWT Token has expired");
        }
        UserModel userFromToken = userDao.getUserByUsername(username);
        int userIdFromToken = userFromToken.getId();

        return userIdFromToken;
    }
}
