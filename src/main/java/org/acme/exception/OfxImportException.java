package org.acme.exception;

import jakarta.ws.rs.core.Response;

public class OfxImportException extends ApiException {

    public OfxImportException(String message) {
        super(Response.Status.BAD_REQUEST, message);
    }
    
    
}
