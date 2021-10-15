package com.Allen.SpringFinancesServer.Period;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PeriodLogic {


    @Autowired
    PeriodDao dao;

    //Check to determine if a period overlaps with an existing period
    public boolean checkForExistingPeriod(PeriodModel period, int usersId) {

        int isOverlapping = 0;

        try {
            List<PeriodModel> overlappingPeriods = dao.getOverlappingPeriods(period, usersId);
            isOverlapping = overlappingPeriods.size();
        }
        catch (EmptyResultDataAccessException e){}
//        isOverlapping = overlappingPeriods.size();

        if(isOverlapping > 0) {
            return true;
        }
        return false;
    }
}
