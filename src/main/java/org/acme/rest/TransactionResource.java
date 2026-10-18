package org.acme.rest;

import java.util.List;

import org.acme.model.Transaction;
import org.acme.rest.dto.ImportFileDTO;
import org.acme.rest.dto.SummaryResponseDTO;
import org.acme.rest.dto.TransactionRequestDTO;
import org.acme.service.CsvImportService;
import org.acme.service.TransactionService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/transactions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TransactionResource {

    @Inject 
    TransactionService transactionService;

    @Inject 
    CsvImportService csvImportService;
    
    @GET
    public List<Transaction> list(
        @QueryParam("year") Integer year,
        @QueryParam("month") Integer month
    ) {
        return transactionService.list(year, month);
    }

    @POST
    public Response create(@Valid TransactionRequestDTO dto) {
        Transaction transaction = transactionService.create(dto);
        return Response.status(Response.Status.CREATED).entity(transaction).build();
    }

    @GET
    @Path("/summary")
    public SummaryResponseDTO calculateSummary(
        @QueryParam("year") Integer year,
        @QueryParam("month") Integer month
    ) {
        return transactionService.calculateSummary(year, month);
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importCsv(ImportFileDTO form) {
        List<Transaction> transactions = csvImportService.importCsv(form.file);
        return Response.status(Response.Status.CREATED).entity(transactions).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        transactionService.delete(id);
        return Response.noContent().build();
    }

    @PUT 
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, @Valid TransactionRequestDTO dto) {
        Transaction updated = transactionService.update(id, dto);
        return Response.ok(updated).build();
    }

}
