package com.Allen.SpringFinancesServer.Utils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TimestampManager {

    public String timestampToStringParser(Timestamp timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        if(timestamp != null){

        String timestampAsString = formatter.format(timestamp.toLocalDateTime());
        return timestampAsString;
        }
        else{
            String timestampAsString = null;
            return timestampAsString;
        }
    }

    public Timestamp stringToTimestampParser(String timestampString){

        if(timestampString.length() > 10) {
            timestampString = timestampString.substring(0, 10);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);

        LocalDateTime localDateTime = LocalDate.parse(timestampString, dtf).atStartOfDay();

        Timestamp timestamp = Timestamp.valueOf(localDateTime);
        return timestamp;
    }

    //Only used for posting periods
    //Ensures posted date will fall at EOD
    public Timestamp periodPostingStringToTimestampParser(String dateString) {

        if(dateString.length() > 10) {
            dateString = dateString.substring(0, 10);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);

        LocalDateTime localDateTime = LocalDate.parse(dateString, dtf).atTime(23, 59, 59, 9);

        Timestamp timestamp = Timestamp.valueOf(localDateTime);
        return timestamp;
    }
}
