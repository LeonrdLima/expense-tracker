package org.acme.rest.dto;

import java.math.BigDecimal;

public record SummaryResponseDTO(
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal balance
) {}
