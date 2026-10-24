package org.acme.rest.dto;

import org.acme.model.TransactionType;

public class TransactionPatchDTO {
    public Long categoryId;
    public String beneficiary;
    public TransactionType transactionType;
    public String institution;
}
