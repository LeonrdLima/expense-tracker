package org.acme.rest;

import java.util.List;

import org.acme.model.Transaction;
import org.acme.rest.dto.TransactionRequestDTO;
import org.acme.service.TransactionService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/transactions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TransactionResource {

    @Inject 
    TransactionService transactionService;
    
    @GET
    public List<Transaction> listAll() {
        return transactionService.listAll();
    }

    @POST
    public Response create(@Valid TransactionRequestDTO dto) {
        Transaction transaction = transactionService.create(dto);
        return Response.status(Response.Status.CREATED).entity(transaction).build();
    }
}
