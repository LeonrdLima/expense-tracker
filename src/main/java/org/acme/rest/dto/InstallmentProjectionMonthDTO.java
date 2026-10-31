package org.acme.rest.dto;

import java.math.BigDecimal;
import java.util.List;

public record InstallmentProjectionMonthDTO(
    int year,
    int month,
    BigDecimal totalCommitted,
    int installmentsCount,
    List<InstallmentProjectionItemDTO> items
) {}
