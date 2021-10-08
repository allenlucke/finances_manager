package com.Allen.SpringFinancesServer.Period;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PeriodLogic {


    @Autowired
    PeriodDao dao;

    //Check to determine if a period overlaps with an existing period
    public boolean checkForExistingPeriod(PeriodModel period, int usersId) {

        List <PeriodModel> overlappingPeriods = dao.getOverlappingPeriods(period, usersId);

        int isOverlapping = overlappingPeriods.size();

        if(isOverlapping > 0) {
            return true;
        }
        return false;
    }
}
