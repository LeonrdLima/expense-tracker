package org.acme.rest;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.model.CategoryPattern;
import org.acme.model.Transaction;
import org.acme.service.PdfImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class ImportResourceTest {

    @Inject
    PdfImportService pdfImportService;

    @BeforeEach
    @Transactional
    void setUp() {
        Transaction.deleteAll();
        CategoryPattern.deleteAll();
    }

    @Test
    @DisplayName("Deve importar CSV de extrato bancário e evitar duplicidade ao reimportar o mesmo arquivo")
    void shouldImportCsvAndPreventDuplicates() {
        String csvContent = """
            Data;Descrição;Valor
            10/10/2026;Salário Empresa;5000,00
            12/10/2026;Supermercado Central;-250,75
            """;

        // 1ª importação: deve salvar as 2 transações
        given()
            .multiPart("file", "extrato.csv", csvContent.getBytes(StandardCharsets.UTF_8), "text/csv")
        .when()
            .post("/transactions/import/csv")
        .then()
            .statusCode(201)
            .body("size()", is(2))
            .body("find { it.description == 'Salário Empresa' }.transactionType", equalTo("INCOME"))
            .body("find { it.description == 'Supermercado Central' }.transactionType", equalTo("EXPENSE"))
            .body("find { it.description == 'Supermercado Central' }.amount", equalTo(250.75f));

        // 2ª importação do mesmo arquivo: não deve duplicar nada (retorna lista vazia de novos salvos)
        given()
            .multiPart("file", "extrato.csv", csvContent.getBytes(StandardCharsets.UTF_8), "text/csv")
        .when()
            .post("/transactions/import/csv")
        .then()
            .statusCode(201)
            .body("size()", is(0));
    }

    @Test
    @DisplayName("Deve importar CSV de cartão de crédito convertendo valores positivos em EXPENSE, detectando Nubank e ignorando Pagamento recebido")
    void shouldImportCreditCardCsvAsExpense() {
        String creditCardCsv = """
            date,title,amount
            2026-10-15,Pagamento recebido,-1200.00
            2026-10-18,Netflix.Com,55.90
            """;

        given()
            .queryParam("creditCard", true)
            .multiPart("file", "fatura.csv", creditCardCsv.getBytes(StandardCharsets.UTF_8), "text/csv")
        .when()
            .post("/transactions/import/csv")
        .then()
            .statusCode(201)
            .body("size()", is(1))
            .body("[0].description", equalTo("Netflix.Com"))
            .body("[0].transactionType", equalTo("EXPENSE"))
            .body("[0].amount", equalTo(55.90f))
            .body("[0].isCreditCard", equalTo(true))
            .body("[0].institution", equalTo("Nubank"));
    }

    @Test
    @DisplayName("Deve importar arquivo OFX extraindo data, valor, parcelas e detectando instituição pelo BANKID")
    void shouldImportOfxWithInstallments() {
        String ofxContent = """
            <OFX>
              <FI>
                <ORG>Banco Inter S.A.
                <FID>0077
              </FI>
              <BANKTRANLIST>
                <STMTTRN>
                  <TRNTYPE>DEBIT
                  <DTPOSTED>20261020120000[-3:BRT]
                  <TRNAMT>-199.90
                  <MEMO>Amazon Br - Parcela 2/5
                </STMTTRN>
              </BANKTRANLIST>
            </OFX>
            """;

        given()
            .multiPart("file", "extrato.ofx", ofxContent.getBytes(StandardCharsets.UTF_8), "application/x-ofx")
        .when()
            .post("/transactions/import/ofx")
        .then()
            .statusCode(201)
            .body("size()", is(1))
            .body("[0].description", equalTo("Amazon Br - Parcela 2/5"))
            .body("[0].transactionType", equalTo("EXPENSE"))
            .body("[0].amount", equalTo(199.90f))
            .body("[0].currentInstallment", equalTo(2))
            .body("[0].totalInstallments", equalTo(5))
            .body("[0].institution", equalTo("Inter"));
    }

    @Test
    @DisplayName("Deve converter valores monetários de PDF em diferentes formatos brasileiros")
    void shouldParsePdfAmountsCorrectly() {
        assertEquals(new BigDecimal("-1250.50"), pdfImportService.parseAmount("R$ 1.250,50 D"));
        assertEquals(new BigDecimal("-45.90"), pdfImportService.parseAmount("-45,90"));
        assertEquals(new BigDecimal("300.00"), pdfImportService.parseAmount("300,00 C"));
    }

    @Test
    @DisplayName("Nao deve inverter sinais de extrato bancario mesmo se creditCard=true for enviado por engano")
    void shouldNotInvertBankStatementEvenIfCreditCardTrue() {
        String nubankAccountCsv = """
            Data,Valor,Identificador,Descrição
            15/10/2026,-85.00,abc-123,Transferência enviada pelo Pix - Fulano
            16/10/2026,200.00,def-456,Transferência recebida pelo Pix - Ciclano
            """;

        given()
            .queryParam("creditCard", true)
            .multiPart("file", "NU_extrato.csv", nubankAccountCsv.getBytes(StandardCharsets.UTF_8), "text/csv")
        .when()
            .post("/transactions/import/csv")
        .then()
            .statusCode(201)
            .body("size()", is(2))
            .body("find { it.description.contains('enviada pelo Pix') }.transactionType", equalTo("EXPENSE"))
            .body("find { it.description.contains('enviada pelo Pix') }.isCreditCard", equalTo(false))
            .body("find { it.description.contains('recebida pelo Pix') }.transactionType", equalTo("INCOME"))
            .body("find { it.description.contains('recebida pelo Pix') }.isCreditCard", equalTo(false));
    }
}
