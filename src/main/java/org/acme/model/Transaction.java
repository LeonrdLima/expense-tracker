package org.acme.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class Transaction extends PanacheEntity {
    public String description;

    @Enumerated(EnumType.STRING)
    public TransactionType transactionType;
    
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    public Category category;

    public BigDecimal amount;
    public LocalDate date;
}
