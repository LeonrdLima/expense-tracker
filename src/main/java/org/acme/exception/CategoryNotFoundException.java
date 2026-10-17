package org.acme.exception;

import jakarta.ws.rs.core.Response;

public class CategoryNotFoundException extends ApiException{

    public CategoryNotFoundException(String message) {
        super(Response.Status.BAD_REQUEST, message);
    }
    
}
