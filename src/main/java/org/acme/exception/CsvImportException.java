package org.acme.exception;

import jakarta.ws.rs.core.Response;

public class CsvImportException extends ApiException {

    public CsvImportException(String message) {
        super(Response.Status.BAD_REQUEST, message);
    }
    
    
}
