package com.Allen.SpringFinancesServer.Security;

import java.util.ArrayList;

import com.Allen.SpringFinancesServer.User.UserDao;
import com.Allen.SpringFinancesServer.User.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class JwtUserDetailsService implements UserDetailsService {

    private static final String CLASS_NAME = "JwtUserDetailsService --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordEncoder bcryptEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        final String methodName = "loadUserByUsername() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        try{
            UserModel checkUser = userDao.getUserByUsername(username);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName + "User successfully loaded");
            return new User(checkUser.getUserName(), checkUser.getPassword(),
                    new ArrayList<>());
        }
        catch (Exception e){
            LOGGER.warn(CLASS_NAME + methodName + "User not found with username: " + username);
        throw new UsernameNotFoundException("User not found with username: " + username);
        }
    }

    public UserModel addUser(UserModel user) {
        final String methodName = "addUser() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        user.setPassword(bcryptEncoder.encode(user.getPassword()));

        UserModel registeredUser = userDao.addUserReturnUser(user);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName + "User successfully added");

        return registeredUser;
    }
}
