package org.acme.rest.dto;

import java.math.BigDecimal;

public record MonthlyEvolutionDTO(
    int year,
    int month,
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal balance,
    int transactionCount
) {}
