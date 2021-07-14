package com.Allen.SpringFinancesServer.Period;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PeriodRowMapper implements RowMapper<PeriodModel> {

    @Override
    public PeriodModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        PeriodModel period = new PeriodModel();

        period.setId(rs.getInt("id"));
        period.setName(rs.getString("name"));
        period.setStartDate(rs.getTimestamp("startDate"));
        period.setEndDate(rs.getTimestamp("endDate"));
        period.setUsers_id(rs.getInt("users_id"));

        return period;
    }
}
