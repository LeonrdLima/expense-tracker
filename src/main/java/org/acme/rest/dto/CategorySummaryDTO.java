package org.acme.rest.dto;

import java.math.BigDecimal;

public record CategorySummaryDTO(
    String categoryName,
    BigDecimal totalAmount,
    Double percentage
) {}
