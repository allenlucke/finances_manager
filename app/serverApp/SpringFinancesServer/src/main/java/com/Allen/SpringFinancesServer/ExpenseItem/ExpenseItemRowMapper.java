package com.Allen.SpringFinancesServer.ExpenseItem;

import com.Allen.SpringFinancesServer.Utils.TimestampManager;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class ExpenseItemRowMapper implements RowMapper<ExpenseItemModel>{

    private static final String CLASS_NAME = "ExpenseItemRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    private TimestampManager timeMgr = new TimestampManager();

    @Override
    public ExpenseItemModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ExpenseItemModel expItem = new ExpenseItemModel();

        expItem.setId(rs.getInt("id"));
        expItem.setBudgetExpenseCategoryId(rs.getInt("budget_expenseCategory_id"));
        expItem.setName(rs.getString("name"));
        Timestamp outputTransactionDate = rs.getTimestamp("transactionDate");
        expItem.setTransactionDate(timeMgr.timestampToStringParser(outputTransactionDate));
        expItem.setAmount(rs.getBigDecimal("amount"));
        expItem.setPaidWithCredit(rs.getBoolean("paidWithCredit"));
        expItem.setPaymentToCreditAccount(rs.getBoolean("paymentToCreditAccount"));
        expItem.setInterestPaymentToCreditAccount(rs.getBoolean("interestPaymentToCreditAccount"));
        expItem.setAccountId(rs.getInt("account_id"));
        expItem.setUsersId(rs.getInt("users_id"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return expItem;
    }
}
