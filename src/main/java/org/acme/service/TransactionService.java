package org.acme.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.acme.exception.CategoryNotFoundException;
import org.acme.exception.TransactionNotFoundException;
import org.acme.model.Category;
import org.acme.model.Transaction;
import org.acme.model.TransactionType;
import org.acme.rest.dto.SummaryResponseDTO;
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
        transaction.beneficiary = dto.beneficiary;
        transaction.persist();

        return transaction;
    }

    public List<Transaction> listAll() {
        return Transaction.listAll();
    }

    public List<Transaction> list(Integer year, Integer month) {
        if(year == null || month == null) {
            return Transaction.listAll();
        }
        
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        return Transaction.list("date between ?1 and ?2 order by date desc", startDate, endDate);
    }

    public SummaryResponseDTO calculateSummary(Integer year, Integer month) {
        List<Transaction> transactions = list(year, month);

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for(Transaction t : transactions) {
            if(t.transactionType == TransactionType.INCOME) {
                totalIncome = totalIncome.add(t.amount);
            } else if(t.transactionType == TransactionType.EXPENSE) {
                totalExpense = totalExpense.add(t.amount);
            }
        }
        
        BigDecimal balance = totalIncome.subtract(totalExpense);

        return new SummaryResponseDTO(totalIncome, totalExpense, balance);
    }
    
    @Transactional 
    public void delete(Long id) {
        Transaction t = Transaction.findById(id);
        
        if(t == null) {
            throw new TransactionNotFoundException("Transação não encontrada para o ID informado");
        }

        t.delete();
    }
}
