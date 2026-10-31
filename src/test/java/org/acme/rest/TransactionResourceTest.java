package org.acme.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.model.CategoryPattern;
import org.acme.model.Transaction;
import org.acme.model.TransactionType;
import org.acme.rest.dto.TransactionRequestDTO;
import org.acme.service.CategorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class TransactionResourceTest {

    @Inject
    CategorizationService categorizationService;

    private Long alimentacaoId;
    private Long lazerId;
    private Long outrosId;

    @BeforeEach
    @Transactional
    void setUp() {
        Transaction.deleteAll();
        CategoryPattern.deleteAll();

        alimentacaoId = categorizationService.findOrCreate("Alimentação").id;
        lazerId = categorizationService.findOrCreate("Lazer").id;
        outrosId = categorizationService.findOrCreate("Outros").id;
    }

    @Test
    @DisplayName("Deve criar uma transação válida e retornar 201")
    void shouldCreateTransaction() {
        TransactionRequestDTO dto = new TransactionRequestDTO();
        dto.description = "Almoço Restaurante";
        dto.transactionType = TransactionType.EXPENSE;
        dto.categoryId = alimentacaoId;
        dto.amount = new BigDecimal("45.90");
        dto.date = LocalDate.of(2026, 10, 15);
        dto.beneficiary = "Restaurante Central";
        dto.currentInstallment = 1;
        dto.totalInstallments = 2;

        given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/transactions")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("description", equalTo("Almoço Restaurante"))
            .body("amount", equalTo(45.90f))
            .body("category.name", equalTo("Alimentação"))
            .body("currentInstallment", equalTo(1))
            .body("totalInstallments", equalTo(2));
    }

    @Test
    @DisplayName("Deve retornar 400 ao tentar criar transação com categoria inexistente")
    void shouldReturn400WhenCategoryNotFound() {
        TransactionRequestDTO dto = new TransactionRequestDTO();
        dto.description = "Compra Teste";
        dto.transactionType = TransactionType.EXPENSE;
        dto.categoryId = 999999L;
        dto.amount = new BigDecimal("50.00");
        dto.date = LocalDate.of(2026, 10, 15);

        given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/transactions")
        .then()
            .statusCode(400)
            .body("status", equalTo(400))
            .body("message", containsString("Categoria não encontrada"));
    }

    @Test
    @DisplayName("Deve atualizar transação e recategorizar outras transações em 'Outros' com o mesmo padrão")
    void shouldUpdateTransactionAndLearnCategoryPattern() {
        // Cria duas transações em "Outros" com a mesma descrição base
        TransactionRequestDTO t1 = new TransactionRequestDTO();
        t1.description = "iFood Pedido 1";
        t1.transactionType = TransactionType.EXPENSE;
        t1.categoryId = outrosId;
        t1.amount = new BigDecimal("35.00");
        t1.date = LocalDate.of(2026, 10, 10);

        Integer id1 = given().contentType(ContentType.JSON).body(t1)
            .when().post("/transactions")
            .then().statusCode(201).extract().path("id");

        TransactionRequestDTO t2 = new TransactionRequestDTO();
        t2.description = "iFood Pedido 1 - Parcela 2/2";
        t2.transactionType = TransactionType.EXPENSE;
        t2.categoryId = outrosId;
        t2.amount = new BigDecimal("35.00");
        t2.date = LocalDate.of(2026, 10, 12);

        given().contentType(ContentType.JSON).body(t2)
            .when().post("/transactions")
            .then().statusCode(201);

        // Atualiza a primeira para "Alimentação" via PUT
        t1.categoryId = alimentacaoId;
        given()
            .contentType(ContentType.JSON)
            .body(t1)
        .when()
            .put("/transactions/" + id1)
        .then()
            .statusCode(200)
            .body("category.name", equalTo("Alimentação"));

        // Verifica se a segunda transação também foi recategorizada automaticamente para "Alimentação"
        given()
            .queryParam("year", 2026)
            .queryParam("month", 10)
        .when()
            .get("/transactions")
        .then()
            .statusCode(200)
            .body("size()", is(2))
            .body("category.name", everyItem(equalTo("Alimentação")));
    }

    @Test
    @DisplayName("Deve deletar transação existente e retornar 404 ao deletar ID inexistente")
    void shouldDeleteTransactionAndReturn404WhenNotFound() {
        TransactionRequestDTO dto = new TransactionRequestDTO();
        dto.description = "Para Deletar";
        dto.transactionType = TransactionType.EXPENSE;
        dto.categoryId = outrosId;
        dto.amount = new BigDecimal("10.00");
        dto.date = LocalDate.of(2026, 10, 1);

        Integer id = given().contentType(ContentType.JSON).body(dto)
            .when().post("/transactions")
            .then().statusCode(201).extract().path("id");

        given()
            .when().delete("/transactions/" + id)
            .then().statusCode(204);

        given()
            .when().delete("/transactions/" + id)
            .then().statusCode(404);
    }

    @Test
    @DisplayName("Deve calcular corretamente o balanço financeiro e gastos por categoria no período")
    void shouldCalculateSummaryAndExpensesByCategory() {
        // Receita em Outubro/2026: R$ 1000.00
        createSampleTransaction("Salário", TransactionType.INCOME, outrosId, "1000.00", LocalDate.of(2026, 10, 5));
        // Despesa Alimentação em Outubro/2026: R$ 150.00 (75% das despesas)
        createSampleTransaction("Mercado", TransactionType.EXPENSE, alimentacaoId, "150.00", LocalDate.of(2026, 10, 10));
        // Despesa Lazer em Outubro/2026: R$ 50.00 (25% das despesas)
        createSampleTransaction("Cinema", TransactionType.EXPENSE, lazerId, "50.00", LocalDate.of(2026, 10, 15));
        // Despesa em Setembro/2026 (não deve entrar no filtro de Outubro)
        createSampleTransaction("Mês Anterior", TransactionType.EXPENSE, alimentacaoId, "500.00", LocalDate.of(2026, 9, 20));

        // 1. Valida /transactions/summary de Outubro/2026
        given()
            .queryParam("year", 2026)
            .queryParam("month", 10)
        .when()
            .get("/transactions/summary")
        .then()
            .statusCode(200)
            .body("totalIncome", equalTo(1000.00f))
            .body("totalExpense", equalTo(200.00f))
            .body("balance", equalTo(800.00f));

        // 2. Valida /transactions/by-category de Outubro/2026
        given()
            .queryParam("year", 2026)
            .queryParam("month", 10)
        .when()
            .get("/transactions/by-category")
        .then()
            .statusCode(200)
            .body("size()", is(2))
            .body("find { it.categoryName == 'Alimentação' }.totalAmount", equalTo(150.00f))
            .body("find { it.categoryName == 'Alimentação' }.percentage", equalTo(75.0f))
            .body("find { it.categoryName == 'Lazer' }.totalAmount", equalTo(50.00f))
            .body("find { it.categoryName == 'Lazer' }.percentage", equalTo(25.0f));
    }

    @Test
    @DisplayName("Deve atualizar parcialmente via PATCH alterando somente a categoria")
    void shouldPatchOnlyCategory() {
        TransactionRequestDTO dto = new TransactionRequestDTO();
        dto.description = "Uber Viagem";
        dto.transactionType = TransactionType.EXPENSE;
        dto.categoryId = outrosId;
        dto.amount = new BigDecimal("25.50");
        dto.date = LocalDate.of(2026, 10, 20);
        dto.beneficiary = "Uber BV";

        Integer id = given().contentType(ContentType.JSON).body(dto)
            .when().post("/transactions")
            .then().statusCode(201).extract().path("id");

        // Envia apenas categoryId no PATCH
        given()
            .contentType(ContentType.JSON)
            .body("{\"categoryId\": " + lazerId + "}")
        .when()
            .patch("/transactions/" + id)
        .then()
            .statusCode(200)
            .body("category.name", equalTo("Lazer"))
            .body("description", equalTo("Uber Viagem"))
            .body("amount", equalTo(25.50f))
            .body("beneficiary", equalTo("Uber BV"))
            .body("transactionType", equalTo("EXPENSE"));
    }

    @Test
    @DisplayName("Deve atualizar parcialmente via PATCH alterando somente o beneficiario")
    void shouldPatchOnlyBeneficiary() {
        TransactionRequestDTO dto = new TransactionRequestDTO();
        dto.description = "Café Padaria";
        dto.transactionType = TransactionType.EXPENSE;
        dto.categoryId = alimentacaoId;
        dto.amount = new BigDecimal("12.00");
        dto.date = LocalDate.of(2026, 10, 21);

        Integer id = given().contentType(ContentType.JSON).body(dto)
            .when().post("/transactions")
            .then().statusCode(201).extract().path("id");

        // Envia apenas beneficiary no PATCH
        given()
            .contentType(ContentType.JSON)
            .body("{\"beneficiary\": \"Padaria do Bairro\"}")
        .when()
            .patch("/transactions/" + id)
        .then()
            .statusCode(200)
            .body("beneficiary", equalTo("Padaria do Bairro"))
            .body("category.name", equalTo("Alimentação"))
            .body("amount", equalTo(12.00f));
    }

    @Test
    @DisplayName("Deve atualizar institution e transactionType via PATCH, aprender padrão de categoria e validar 404/400")
    void shouldPatchInstitutionTypeAndLearnPatternAndValidateErrors() {
        TransactionRequestDTO t1 = new TransactionRequestDTO();
        t1.description = "Spotify Assinatura";
        t1.transactionType = TransactionType.EXPENSE;
        t1.categoryId = outrosId;
        t1.amount = new BigDecimal("21.90");
        t1.date = LocalDate.of(2026, 10, 22);

        Integer id1 = given().contentType(ContentType.JSON).body(t1)
            .when().post("/transactions")
            .then().statusCode(201).extract().path("id");

        TransactionRequestDTO t2 = new TransactionRequestDTO();
        t2.description = "Spotify Assinatura - Parcela 2/2";
        t2.transactionType = TransactionType.EXPENSE;
        t2.categoryId = outrosId;
        t2.amount = new BigDecimal("21.90");
        t2.date = LocalDate.of(2026, 10, 23);

        given().contentType(ContentType.JSON).body(t2)
            .when().post("/transactions")
            .then().statusCode(201);

        // PATCH atualizando categoryId e institution
        given()
            .contentType(ContentType.JSON)
            .body("{\"categoryId\": " + lazerId + ", \"institution\": \"Nubank\"}")
        .when()
            .patch("/transactions/" + id1)
        .then()
            .statusCode(200)
            .body("category.name", equalTo("Lazer"))
            .body("institution", equalTo("Nubank"));

        // Verifica aprendizado automático na segunda transação em "Outros"
        given()
            .queryParam("description", "Spotify")
        .when()
            .get("/transactions")
        .then()
            .statusCode(200)
            .body("size()", is(2))
            .body("category.name", everyItem(equalTo("Lazer")));

        // Valida 404 para transação inexistente
        given()
            .contentType(ContentType.JSON)
            .body("{\"beneficiary\": \"Teste\"}")
        .when()
            .patch("/transactions/999999")
        .then()
            .statusCode(404);

        // Valida 400 para categoria inexistente no PATCH
        given()
            .contentType(ContentType.JSON)
            .body("{\"categoryId\": 999999}")
        .when()
            .patch("/transactions/" + id1)
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("Deve filtrar transações, resumo e gastos por categoria usando institution e isCreditCard")
    void shouldFilterByInstitutionAndIsCreditCard() {
        createSampleTransaction("Mercado Nubank", TransactionType.EXPENSE, alimentacaoId, "120.00", LocalDate.of(2026, 10, 10), true, "Nubank");
        createSampleTransaction("Cinema Itaú", TransactionType.EXPENSE, lazerId, "60.00", LocalDate.of(2026, 10, 11), true, "Itaú");
        createSampleTransaction("Pix Débito Nubank", TransactionType.EXPENSE, alimentacaoId, "80.00", LocalDate.of(2026, 10, 12), false, "Nubank");

        // Filtra apenas Nubank (case-insensitive)
        given()
            .queryParam("institution", "nubank")
        .when()
            .get("/transactions")
        .then()
            .statusCode(200)
            .body("size()", is(2))
            .body("institution", everyItem(equalTo("Nubank")));

        // Filtra Nubank + apenas cartão de crédito no summary
        given()
            .queryParam("institution", "Nubank")
            .queryParam("isCreditCard", true)
        .when()
            .get("/transactions/summary")
        .then()
            .statusCode(200)
            .body("totalExpense", equalTo(120.00f))
            .body("balance", equalTo(-120.00f));
    }

    @Test
    @DisplayName("Deve paginar e ordenar transações via GET /transactions e GET /transactions/paged")
    void shouldPaginateAndSortTransactions() {
        createSampleTransaction("Item B", TransactionType.EXPENSE, alimentacaoId, "50.00", LocalDate.of(2026, 10, 1));
        createSampleTransaction("Item A", TransactionType.EXPENSE, alimentacaoId, "10.00", LocalDate.of(2026, 10, 2));
        createSampleTransaction("Item C", TransactionType.EXPENSE, alimentacaoId, "100.00", LocalDate.of(2026, 10, 3));

        // Ordena por valor ascendente com paginação no /transactions/paged
        given()
            .queryParam("page", 0)
            .queryParam("size", 2)
            .queryParam("sortBy", "amount")
            .queryParam("sortDir", "asc")
        .when()
            .get("/transactions/paged")
        .then()
            .statusCode(200)
            .body("page", equalTo(0))
            .body("size", equalTo(2))
            .body("totalElements", equalTo(3))
            .body("totalPages", equalTo(2))
            .body("content.size()", is(2))
            .body("content[0].description", equalTo("Item A"))
            .body("content[1].description", equalTo("Item B"));

        // Valida 400 para sortBy inválido
        given()
            .queryParam("sortBy", "campoInvalido")
        .when()
            .get("/transactions")
        .then()
            .statusCode(400)
            .body("message", containsString("Campo de ordenação inválido"));
    }

    @Test
    @DisplayName("Deve calcular evolução mensal e projeção de parcelas futuras sem duplicar compras")
    void shouldCalculateMonthlyEvolutionAndInstallmentProjection() {
        // Compra parcelada: Parcela 1/3 em Setembro e Parcela 2/3 em Outubro (falta apenas a 3/3 em Novembro!)
        TransactionRequestDTO p1 = new TransactionRequestDTO();
        p1.description = "Notebook Dell - Parcela 1/3";
        p1.transactionType = TransactionType.EXPENSE;
        p1.categoryId = outrosId;
        p1.amount = new BigDecimal("500.00");
        p1.date = LocalDate.of(2026, 9, 15);
        p1.currentInstallment = 1;
        p1.totalInstallments = 3;
        p1.isCreditCard = true;
        p1.institution = "Nubank";
        given().contentType(ContentType.JSON).body(p1).when().post("/transactions").then().statusCode(201);

        TransactionRequestDTO p2 = new TransactionRequestDTO();
        p2.description = "Notebook Dell - Parcela 2/3";
        p2.transactionType = TransactionType.EXPENSE;
        p2.categoryId = outrosId;
        p2.amount = new BigDecimal("500.00");
        p2.date = LocalDate.of(2026, 10, 15);
        p2.currentInstallment = 2;
        p2.totalInstallments = 3;
        p2.isCreditCard = true;
        p2.institution = "Nubank";
        given().contentType(ContentType.JSON).body(p2).when().post("/transactions").then().statusCode(201);

        // Receita em Outubro
        createSampleTransaction("Salário", TransactionType.INCOME, outrosId, "3000.00", LocalDate.of(2026, 10, 5));

        // 1. Valida evolução mensal (Setembro e Outubro em ordem cronológica)
        given()
        .when()
            .get("/transactions/monthly-evolution")
        .then()
            .statusCode(200)
            .body("size()", is(2))
            .body("[0].year", equalTo(2026))
            .body("[0].month", equalTo(9))
            .body("[0].totalExpense", equalTo(500.00f))
            .body("[1].month", equalTo(10))
            .body("[1].totalIncome", equalTo(3000.00f))
            .body("[1].totalExpense", equalTo(500.00f))
            .body("[1].balance", equalTo(2500.00f));

        // 2. Valida projeção de parcelas futuras: deve projetar apenas a Parcela 3/3 em Novembro/2026
        given()
            .queryParam("months", 6)
        .when()
            .get("/transactions/installments/projection")
        .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].year", equalTo(2026))
            .body("[0].month", equalTo(11))
            .body("[0].totalCommitted", equalTo(500.00f))
            .body("[0].installmentsCount", equalTo(1))
            .body("[0].items[0].description", equalTo("Notebook Dell - Parcela 3/3"))
            .body("[0].items[0].projectedInstallment", equalTo(3));
    }

    @Test
    @DisplayName("Deve filtrar por intervalo customizado (ciclo de fatura startDate/endDate) e filtros combinados")
    void shouldFilterByCustomDateRangeAndCriteria() {
        // Fora do ciclo (19/09)
        createSampleTransaction("Compra Antiga", TransactionType.EXPENSE, alimentacaoId, "90.00", LocalDate.of(2026, 9, 19));
        // Dentro do ciclo (20/09 a 19/10)
        createSampleTransaction("iFood Pizzaria", TransactionType.EXPENSE, alimentacaoId, "80.00", LocalDate.of(2026, 9, 20));
        createSampleTransaction("iFood Mercado", TransactionType.EXPENSE, alimentacaoId, "120.00", LocalDate.of(2026, 10, 5));
        createSampleTransaction("Show Musical", TransactionType.EXPENSE, lazerId, "200.00", LocalDate.of(2026, 10, 19));
        // Fora do ciclo (20/10 - próxima fatura)
        createSampleTransaction("Próxima Fatura", TransactionType.EXPENSE, lazerId, "300.00", LocalDate.of(2026, 10, 20));

        // 1. Resumo exato do ciclo da fatura (20/09/2026 a 19/10/2026): 80 + 120 + 200 = 400
        given()
            .queryParam("startDate", "2026-09-20")
            .queryParam("endDate", "2026-10-19")
        .when()
            .get("/transactions/summary")
        .then()
            .statusCode(200)
            .body("totalExpense", equalTo(400.00f))
            .body("balance", equalTo(-400.00f));

        // 2. Filtro combinado: dentro do ciclo + categoria Alimentação + descrição contendo "ifood"
        given()
            .queryParam("startDate", "2026-09-20")
            .queryParam("endDate", "2026-10-19")
            .queryParam("categoryId", alimentacaoId)
            .queryParam("type", "EXPENSE")
            .queryParam("description", "ifood")
        .when()
            .get("/transactions")
        .then()
            .statusCode(200)
            .body("size()", is(2));

        // 3. Valida erro 400 quando startDate > endDate
        given()
            .queryParam("startDate", "2026-10-25")
            .queryParam("endDate", "2026-10-01")
        .when()
            .get("/transactions")
        .then()
            .statusCode(400)
            .body("message", containsString("data inicial não pode ser posterior"));
    }

    @Test
    @DisplayName("Deve abater estorno da categoria correspondente no cálculo de gastos por categoria")
    void shouldDeductRefundFromCategoryExpenses() {
        // Compra de R$ 100.00 em Lazer
        createSampleTransaction("Ingresso Show", TransactionType.EXPENSE, lazerId, "100.00", LocalDate.of(2026, 10, 10));
        // Estorno de R$ 40.00 em Lazer
        createSampleTransaction("Estorno Ingresso Show", TransactionType.INCOME, lazerId, "40.00", LocalDate.of(2026, 10, 12));

        // Deve exibir gasto líquido de R$ 60.00 em Lazer
        given()
            .queryParam("year", 2026)
            .queryParam("month", 10)
        .when()
            .get("/transactions/by-category")
        .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].categoryName", equalTo("Lazer"))
            .body("[0].totalAmount", equalTo(60.00f))
            .body("[0].percentage", equalTo(100.0f));
    }

    @Test
    @DisplayName("Deve abater qualquer INCOME de cartao de credito mesmo sem a palavra estorno na descricao")
    void shouldDeductCreditCardIncomeEvenWithoutRefundWordInDescription() {
        // Compra no cartão de R$ 80.00
        createSampleTransaction("Uber - NuPay", TransactionType.EXPENSE, alimentacaoId, "80.00", LocalDate.of(2026, 10, 5), true);
        // Devolução no cartão de R$ 30.00 com descrição genérica (sem a palavra 'estorno')
        createSampleTransaction("Uber - NuPay", TransactionType.INCOME, alimentacaoId, "30.00", LocalDate.of(2026, 10, 6), true);

        // Deve abater e exibir gasto líquido de R$ 50.00 em Alimentação
        given()
            .queryParam("year", 2026)
            .queryParam("month", 10)
        .when()
            .get("/transactions/by-category")
        .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].categoryName", equalTo("Alimentação"))
            .body("[0].totalAmount", equalTo(50.00f))
            .body("[0].percentage", equalTo(100.0f));
    }

    private void createSampleTransaction(String desc, TransactionType type, Long catId, String amount, LocalDate date) {
        createSampleTransaction(desc, type, catId, amount, date, false, null);
    }

    private void createSampleTransaction(String desc, TransactionType type, Long catId, String amount, LocalDate date, boolean isCreditCard) {
        createSampleTransaction(desc, type, catId, amount, date, isCreditCard, null);
    }

    private void createSampleTransaction(String desc, TransactionType type, Long catId, String amount, LocalDate date, boolean isCreditCard, String institution) {
        TransactionRequestDTO dto = new TransactionRequestDTO();
        dto.description = desc;
        dto.transactionType = type;
        dto.categoryId = catId;
        dto.amount = new BigDecimal(amount);
        dto.date = date;
        dto.isCreditCard = isCreditCard;
        dto.institution = institution;

        given().contentType(ContentType.JSON).body(dto)
            .when().post("/transactions")
            .then().statusCode(201);
    }
}
