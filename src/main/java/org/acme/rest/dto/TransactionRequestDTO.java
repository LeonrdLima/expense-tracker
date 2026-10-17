package org.acme.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.acme.model.TransactionType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class TransactionRequestDTO {
    @NotBlank
    public String description;

    @NotNull
    public TransactionType transactionType;
    
    @NotNull 
    public Long categoryId;

    @Positive 
    @NotNull 
    public BigDecimal amount;

    @NotNull
    public LocalDate date;

    public String beneficiary;

}
