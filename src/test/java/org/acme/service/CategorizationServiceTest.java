package org.acme.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.model.Category;
import org.acme.model.TransactionType;
import org.acme.rest.dto.CategorizationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class CategorizationServiceTest {

    @Inject
    CategorizationService categorizationService;

    @Test
    @DisplayName("Deve limpar sufixo de parcelas e espaços da descrição")
    void shouldCleanDescriptionProperly() {
        String raw = "  Magazine Luiza - Parcela 02/10   ";
        String cleaned = categorizationService.cleanForMatching(raw);

        assertEquals("magazine luiza", cleaned);
    }

    @Test
    @DisplayName("Deve categorizar como despesa quando o valor for negativo")
    void shouldCategorizeAsExpenseWhenAmountNegative() {
        Category defaultCategory = categorizationService.findOrCreate("Outros");
        CategorizationResult result = categorizationService.categorize(
            "Compra Qualquer",
            new BigDecimal("-50.00"),
            defaultCategory
        );

        assertEquals(TransactionType.EXPENSE, result.type());
        assertEquals("Outros", result.category().name);
    }

    @Test
    @Transactional
    @DisplayName("Deve aprender novo padrão e usá-lo na próxima categorização")
    void shouldLearnPatternAndCategorizeCorrectly() {
        Category mercado = categorizationService.findOrCreate("Supermercado");
        categorizationService.learn("Pão de Açúcar Loja 123", mercado);

        Category defaultCategory = categorizationService.findOrCreate("Outros");
        CategorizationResult result = categorizationService.categorize(
            "Pão de Açúcar Loja 123 - Parcela 1/2",
            new BigDecimal("-120.00"),
            defaultCategory
        );

        assertEquals("Supermercado", result.category().name);
        assertEquals(TransactionType.EXPENSE, result.type());
    }
}
