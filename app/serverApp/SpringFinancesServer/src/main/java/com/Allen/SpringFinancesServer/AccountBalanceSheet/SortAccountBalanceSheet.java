package com.Allen.SpringFinancesServer.AccountBalanceSheet;

import java.util.Comparator;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class SortAccountBalanceSheet implements Comparator<AccountBalanceSheetModel> {

    private static final String CLASS_NAME = "SortAccountBalanceSheet --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Override
    public int compare(AccountBalanceSheetModel o1, AccountBalanceSheetModel o2) {

        final String methodName = "compare() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return o1.getTransactionDate().compareTo(o2.getTransactionDate());
    }
}
