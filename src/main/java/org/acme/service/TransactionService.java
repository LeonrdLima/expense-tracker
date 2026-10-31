package org.acme.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.acme.exception.CategoryNotFoundException;
import org.acme.exception.InvalidFilterException;
import org.acme.exception.TransactionNotFoundException;
import org.acme.model.Category;
import org.acme.model.Transaction;
import org.acme.model.TransactionType;
import org.acme.rest.dto.CategorySummaryDTO;
import org.acme.rest.dto.InstallmentProjectionItemDTO;
import org.acme.rest.dto.InstallmentProjectionMonthDTO;
import org.acme.rest.dto.MonthlyEvolutionDTO;
import org.acme.rest.dto.PagedResponseDTO;
import org.acme.rest.dto.SummaryResponseDTO;
import org.acme.rest.dto.TransactionFilterDTO;
import org.acme.rest.dto.TransactionPatchDTO;
import org.acme.rest.dto.TransactionRequestDTO;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped 
public class TransactionService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("date", "amount", "description", "id");

    @Inject
    CategorizationService categorizationService;

    @Inject
    InstitutionDetector institutionDetector;

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
        transaction.institution = institutionDetector != null
            ? institutionDetector.canonicalize(dto.institution)
            : dto.institution;
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
        PanacheQuery<Transaction> panacheQuery = buildPanacheQuery(filter);
        if(filter != null && (filter.page != null || filter.size != null)) {
            int page = (filter.page != null) ? filter.page : 0;
            int size = (filter.size != null) ? filter.size : 20;
            validatePagination(page, size);
            return panacheQuery.page(Page.of(page, size)).list();
        }
        return panacheQuery.list();
    }

    public PagedResponseDTO<Transaction> listPaged(TransactionFilterDTO filter) {
        int page = (filter != null && filter.page != null) ? filter.page : 0;
        int size = (filter != null && filter.size != null) ? filter.size : 20;
        validatePagination(page, size);

        PanacheQuery<Transaction> panacheQuery = buildPanacheQuery(filter);
        long totalElements = panacheQuery.count();
        List<Transaction> content = panacheQuery.page(Page.of(page, size)).list();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return new PagedResponseDTO<>(content, page, size, totalElements, totalPages);
    }

    private void validatePagination(int page, int size) {
        if(page < 0) {
            throw new InvalidFilterException("O índice da página não pode ser negativo");
        }
        if(size <= 0 || size > 500) {
            throw new InvalidFilterException("O tamanho da página deve estar entre 1 e 500");
        }
    }

    private PanacheQuery<Transaction> buildPanacheQuery(TransactionFilterDTO filter) {
        String orderByClause = resolveOrderByClause(filter);
        if(filter == null) {
            return Transaction.find(orderByClause);
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
            String canonicalInst = (institutionDetector != null)
                ? institutionDetector.canonicalize(filter.institution)
                : filter.institution.trim();
            conditions.add("lower(institution) = :institution");
            params.put("institution", canonicalInst.toLowerCase());
        }

        if(conditions.isEmpty()) {
            return Transaction.find(orderByClause);
        }

        String query = String.join(" and ", conditions) + " " + orderByClause;
        return Transaction.find(query, params);
    }

    private String resolveOrderByClause(TransactionFilterDTO filter) {
        String sortBy = (filter != null && filter.sortBy != null && !filter.sortBy.isBlank())
            ? filter.sortBy.trim()
            : "date";
        if(!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new InvalidFilterException("Campo de ordenação inválido: " + sortBy + ". Permitidos: date, amount, description, id");
        }

        String sortDir = (filter != null && filter.sortDir != null && !filter.sortDir.isBlank())
            ? filter.sortDir.trim().toLowerCase()
            : "desc";
        if(!sortDir.equals("asc") && !sortDir.equals("desc")) {
            throw new InvalidFilterException("Direção de ordenação inválida: " + sortDir + ". Use asc ou desc");
        }

        return "order by " + sortBy + " " + sortDir;
    }

    public SummaryResponseDTO calculateSummary(Integer year, Integer month) {
        TransactionFilterDTO filter = new TransactionFilterDTO();
        filter.year = year;
        filter.month = month;
        return calculateSummary(filter);
    }

    public SummaryResponseDTO calculateSummary(TransactionFilterDTO filter) {
        List<Transaction> transactions = buildPanacheQuery(filter).list();

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
            transaction.institution = institutionDetector != null
                ? institutionDetector.canonicalize(dto.institution)
                : dto.institution;
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
            transaction.institution = institutionDetector != null
                ? institutionDetector.canonicalize(dto.institution)
                : dto.institution;
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
        List<Transaction> transactions = buildPanacheQuery(filter).list();
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

    public List<MonthlyEvolutionDTO> getMonthlyEvolution(TransactionFilterDTO filter) {
        List<Transaction> transactions = buildPanacheQuery(filter).list();
        Map<YearMonth, List<Transaction>> byMonth = new TreeMap<>();

        for(Transaction t : transactions) {
            if(t.date == null) continue;
            YearMonth ym = YearMonth.from(t.date);
            byMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(t);
        }

        List<MonthlyEvolutionDTO> result = new ArrayList<>();
        for(Map.Entry<YearMonth, List<Transaction>> entry : byMonth.entrySet()) {
            YearMonth ym = entry.getKey();
            List<Transaction> monthTxs = entry.getValue();
            BigDecimal income = BigDecimal.ZERO;
            BigDecimal expense = BigDecimal.ZERO;

            for(Transaction t : monthTxs) {
                if(t.transactionType == TransactionType.INCOME) {
                    income = income.add(t.amount);
                } else if(t.transactionType == TransactionType.EXPENSE) {
                    expense = expense.add(t.amount);
                }
            }

            result.add(new MonthlyEvolutionDTO(
                ym.getYear(),
                ym.getMonthValue(),
                income,
                expense,
                income.subtract(expense),
                monthTxs.size()
            ));
        }

        return result;
    }

    public List<InstallmentProjectionMonthDTO> getInstallmentProjection(int months, String institution) {
        if(months < 1 || months > 60) {
            throw new InvalidFilterException("O número de meses para projeção deve estar entre 1 e 60");
        }

        List<Transaction> installmentTxs;
        if(institution != null && !institution.isBlank()) {
            String canonicalInst = (institutionDetector != null)
                ? institutionDetector.canonicalize(institution)
                : institution.trim();
            installmentTxs = Transaction.list(
                "currentInstallment is not null and totalInstallments is not null and totalInstallments > 1 and lower(institution) = ?1",
                canonicalInst.toLowerCase()
            );
        } else {
            installmentTxs = Transaction.list(
                "currentInstallment is not null and totalInstallments is not null and totalInstallments > 1"
            );
        }

        // Agrupa compras parceladas pela assinatura base (descrição sem parcela + valor + totalInstallments)
        // mantendo apenas a parcela mais recente registrada de cada compra
        Map<String, Transaction> latestByPurchase = new LinkedHashMap<>();
        for(Transaction t : installmentTxs) {
            String baseDesc = (categorizationService != null)
                ? categorizationService.cleanForMatching(t.description)
                : (t.description != null ? t.description.toLowerCase().trim() : "");
            String signature = baseDesc + "|" + t.amount.stripTrailingZeros().toPlainString() + "|" + t.totalInstallments;

            Transaction existing = latestByPurchase.get(signature);
            if(existing == null || t.currentInstallment > existing.currentInstallment) {
                latestByPurchase.put(signature, t);
            }
        }

        Map<YearMonth, List<InstallmentProjectionItemDTO>> projectedByMonth = new TreeMap<>();
        YearMonth currentMonth = YearMonth.now();

        for(Transaction latest : latestByPurchase.values()) {
            if(latest.currentInstallment >= latest.totalInstallments || latest.date == null) {
                continue;
            }

            String baseDesc = latest.description != null
                ? latest.description.replaceAll("(?i)\\s*-\\s*parcela\\s+\\d+/\\d+$", "").trim()
                : "";
            String categoryName = latest.category != null ? latest.category.name : "Outros";

            // Bancos como a Caixa mantêm a data original da compra (1ª parcela) em todas as faturas.
            // Se a data da transação está em um mês anterior ao atual e currentInstallment > 1,
            // calculamos a data de competência da parcela atual somando (currentInstallment - 1) meses.
            YearMonth txMonth = YearMonth.from(latest.date);
            LocalDate currentInstallmentDate = (latest.currentInstallment > 1 && txMonth.isBefore(currentMonth))
                ? latest.date.plusMonths(latest.currentInstallment - 1)
                : latest.date;

            int remaining = latest.totalInstallments - latest.currentInstallment;

            for(int offset = 1; offset <= remaining; offset++) {
                int nextInst = latest.currentInstallment + offset;
                LocalDate projectedDate = currentInstallmentDate.plusMonths(offset);
                YearMonth ym = YearMonth.from(projectedDate);

                if(ym.isBefore(currentMonth)) {
                    continue;
                }

                InstallmentProjectionItemDTO item = new InstallmentProjectionItemDTO(
                    baseDesc + " - Parcela " + nextInst + "/" + latest.totalInstallments,
                    categoryName,
                    latest.institution,
                    nextInst,
                    latest.totalInstallments,
                    latest.amount,
                    projectedDate
                );

                projectedByMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(item);
            }
        }

        List<InstallmentProjectionMonthDTO> response = new ArrayList<>();
        int countMonths = 0;
        for(Map.Entry<YearMonth, List<InstallmentProjectionItemDTO>> entry : projectedByMonth.entrySet()) {
            if(countMonths >= months) break;
            YearMonth ym = entry.getKey();
            List<InstallmentProjectionItemDTO> items = entry.getValue();
            items.sort(Comparator.comparing(InstallmentProjectionItemDTO::projectedDate));

            BigDecimal totalCommitted = items.stream()
                .map(InstallmentProjectionItemDTO::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            response.add(new InstallmentProjectionMonthDTO(
                ym.getYear(),
                ym.getMonthValue(),
                totalCommitted,
                items.size(),
                items
            ));
            countMonths++;
        }

        return response;
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
