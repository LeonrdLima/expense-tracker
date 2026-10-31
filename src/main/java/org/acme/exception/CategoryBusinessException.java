package org.acme.exception;

import jakarta.ws.rs.core.Response;

public class CategoryBusinessException extends ApiException {

    public CategoryBusinessException(Response.Status status, String message) {
        super(status, message);
    }
}
