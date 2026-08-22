package com.Allen.SpringFinancesServer.Model;

import org.springframework.http.HttpStatus;
import java.sql.Timestamp;

public class ApiError {

    private Timestamp timestamp;
    private int status;
    private HttpStatus error;
    private String message;
    private String path;


    public ApiError( int status, HttpStatus error, String message, String path) {
        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public HttpStatus getError() {
        return error;
    }

    public void setError(HttpStatus error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
