package com.Allen.SpringFinancesServer.BudgetBalanceSheet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BudgetBalanceSheetDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;


}
