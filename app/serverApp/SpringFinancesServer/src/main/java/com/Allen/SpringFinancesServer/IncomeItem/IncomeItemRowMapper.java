package com.Allen.SpringFinancesServer.IncomeItem;

import com.Allen.SpringFinancesServer.Utils.TimestampManager;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class IncomeItemRowMapper implements RowMapper<IncomeItemModel> {

    private static final String CLASS_NAME = "IncomeItemRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    private TimestampManager timeMgr = new TimestampManager();

    //Maps result set returned from database to IncomeItemModel
    @Override
    public IncomeItemModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        IncomeItemModel incomeItem = new IncomeItemModel();

        incomeItem.setId(rs.getInt("id"));
        incomeItem.setBudgetIncomeCategoryId(rs.getInt("budget_incomeCategory_id"));
        incomeItem.setName(rs.getString("name"));
        Timestamp outputReceivedDate = rs.getTimestamp("receivedDate");
        incomeItem.setReceivedDate(timeMgr.timestampToStringParser(outputReceivedDate));
        incomeItem.setAmountExpected(rs.getBigDecimal("amountExpected"));
        incomeItem.setAmountReceived(rs.getBigDecimal("amountReceived"));
        incomeItem.setAccountId(rs.getInt("account_id"));
        incomeItem.setUsersId(rs.getInt("users_id"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return incomeItem;
    }
}
