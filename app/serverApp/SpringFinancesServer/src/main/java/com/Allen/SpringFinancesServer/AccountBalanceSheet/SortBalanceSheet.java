package com.Allen.SpringFinancesServer.AccountBalanceSheet;

import java.util.Comparator;

public class SortBalanceSheet implements Comparator<BalanceSheetModel> {
    @Override
    public int compare(BalanceSheetModel o1, BalanceSheetModel o2) {

        return o1.getTransactionDate().compareTo(o2.getTransactionDate());
    }
}
