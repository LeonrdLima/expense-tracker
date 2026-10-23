package org.acme.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "category_patterns")
public class CategoryPattern extends PanacheEntity {
    
    @NotBlank
    @Column(nullable = false, unique = true)
    public String pattern;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    public Category category;

    public static CategoryPattern findByPattern(String pattern) {
        return find("pattern", pattern.toLowerCase().trim()).firstResult();
    }
}
