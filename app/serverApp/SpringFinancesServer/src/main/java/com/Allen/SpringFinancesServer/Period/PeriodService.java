package com.Allen.SpringFinancesServer.Period;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import com.Allen.SpringFinancesServer.User.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Consumes;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RestController
public class PeriodService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    PeriodDao dao;

    @GetMapping("/getAllPeriods")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<PeriodModel> getAllPeriods(){
        List<PeriodModel> result;
        result = dao.getAllPeriods();

        return result;
    }

    @GetMapping("/getPeriod")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<PeriodModel> getPeriodById(@QueryParam("id") int id){
        List<PeriodModel> result;

            result = dao.getPeriodById(id);

        return result;
    }

    @PostMapping("/addPeriodRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ReturnIdModel> addPeriodRetId(@RequestBody PeriodModel period) {
        List<ReturnIdModel> returnedId = dao.addPeriodReturnId(period);
        return returnedId;
    }
}
