package com.Allen.SpringFinancesServer.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class UserLogic {

    private static final String CLASS_NAME = "UserLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    UserDao dao;

    //***
    //*** Basic DAO Calls - No Logic Required ***//
    //***

    public String getUserFirstName(final int id){

        final String methodName = "getUserFirstName() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String respString;
        respString = dao.getUserFirstName(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return respString;
    }

    //Admin ONLY!
    public List<UserModel> getAllUsers(){

        final String methodName = "getAllUsers() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<UserModel> result;
        result = dao.getAllUsers();

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    public List<UserModel> getUserById(final int id) {

        final String methodName = "getUserById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<UserModel> result;
        result = dao.getUserById(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Used for application Client for local storage user data
    public List<ClientLocalStorageUser> getUserByToken(final int id){

        final String methodName = "getUserByToken() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ClientLocalStorageUser> result;
        result = dao.getUserByToken(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    public int addUserReturnId(final UserModel usr) {

        final String methodName = "addUserReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        int returnedId;
        returnedId = dao.addUserReturnId(usr);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return returnedId;
    }

    //Used by JWTAuthenticationController - No Auth required
    public UserModel getUserByUsername(final String username){

        final String methodName = "getUserByUsername() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        UserModel usr;
        usr = dao.getUserByUsername(username);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return usr;
    }

    //Used by JWTAuthenticationController? - No Auth required
    public UserModel addUserReturnUser(final UserModel user) {

        final String methodName = "addUserReturnUser() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        UserModel usr;
        usr = dao.addUserReturnUser(user);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return usr;
    }
}
