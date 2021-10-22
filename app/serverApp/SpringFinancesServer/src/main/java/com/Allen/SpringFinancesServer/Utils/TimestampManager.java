package com.Allen.SpringFinancesServer.Utils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TimestampManager {

    public String timestampToStringParser(Timestamp timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        String timestampAsString = formatter.format(timestamp.toLocalDateTime());
        return timestampAsString;
    }

    public Timestamp stringToTimestampParser(String timeStampString){

        if(timeStampString.length() > 10) {
            timeStampString = timeStampString.substring(0, 10);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);

        LocalDateTime localDateTime = LocalDate.parse(timeStampString, dtf).atStartOfDay();

        Timestamp timestamp = Timestamp.valueOf(localDateTime);
        return timestamp;
    }
}
