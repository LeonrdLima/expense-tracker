package org.acme.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.smallrye.common.constraint.NotNull;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Entity
@Table(name = "transactions")
public class Transaction extends PanacheEntity {
    @NotBlank 
    public String description;
    
    @Enumerated(EnumType.STRING) 
    @NotNull
    public TransactionType transactionType;
    
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    @NotNull
    public Category category;
    
    @Positive
    @NotNull
    public BigDecimal amount;

    @NotNull
    public LocalDate date;
}
