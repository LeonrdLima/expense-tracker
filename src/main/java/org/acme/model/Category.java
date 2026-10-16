package org.acme.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity 
@Table(name = "categories")
public class Category extends PanacheEntity{
    @NotBlank 
    @Column(nullable = false, unique = true)
    public String name;
    

}
