package com.Allen.SpringFinancesServer.CloneOrEditBudget;

import com.Allen.SpringFinancesServer.Budget.BudgetModel;
import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryDao;
import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryLogic;
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
    CloneOrEditBudgetLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @PostMapping("/cloneBudget")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity cloneBudget(@RequestBody BudgetModel newBudget,
            @RequestHeader("Authorization") String jwtString, @QueryParam("templateBudgetId") int templateBudgetId) throws ServletException, IOException {

        final String methodName = "cloneBudget() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ///Check user auth: Only admin or assigned user may post
        int requestUserId = newBudget.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else{
            List<ReturnIdModel> newBudgetReturnedIdList = mgr.cloneBudget(templateBudgetId, requestUserId, newBudget);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(newBudgetReturnedIdList, HttpStatus.OK);
        }
    }

}
