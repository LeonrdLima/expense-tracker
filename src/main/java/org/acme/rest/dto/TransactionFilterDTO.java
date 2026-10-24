package org.acme.rest.dto;

import java.time.LocalDate;

import org.acme.model.TransactionType;

import jakarta.ws.rs.QueryParam;

public class TransactionFilterDTO {

    @QueryParam("year")
    public Integer year;

    @QueryParam("month")
    public Integer month;

    @QueryParam("startDate")
    public LocalDate startDate;

    @QueryParam("endDate")
    public LocalDate endDate;

    @QueryParam("categoryId")
    public Long categoryId;

    @QueryParam("type")
    public TransactionType type;

    @QueryParam("description")
    public String description;

    @QueryParam("isCreditCard")
    public Boolean isCreditCard;

    @QueryParam("institution")
    public String institution;
}
