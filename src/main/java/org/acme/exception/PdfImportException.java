package org.acme.exception;

import jakarta.ws.rs.core.Response;

public class PdfImportException extends ApiException {

    public PdfImportException(String message) {
        super(Response.Status.BAD_REQUEST, message);
    }
    
    
}
