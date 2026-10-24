package org.acme.rest;

import java.util.List;

import org.acme.model.Transaction;
import org.acme.rest.dto.CategorySummaryDTO;
import org.acme.rest.dto.ImportFileDTO;
import org.acme.rest.dto.SummaryResponseDTO;
import org.acme.rest.dto.TransactionFilterDTO;
import org.acme.rest.dto.TransactionPatchDTO;
import org.acme.rest.dto.TransactionRequestDTO;
import org.acme.service.CategorizationService;
import org.acme.service.CsvImportService;
import org.acme.service.OfxImportService;
import org.acme.service.PdfImportService;
import org.acme.service.TransactionService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
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

    @Inject
    PdfImportService pdfImportService;

    @Inject
    OfxImportService ofxImportService;

    @Inject
    CategorizationService categorizationService;
    
    @GET
    public List<Transaction> list(@BeanParam TransactionFilterDTO filter) {
        return transactionService.list(filter);
    }

    @POST
    public Response create(@Valid TransactionRequestDTO dto) {
        Transaction transaction = transactionService.create(dto);
        return Response.status(Response.Status.CREATED).entity(transaction).build();
    }

    @GET
    @Path("/summary")
    public SummaryResponseDTO calculateSummary(@BeanParam TransactionFilterDTO filter) {
        return transactionService.calculateSummary(filter);
    }

    @POST
    @Path("/import/csv")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importCsv(
        ImportFileDTO form,
        @QueryParam("creditCard") boolean creditCard,
        @QueryParam("institution") String institution
    ) {
        List<Transaction> transactions = creditCard
                                       ? csvImportService.importCreditCardCsv(form.file, institution)
                                       : csvImportService.importCsv(form.file, institution);
        List<Transaction> saved = transactionService.persistUnique(transactions);
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    @POST
    @Path("/import/pdf")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importPdf(ImportFileDTO form, @QueryParam("institution") String institution) {
        List<Transaction> transactions = pdfImportService.importPdf(form.file, institution);
        List<Transaction> saved = transactionService.persistUnique(transactions);
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    @POST
    @Path("/import/ofx")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importOfx(ImportFileDTO form, @QueryParam("institution") String institution) {
        List<Transaction> transactions = ofxImportService.importOfx(form.file, institution);
        List<Transaction> saved = transactionService.persistUnique(transactions);
        return Response.status(Response.Status.CREATED).entity(saved).build();

    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        transactionService.delete(id);
        return Response.noContent().build();
    }

    @DELETE
    public Response deleteAll() {
        transactionService.deleteAll();
        return Response.noContent().build();
    }

    @PUT 
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, @Valid TransactionRequestDTO dto) {
        Transaction updated = transactionService.update(id, dto);
        categorizationService.learn(updated.description, updated.category);
        return Response.ok(updated).build();
    }

    @PATCH
    @Path("/{id}")
    public Response partialUpdate(@PathParam("id") Long id, @Valid TransactionPatchDTO dto) {
        Transaction updated = transactionService.partialUpdate(id, dto);
        if(dto.categoryId != null) {
            categorizationService.learn(updated.description, updated.category);
        }
        return Response.ok(updated).build();
    }

    @GET
    @Path("/by-category")
    public List<CategorySummaryDTO> getExpensesByCategory(@BeanParam TransactionFilterDTO filter) {
        return transactionService.getExpensesByCategory(filter);
    }
}
