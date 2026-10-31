package org.acme.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentProjectionItemDTO(
    String description,
    String categoryName,
    String institution,
    int projectedInstallment,
    int totalInstallments,
    BigDecimal amount,
    LocalDate projectedDate
) {}
