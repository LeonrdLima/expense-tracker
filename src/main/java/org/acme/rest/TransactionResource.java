package org.acme.rest;

import java.util.List;

import org.acme.model.Transaction;
import org.acme.rest.dto.CategorySummaryDTO;
import org.acme.rest.dto.ImportFileDTO;
import org.acme.rest.dto.InstallmentProjectionMonthDTO;
import org.acme.rest.dto.MonthlyEvolutionDTO;
import org.acme.rest.dto.PagedResponseDTO;
import org.acme.rest.dto.SummaryResponseDTO;
import org.acme.rest.dto.TransactionFilterDTO;
import org.acme.rest.dto.TransactionPatchDTO;
import org.acme.rest.dto.TransactionRequestDTO;
import org.acme.service.CategorizationService;
import org.acme.service.CsvImportService;
import org.acme.service.OfxImportService;
import org.acme.service.PdfImportService;
import org.acme.service.TransactionService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
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
@Tag(name = "Transações", description = "Gerenciamento de transações financeiras, importação de extratos/faturas e relatórios")
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
    @Operation(
        summary = "Listar transações",
        description = "Lista transações com suporte a filtros combinados (período, categoria, tipo, descrição, cartão de crédito, instituição), ordenação (sortBy/sortDir) e paginação opcional (page/size)."
    )
    public List<Transaction> list(@BeanParam TransactionFilterDTO filter) {
        return transactionService.list(filter);
    }

    @GET
    @Path("/paged")
    @Operation(
        summary = "Listar transações com metadados de paginação",
        description = "Retorna as transações filtradas e ordenadas envelopadas com metadados de paginação (page, size, totalElements, totalPages)."
    )
    public PagedResponseDTO<Transaction> listPaged(@BeanParam TransactionFilterDTO filter) {
        return transactionService.listPaged(filter);
    }

    @POST
    @Operation(summary = "Criar transação", description = "Registra manualmente uma nova transação financeira.")
    public Response create(@Valid TransactionRequestDTO dto) {
        Transaction transaction = transactionService.create(dto);
        return Response.status(Response.Status.CREATED).entity(transaction).build();
    }

    @GET
    @Path("/summary")
    @Operation(
        summary = "Calcular balanço financeiro",
        description = "Calcula o total de receitas (totalIncome), despesas (totalExpense) e saldo líquido (balance) aplicando os filtros informados."
    )
    public SummaryResponseDTO calculateSummary(@BeanParam TransactionFilterDTO filter) {
        return transactionService.calculateSummary(filter);
    }

    @POST
    @Path("/import/csv")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(
        summary = "Importar extrato ou fatura CSV",
        description = "Importa transações a partir de arquivo CSV, detectando automaticamente colunas, instituição financeira, cartão de crédito e evitando duplicatas."
    )
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
    @Operation(
        summary = "Importar extrato ou fatura PDF",
        description = "Extrai transações de faturas ou extratos bancários em formato PDF, identificando parcelas, estornos e instituição financeira."
    )
    public Response importPdf(ImportFileDTO form, @QueryParam("institution") String institution) {
        List<Transaction> transactions = pdfImportService.importPdf(form.file, institution);
        List<Transaction> saved = transactionService.persistUnique(transactions);
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    @POST
    @Path("/import/ofx")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(
        summary = "Importar extrato ou fatura OFX",
        description = "Importa transações de arquivos OFX (Open Financial Exchange), identificando parcelas, cartão de crédito e instituição financeira."
    )
    public Response importOfx(ImportFileDTO form, @QueryParam("institution") String institution) {
        List<Transaction> transactions = ofxImportService.importOfx(form.file, institution);
        List<Transaction> saved = transactionService.persistUnique(transactions);
        return Response.status(Response.Status.CREATED).entity(saved).build();

    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Excluir transação por ID", description = "Remove uma transação específica pelo seu identificador.")
    public Response delete(@PathParam("id") Long id) {
        transactionService.delete(id);
        return Response.noContent().build();
    }

    @DELETE
    @Operation(summary = "Excluir todas as transações", description = "Remove todas as transações cadastradas.")
    public Response deleteAll() {
        transactionService.deleteAll();
        return Response.noContent().build();
    }

    @PUT 
    @Path("/{id}")
    @Operation(
        summary = "Atualizar transação completa",
        description = "Atualiza todos os dados de uma transação e ensina o padrão de categorização para transações similares."
    )
    public Response update(@PathParam("id") Long id, @Valid TransactionRequestDTO dto) {
        Transaction updated = transactionService.update(id, dto);
        categorizationService.learn(updated.description, updated.category);
        return Response.ok(updated).build();
    }

    @PATCH
    @Path("/{id}")
    @Operation(
        summary = "Atualizar transação parcialmente",
        description = "Atualiza apenas os campos informados (categoryId, beneficiary, transactionType, institution) e aprende o padrão caso a categoria seja alterada."
    )
    public Response partialUpdate(@PathParam("id") Long id, @Valid TransactionPatchDTO dto) {
        Transaction updated = transactionService.partialUpdate(id, dto);
        if(dto.categoryId != null) {
            categorizationService.learn(updated.description, updated.category);
        }
        return Response.ok(updated).build();
    }

    @GET
    @Path("/by-category")
    @Operation(
        summary = "Gastos agrupados por categoria",
        description = "Retorna o total líquido gasto e o percentual de cada categoria no período filtrado, abatendo estornos e reembolsos."
    )
    public List<CategorySummaryDTO> getExpensesByCategory(@BeanParam TransactionFilterDTO filter) {
        return transactionService.getExpensesByCategory(filter);
    }

    @GET
    @Path("/monthly-evolution")
    @Operation(
        summary = "Evolução financeira mês a mês",
        description = "Agrupa receitas, despesas, saldo líquido e quantidade de transações mês a mês em ordem cronológica."
    )
    public List<MonthlyEvolutionDTO> getMonthlyEvolution(@BeanParam TransactionFilterDTO filter) {
        return transactionService.getMonthlyEvolution(filter);
    }

    @GET
    @Path("/installments/projection")
    @Operation(
        summary = "Projeção de parcelas futuras",
        description = "Projeta o comprometimento mensal com compras parceladas em andamento para os próximos N meses."
    )
    public List<InstallmentProjectionMonthDTO> getInstallmentProjection(
        @QueryParam("months") @DefaultValue("6") int months,
        @QueryParam("institution") String institution
    ) {
        return transactionService.getInstallmentProjection(months, institution);
    }
}
