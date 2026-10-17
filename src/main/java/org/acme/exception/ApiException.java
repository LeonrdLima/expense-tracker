package org.acme.exception;

import jakarta.ws.rs.core.Response;

public abstract class ApiException extends RuntimeException {
    private final Response.Status status;

    protected ApiException(Response.Status status, String message) {
        super(message);
        this.status = status;
    }

    public Response.Status getStatus() {
        return status;
    }
}
