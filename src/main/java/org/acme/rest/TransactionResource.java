package org.acme.rest;

import java.util.List;

import org.acme.model.Category;
import org.acme.model.Transaction;
import org.acme.rest.dto.TransactionRequestDTO;

import jakarta.transaction.Transactional;
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
    
    @GET
    public List<Transaction> listAll() {
        return Transaction.listAll();
    }

    @POST
    @Transactional
    public Response create(@Valid TransactionRequestDTO dto) {
        Category category = Category.findById(dto.categoryId);
        if(category == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("Categoria não encontrada para o ID informado")
                           .build();
        }
        Transaction transaction = new Transaction();
        transaction.description = dto.description;
        transaction.transactionType = dto.transactionType;
        transaction.amount = dto.amount;
        transaction.date = dto.date;
        transaction.category = category;
        transaction.persist();

        return Response.status(Response.Status.CREATED).entity(transaction).build();
    }
}
