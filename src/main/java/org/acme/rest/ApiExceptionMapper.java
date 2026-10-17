package org.acme.rest;

import org.acme.exception.ApiException;
import org.acme.rest.dto.ErrorResponse;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider 
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Override 
    public Response toResponse(ApiException exception) {
        return Response.status(exception.getStatus())
                       .type(MediaType.APPLICATION_JSON)
                       .entity(new ErrorResponse(exception.getStatus().getStatusCode(), 
                                                 exception.getMessage()))
                       .build();
    }
    
}