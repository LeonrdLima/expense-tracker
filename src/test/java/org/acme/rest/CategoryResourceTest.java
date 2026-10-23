package org.acme.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.rest.dto.CategoryRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("Deve criar uma categoria com sucesso")
    void shouldCreateCategory() {
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
}
