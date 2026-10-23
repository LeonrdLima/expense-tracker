package org.acme.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.acme.exception.CategoryNotFoundException;
import org.acme.exception.TransactionNotFoundException;
import org.acme.model.Category;
import org.acme.model.Transaction;
import org.acme.model.TransactionType;
import org.acme.rest.dto.CategorySummaryDTO;
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

    @Transactional 
    public Transaction update(Long id, TransactionRequestDTO dto) {
        Transaction transaction = Transaction.findById(id);
        if(transaction == null) {
            throw new TransactionNotFoundException("Transação não encontrada para o ID informado");
        }

        Category category = Category.findById(dto.categoryId);
        if(category == null) {
            throw new CategoryNotFoundException("Categoria não encontrada para o ID informado");
        }

        transaction.description = dto.description;
        transaction.transactionType = dto.transactionType;
        transaction.amount = dto.amount;
        transaction.date = dto.date;
        transaction.category = category;
        transaction.beneficiary = dto.beneficiary;

        return transaction;
    }

    public List<CategorySummaryDTO> getExpensesByCategory(Integer year, Integer month) {
        List<Transaction> transactions = list(year, month);
        Map<String, BigDecimal> categoryTotals = new HashMap<>();

        for(Transaction t : transactions) {
            if(t.transactionType == TransactionType.EXPENSE) {
                String categoryName = t.category.name;
                BigDecimal current = categoryTotals.getOrDefault(categoryName, BigDecimal.ZERO);
                categoryTotals.put(categoryName, current.add(t.amount));
            }
        }
        
        BigDecimal totalExpense = BigDecimal.ZERO;
        for(BigDecimal amount : categoryTotals.values()) {
            totalExpense = totalExpense.add(amount);
        }

        List<CategorySummaryDTO> result = new ArrayList<>();
        
        for(Map.Entry<String, BigDecimal> entry : categoryTotals.entrySet()) {
            String categoryName = entry.getKey();
            BigDecimal amount = entry.getValue();

            Double percentage = 0.0;

            if(totalExpense.compareTo(BigDecimal.ZERO) > 0) {
                double rawPercentage = (amount.doubleValue() / totalExpense.doubleValue()) * 100.0;
                percentage = BigDecimal.valueOf(rawPercentage)
                                       .setScale(2, RoundingMode.HALF_UP)
                                       .doubleValue();
            }

            result.add(new CategorySummaryDTO(categoryName, amount, percentage));
        }

        return result;        
    }

    @Transactional
    public List<Transaction> persistUnique(List<Transaction> transactions) {
        List<Transaction> saved = new ArrayList<>();
        Map<String, Long> existingCountsCache = new HashMap<>();
        Map<String, Long> seenInBatchCounts = new HashMap<>();

        for(Transaction t : transactions) {
            String key = t.date + "|" + t.description + "|" + t.amount.stripTrailingZeros().toPlainString();
            long existingInDb = existingCountsCache.computeIfAbsent(key, k -> Transaction.count("date = ?1 and description = ?2 and amount = ?3", t.date, t.description, t.amount));
            long seenNow = seenInBatchCounts.getOrDefault(key, 0L) + 1;
            seenInBatchCounts.put(key, seenNow);

            if(seenNow > existingInDb) {
                t.persist();
                saved.add(t);
            }
        }
        return saved;
    }

}
