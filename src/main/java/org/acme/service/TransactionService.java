package org.acme.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.acme.exception.CategoryNotFoundException;
import org.acme.exception.InvalidFilterException;
import org.acme.exception.TransactionNotFoundException;
import org.acme.model.Category;
import org.acme.model.Transaction;
import org.acme.model.TransactionType;
import org.acme.rest.dto.CategorySummaryDTO;
import org.acme.rest.dto.SummaryResponseDTO;
import org.acme.rest.dto.TransactionFilterDTO;
import org.acme.rest.dto.TransactionPatchDTO;
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
        transaction.currentInstallment = dto.currentInstallment;
        transaction.totalInstallments = dto.totalInstallments;
        transaction.isCreditCard = Boolean.TRUE.equals(dto.isCreditCard);
        transaction.institution = dto.institution;
        transaction.persist();

        return transaction;
    }

    public List<Transaction> listAll() {
        return Transaction.listAll();
    }

    public List<Transaction> list(Integer year, Integer month) {
        TransactionFilterDTO filter = new TransactionFilterDTO();
        filter.year = year;
        filter.month = month;
        return list(filter);
    }

    public List<Transaction> list(TransactionFilterDTO filter) {
        if(filter == null) {
            return Transaction.list("order by date desc");
        }

        LocalDate effectiveStart = filter.startDate;
        LocalDate effectiveEnd = filter.endDate;

        // Se startDate/endDate não foram informados, usa year/month como fallback
        if(effectiveStart == null && effectiveEnd == null && filter.year != null) {
            if(filter.month != null) {
                if(filter.month < 1 || filter.month > 12) {
                    throw new InvalidFilterException("Mês inválido: deve estar entre 1 e 12");
                }
                effectiveStart = LocalDate.of(filter.year, filter.month, 1);
                effectiveEnd = effectiveStart.withDayOfMonth(effectiveStart.lengthOfMonth());
            } else {
                effectiveStart = LocalDate.of(filter.year, 1, 1);
                effectiveEnd = LocalDate.of(filter.year, 12, 31);
            }
        }

        if(effectiveStart != null && effectiveEnd != null && effectiveStart.isAfter(effectiveEnd)) {
            throw new InvalidFilterException("A data inicial não pode ser posterior à data final");
        }

        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if(effectiveStart != null) {
            conditions.add("date >= :startDate");
            params.put("startDate", effectiveStart);
        }

        if(effectiveEnd != null) {
            conditions.add("date <= :endDate");
            params.put("endDate", effectiveEnd);
        }

        if(filter.categoryId != null) {
            conditions.add("category.id = :categoryId");
            params.put("categoryId", filter.categoryId);
        }

        if(filter.type != null) {
            conditions.add("transactionType = :type");
            params.put("type", filter.type);
        }

        if(filter.description != null && !filter.description.isBlank()) {
            conditions.add("lower(description) like :description");
            params.put("description", "%" + filter.description.trim().toLowerCase() + "%");
        }

        if(filter.isCreditCard != null) {
            conditions.add("isCreditCard = :isCreditCard");
            params.put("isCreditCard", filter.isCreditCard);
        }

        if(filter.institution != null && !filter.institution.isBlank()) {
            conditions.add("lower(institution) = :institution");
            params.put("institution", filter.institution.trim().toLowerCase());
        }

        if(conditions.isEmpty()) {
            return Transaction.list("order by date desc");
        }

        String query = String.join(" and ", conditions) + " order by date desc";
        return Transaction.list(query, params);
    }

    public SummaryResponseDTO calculateSummary(Integer year, Integer month) {
        TransactionFilterDTO filter = new TransactionFilterDTO();
        filter.year = year;
        filter.month = month;
        return calculateSummary(filter);
    }

    public SummaryResponseDTO calculateSummary(TransactionFilterDTO filter) {
        List<Transaction> transactions = list(filter);

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
    public void deleteAll() {
        Transaction.deleteAll();
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
        if(dto.currentInstallment != null) {
            transaction.currentInstallment = dto.currentInstallment;
        }
        if(dto.totalInstallments != null) {
            transaction.totalInstallments = dto.totalInstallments;
        }
        if(dto.isCreditCard != null) {
            transaction.isCreditCard = dto.isCreditCard;
        }
        if(dto.institution != null) {
            transaction.institution = dto.institution;
        }

        return transaction;
    }

    @Transactional
    public Transaction partialUpdate(Long id, TransactionPatchDTO dto) {
        Transaction transaction = Transaction.findById(id);
        if(transaction == null) {
            throw new TransactionNotFoundException("Transação não encontrada para o ID informado");
        }

        if(dto.categoryId != null) {
            Category category = Category.findById(dto.categoryId);
            if(category == null) {
                throw new CategoryNotFoundException("Categoria não encontrada para o ID informado");
            }
            transaction.category = category;
        }
        if(dto.beneficiary != null) {
            transaction.beneficiary = dto.beneficiary;
        }
        if(dto.transactionType != null) {
            transaction.transactionType = dto.transactionType;
        }
        if(dto.institution != null) {
            transaction.institution = dto.institution;
        }

        return transaction;
    }

    public List<CategorySummaryDTO> getExpensesByCategory(Integer year, Integer month) {
        TransactionFilterDTO filter = new TransactionFilterDTO();
        filter.year = year;
        filter.month = month;
        return getExpensesByCategory(filter);
    }

    public List<CategorySummaryDTO> getExpensesByCategory(TransactionFilterDTO filter) {
        List<Transaction> transactions = list(filter);
        Map<String, BigDecimal> categoryExpenses = new HashMap<>();
        Map<String, BigDecimal> categoryRefunds = new HashMap<>();

        for(Transaction t : transactions) {
            String categoryName = t.category.name;

            if(t.transactionType == TransactionType.EXPENSE) {
                BigDecimal current = categoryExpenses.getOrDefault(categoryName, BigDecimal.ZERO);
                categoryExpenses.put(categoryName, current.add(t.amount));
            } else if(t.transactionType == TransactionType.INCOME && (Boolean.TRUE.equals(t.isCreditCard) || isRefund(t.description))) {
                BigDecimal current = categoryRefunds.getOrDefault(categoryName, BigDecimal.ZERO);
                categoryRefunds.put(categoryName, current.add(t.amount));
            }
        }

        Map<String, BigDecimal> netTotals = new HashMap<>();
        BigDecimal totalExpense = BigDecimal.ZERO;

        for(Map.Entry<String, BigDecimal> entry : categoryExpenses.entrySet()) {
            String categoryName = entry.getKey();
            BigDecimal expenses = entry.getValue();
            BigDecimal refunds = categoryRefunds.getOrDefault(categoryName, BigDecimal.ZERO);
            BigDecimal net = expenses.subtract(refunds);

            if(net.compareTo(BigDecimal.ZERO) > 0) {
                netTotals.put(categoryName, net);
                totalExpense = totalExpense.add(net);
            }
        }

        List<CategorySummaryDTO> result = new ArrayList<>();
        
        for(Map.Entry<String, BigDecimal> entry : netTotals.entrySet()) {
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

    private boolean isRefund(String description) {
        if(description == null) return false;
        String lower = description.toLowerCase();
        return lower.contains("estorno") || lower.contains("reembolso") || lower.contains("devolucao") || lower.contains("devolução");
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
