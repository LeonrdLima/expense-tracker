package org.acme.rest.dto;

import java.util.List;

public record PagedResponseDTO<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
