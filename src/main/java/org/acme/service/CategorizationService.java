package org.acme.service;

import java.math.BigDecimal;
import java.util.List;

import org.acme.model.Category;
import org.acme.model.CategoryPattern;
import org.acme.model.Transaction;
import org.acme.model.TransactionType;
import org.acme.rest.dto.CategorizationResult;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped 
public class CategorizationService {
    public CategorizationResult categorize(String description, BigDecimal rawAmount, Category defaultCategory) {
        String cleanDesc = cleanForMatching(description);

        TransactionType defaultType = (rawAmount.compareTo(BigDecimal.ZERO) < 0) 
            ? TransactionType.EXPENSE 
            : TransactionType.INCOME;
        
        // 1. Busca os padrões aprendidos no banco
        List<CategoryPattern> patterns = CategoryPattern.listAll();
        for(CategoryPattern p : patterns) {
            if(cleanDesc.contains(p.pattern)) {
                return new CategorizationResult(p.category, defaultType);
            }
        }

        // 2. Se não encontrou nenhum padrão, aplica regra default
        return new CategorizationResult(defaultCategory, defaultType);
    }

    public String cleanForMatching(String description) {
        if(description == null) return "";
        return description.replaceAll("(?i)\\s*-\\s*parcela\\s+\\d+/\\d+$", "")
                          .toLowerCase()
                          .trim();
    }

    @Transactional 
    public void learn(String rawDescription, Category category) {
        String pattern = cleanForMatching(rawDescription);
        if(pattern.isBlank()) return;

        CategoryPattern catpattern = CategoryPattern.findByPattern(pattern);
        if(catpattern == null) {
            catpattern = new CategoryPattern();
            catpattern.pattern = pattern;
            catpattern.category = category;
            catpattern.persist();
        } else {
            catpattern.category = category;
        }
        Category defaultCategory = findOrCreate("Outros");
        if(defaultCategory != null && !defaultCategory.id.equals(category.id)) {
            List<Transaction> others = Transaction.list("category = ?1", defaultCategory);
            for(Transaction t : others) {
                if(cleanForMatching(t.description).contains(pattern)) {
                    t.category = category;
                }
            }
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
}
