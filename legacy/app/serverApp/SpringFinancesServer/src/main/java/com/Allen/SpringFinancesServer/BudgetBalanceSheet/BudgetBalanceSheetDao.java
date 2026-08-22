package com.Allen.SpringFinancesServer.BudgetBalanceSheet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class BudgetBalanceSheetDao {

    private static final String CLASS_NAME = "BudgetBalanceSheetDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;


    //Get budget expense categories by period
    //User may only access budget expense categories assigned to the user
    protected List<BudgetBalanceSheetModel> getBudgetExpCatsByPeriod(int periodId, final int usersId){

        final String methodName = "getBudgetExpCatsByPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT \"expenseCategory\".id AS \"expenseCategoryId\",\n" +
                "\"expenseCategory\".name AS \"expenseCategoryName\", \"expenseCategory\".\"users_id\" AS \"usersId\",\n" +
                "\"budget_expenseCategory\".id AS \"budgetExpenseCategoryId\", \"budget\".id AS \"budgetId\",\n" +
                "\"budget\".\"period_id\" AS \"periodId\", \n" +
                "\"budget_expenseCategory\".\"amountBudgeted\" AS \"amountBudgeted\",\n" +
                "\"budget\".\"isClosed\" AS \"isClosed\" FROM \"expenseCategory\"\n" +
                "JOIN \"budget_expenseCategory\" ON \"expenseCategory\".id = \"budget_expenseCategory\".\"expenseCategory_id\"\n" +
                "JOIN \"budget\" ON \"budget_expenseCategory\".\"budget_id\" = \"budget\".id\n" +
                "WHERE \"budget\".\"period_id\" = ?\n" +
                "AND \"budget\".\"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {periodId, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgetBalanceSheetModel> result = new ArrayList<BudgetBalanceSheetModel>();
        for(Map<String, Object> row:rows){
            BudgetBalanceSheetModel budgExpCats = new BudgetBalanceSheetModel();
            budgExpCats.setExpenseCategoryId((int)row.get("expenseCategoryId"));
            budgExpCats.setExpenseCategoryName((String)row.get("expenseCategoryName"));
            budgExpCats.setUsersId((int)row.get("usersId"));
            budgExpCats.setBudgetExpenseCategoryId((int)row.get("budgetExpenseCategoryId"));
            budgExpCats.setBudgetId((int)row.get("budgetId"));
            budgExpCats.setPeriodId((int)row.get("periodId"));
            budgExpCats.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));
            budgExpCats.setClosed((boolean)row.get("isClosed"));

            result.add(budgExpCats);

        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }


}
