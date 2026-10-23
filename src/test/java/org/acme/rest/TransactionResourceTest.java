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

    private void createSampleTransaction(String desc, TransactionType type, Long catId, String amount, LocalDate date) {
        TransactionRequestDTO dto = new TransactionRequestDTO();
        dto.description = desc;
        dto.transactionType = type;
        dto.categoryId = catId;
        dto.amount = new BigDecimal(amount);
        dto.date = date;

        given().contentType(ContentType.JSON).body(dto)
            .when().post("/transactions")
            .then().statusCode(201);
    }
}
