package com.Allen.SpringFinancesServer.CloneOrEditBudget;

import com.Allen.SpringFinancesServer.Budget.BudgetModel;
import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryDao;
import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryModel;
import com.Allen.SpringFinancesServer.ExpenseCategory.ExpenseCategoryModel;
import com.Allen.SpringFinancesServer.ReturnIdModel;
import com.Allen.SpringFinancesServer.Security.AuthorizationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletException;
import javax.ws.rs.Consumes;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@RestController
public class CloneOrEditBudgetController {

    private static final String CLASS_NAME = "CloneOrEditBudgetController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

//    @Autowired
//    CloneOrEditBudgetDao dao;

    @Autowired
    BudgetExpenseCategoryDao budgetExpenseCategoryDao;

    @Autowired
    CloneOrEditBudgetLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("test/getBudgetExpCatByExpCat")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetExpenseCategoryModel> getBudgetById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int budgetId){

        final String methodName = "getBudgetById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetExpenseCategoryModel> result;
        result = budgetExpenseCategoryDao.getBudgetExpCatByExpCat(budgetId, userId);
        
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    @PostMapping("test/CloneBudget")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity cloneBudget(@RequestBody BudgetModel newBudget,
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int templateBudgetId) throws ServletException, IOException {

        final String methodName = "cloneBudget() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ///Check user auth: Only admin or assigned user may post
        int requestUserId = newBudget.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
//        else {
//            List<ReturnIdModel> returnedId = dao.addBudgetReturnId(budget);
//            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
//            return new ResponseEntity(returnedId, HttpStatus.OK);
//        }
        else{
            List<BudgetExpenseCategoryModel> matchingExpCats = mgr.cloneBudget(templateBudgetId, requestUserId, newBudget);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(matchingExpCats, HttpStatus.OK);
        }
    }
}
