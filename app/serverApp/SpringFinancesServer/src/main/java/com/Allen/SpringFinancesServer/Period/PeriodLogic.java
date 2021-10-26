package com.Allen.SpringFinancesServer.Period;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;


@Service
public class PeriodLogic {

    private static final String CLASS_NAME = "PeriodLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    PeriodDao dao;

    public ResponseEntity addPeriodRetId(PeriodModel period, int usersId) {

        final String methodName = "addPeriodRetId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        boolean overlappingPeriodExists = checkForExistingPeriod(period, usersId);

        if(overlappingPeriodExists){
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            LOGGER.warn(CLASS_NAME + methodName + "This request overlaps/conflicts with a previously existing period.");
            return new ResponseEntity("Bad Request", HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        else{
            List<ReturnIdModel> returnedId = dao.addPeriodReturnId(period);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

    //Check to determine if a period overlaps with an existing period
    private boolean checkForExistingPeriod(PeriodModel period, int usersId) {

        final String methodName = "checkForExistingPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        int isOverlapping = 0;

        try {
            List<PeriodModel> overlappingPeriods = dao.getOverlappingPeriods(period, usersId);
            isOverlapping = overlappingPeriods.size();
        }
        catch (EmptyResultDataAccessException e){}

        if(isOverlapping > 0) {
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return true;
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return false;
    }
}
