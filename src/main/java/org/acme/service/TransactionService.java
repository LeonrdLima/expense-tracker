package org.acme.service;

import java.util.List;

import org.acme.exception.CategoryNotFoundException;
import org.acme.model.Category;
import org.acme.model.Transaction;
import org.acme.rest.dto.TransactionRequestDTO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped 
public class TransactionService {

    @Transactional 
    public Transaction create(TransactionRequestDTO dto) {
        Category category = Category.findById(dto.categoryId);
        if(category == null) {
            throw new CategoryNotFoundException("Categoria não encontrada para o ID informado");
        }

        Transaction transaction = new Transaction();
        transaction.description = dto.description;
        transaction.transactionType = dto.transactionType;
        transaction.amount = dto.amount;
        transaction.date = dto.date;
        transaction.category = category;
        transaction.persist();

        return transaction;
    }

    public List<Transaction> listAll() {
        return Transaction.listAll();
    }
    
}
