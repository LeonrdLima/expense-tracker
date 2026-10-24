package org.acme.exception;

import jakarta.ws.rs.core.Response;

public class InvalidFilterException extends ApiException {

    public InvalidFilterException(String message) {
        super(Response.Status.BAD_REQUEST, message);
    }
}
