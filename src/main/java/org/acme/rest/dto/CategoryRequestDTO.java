package org.acme.rest.dto;

import jakarta.validation.constraints.NotBlank;

public class CategoryRequestDTO {
    @NotBlank 
    public String name;
}
