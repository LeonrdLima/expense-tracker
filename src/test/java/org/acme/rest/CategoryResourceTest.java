package org.acme.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.model.TransactionType;
import org.acme.rest.dto.CategoryRequestDTO;
import org.acme.rest.dto.TransactionRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class CategoryResourceTest {

    @Test
    @DisplayName("Deve criar e listar as categorias existentes")
    void shouldListCategories() {
        CategoryRequestDTO dto = new CategoryRequestDTO();
        dto.name = "Teste-" + System.currentTimeMillis();

        given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/categories")
        .then()
            .statusCode(201);

        given()
            .when().get("/categories")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    @DisplayName("Deve criar uma categoria com sucesso e rejeitar duplicata com ErrorResponse padronizado")
    void shouldCreateCategoryAndRejectDuplicate() {
        CategoryRequestDTO dto = new CategoryRequestDTO();
        dto.name = "Transporte-" + System.currentTimeMillis();

        given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/categories")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo(dto.name));

        // Tenta criar novamente com o mesmo nome
        given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/categories")
        .then()
            .statusCode(400)
            .body("status", equalTo(400))
            .body("message", containsString("Categoria já existe"));
    }

    @Test
    @DisplayName("Deve rejeitar categoria com nome em branco")
    void shouldRejectBlankCategoryName() {
        CategoryRequestDTO dto = new CategoryRequestDTO();
        dto.name = "   ";

        given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/categories")
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("Deve buscar por ID, atualizar e deletar categoria sem transações vinculadas")
    void shouldFindUpdateAndDeleteCategory() {
        CategoryRequestDTO dto = new CategoryRequestDTO();
        dto.name = "Saúde-" + System.currentTimeMillis();

        Integer id = given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/categories")
        .then()
            .statusCode(201)
            .extract().path("id");

        // Busca por ID
        given()
            .when().get("/categories/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo(dto.name));

        // Atualiza nome
        CategoryRequestDTO updateDto = new CategoryRequestDTO();
        updateDto.name = dto.name + "-Atualizado";

        given()
            .contentType(ContentType.JSON)
            .body(updateDto)
        .when()
            .put("/categories/" + id)
        .then()
            .statusCode(200)
            .body("name", equalTo(updateDto.name));

        // Deleta categoria sem transações
        given()
            .when().delete("/categories/" + id)
            .then()
            .statusCode(204);

        // Verifica 404 após deleção
        given()
            .when().get("/categories/" + id)
            .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("Deve retornar 409 ao tentar excluir categoria em uso por transações")
    void shouldReturn409WhenDeletingCategoryInUse() {
        CategoryRequestDTO catDto = new CategoryRequestDTO();
        catDto.name = "Educação-" + System.currentTimeMillis();

        Integer catId = given()
            .contentType(ContentType.JSON)
            .body(catDto)
        .when()
            .post("/categories")
        .then()
            .statusCode(201)
            .extract().path("id");

        TransactionRequestDTO txDto = new TransactionRequestDTO();
        txDto.description = "Curso Online";
        txDto.transactionType = TransactionType.EXPENSE;
        txDto.categoryId = catId.longValue();
        txDto.amount = new BigDecimal("199.90");
        txDto.date = LocalDate.of(2026, 10, 15);

        given()
            .contentType(ContentType.JSON)
            .body(txDto)
        .when()
            .post("/transactions")
        .then()
            .statusCode(201);

        given()
            .when().delete("/categories/" + catId)
            .then()
            .statusCode(409)
            .body("status", equalTo(409))
            .body("message", containsString("possui transações vinculadas"));
    }
}
