package com.Allen.SpringFinancesServer.AccountBalanceSheet;

import java.util.Comparator;

public class SortAccountBalanceSheet implements Comparator<AccountBalanceSheetModel> {
    @Override
    public int compare(AccountBalanceSheetModel o1, AccountBalanceSheetModel o2) {

        return o1.getTransactionDate().compareTo(o2.getTransactionDate());
    }
}
