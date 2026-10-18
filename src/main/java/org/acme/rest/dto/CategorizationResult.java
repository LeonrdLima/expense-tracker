package org.acme.rest.dto;

import org.acme.model.Category;
import org.acme.model.TransactionType;

public record CategorizationResult(Category category, TransactionType type) {}
