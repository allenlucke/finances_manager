package com.Allen.SpringFinancesServer.IncomeCategory;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class IncomeCategoryLogic {

    private static final String CLASS_NAME = "IncomeCategoryLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    IncomeCategoryDao dao;

    //***
    //*** Basic DAO Calls - No Logic Required ***//
    //***

    //User may only access income categories assigned to the user
    public List<IncomeCategoryModel> getAllIncomeCats(final int usersId) {

        final String methodName = "getAllIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<IncomeCategoryModel> result;
        result = dao.getAllIncomeCats(usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all income categories
    public List<IncomeCategoryModel> adminGetAllIncomeCats() {

        final String methodName = "adminGetAllIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<IncomeCategoryModel> result;
        result = dao.adminGetAllIncomeCats();

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any income category
    public List<IncomeCategoryModel> adminGetIncomeCatById(final int id){

        final String methodName = "adminGetIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<IncomeCategoryModel> result;
        result = dao.adminGetIncomeCatById(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access income categories assigned to the user
    public List<IncomeCategoryModel> getIncomeCatById(final int id, final int usersId){

        final String methodName = "getIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<IncomeCategoryModel> result;
        result = dao.getIncomeCatById(id, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the income category will be assigned
    //may use this post call
    public List<ReturnIdModel> addIncomeCatReturnId(final IncomeCategoryModel incomeCat) {

        final String methodName = "addIncomeCatReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ReturnIdModel> result;
        result = dao.addIncomeCatReturnId(incomeCat);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only delete income categories assigned to the user
    public boolean deleteIncomeCatById(final int catId, final int usersId) {
        final String methodName = "deleteIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        boolean wasDeleted;
        wasDeleted = dao.deleteIncomeCatById(catId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return wasDeleted;
    }

}
