package org.acme.exception;

import jakarta.ws.rs.core.Response;

public class TransactionNotFoundException extends ApiException {
    
    public TransactionNotFoundException(String message) {
        super(Response.Status.NOT_FOUND, message);
    }

}
