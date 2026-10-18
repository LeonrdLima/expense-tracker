package org.acme.service;

import java.math.BigDecimal;

import org.acme.model.Category;
import org.acme.model.TransactionType;
import org.acme.rest.dto.CategorizationResult;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped 
public class CategorizationService {
    public CategorizationResult categorize(String description, BigDecimal rawAmount, Category defaultCategory) {
        String desc = description.toLowerCase();
        
        if(desc.contains("rdb") || desc.contains("crédito em conta") || desc.contains("guardado") || desc.contains("caixinha") || desc.contains("nuinvest")) {
            return new CategorizationResult(findOrCreate("Investimentos"), TransactionType.TRANSFER);
        }

        if(desc.contains("transferência enviada pelo pix")) {
            if(!isCnpj(description)) {
                return new CategorizationResult(findOrCreate("Transferência Pessoal"), TransactionType.TRANSFER);
            }
        }

        TransactionType defaultType = (rawAmount.compareTo(BigDecimal.ZERO) < 0) 
            ? TransactionType.EXPENSE 
            : TransactionType.INCOME;

        if(desc.contains("ifood") || desc.contains("mercado") || desc.contains("supermercado") || desc.contains("padaria") || desc.contains("restaurante") || desc.contains("hortifruti") || desc.contains("biscoito")) {
            return new CategorizationResult(findOrCreate("Alimentação"), defaultType);
        }

        else if(desc.contains("uber") || desc.contains("99app") || desc.contains("posto") || desc.contains("combustível") || desc.contains("estacionamento")) {
            return new CategorizationResult(findOrCreate("Transporte"), defaultType);
        }

        else if(desc.contains("farmacia") || desc.contains("drogaria") || desc.contains("raia") || desc.contains("navalha") || desc.contains("salao") || desc.contains("barbearia")) {
            return new CategorizationResult(findOrCreate("Saúde"), defaultType);
        }

        else if(desc.contains("steam") || desc.contains("nuuvem")) {
            return new CategorizationResult(findOrCreate("Lazer"), defaultType);
        }

        else if(desc.contains("spotify") || desc.contains("amazon") || desc.contains("meli") || desc.contains("youtube") || desc.contains("netflix")) {
            return new CategorizationResult(findOrCreate("Assinaturas"), defaultType);
        }

        else {
            return new CategorizationResult(defaultCategory, defaultType);
        }
    }

    public Category findOrCreate(String name) {
        Category category = Category.find("lower(name)", name.toLowerCase()).firstResult();
        if(category == null) {
            category = new Category();
            category.name = name;
            category.persist();
        }
        return category;
    }

    public boolean isCnpj(String description) {
        return description.contains("/0001") || description.contains("/0002");
    }
}
