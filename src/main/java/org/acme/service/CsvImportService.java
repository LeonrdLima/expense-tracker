package org.acme.service;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.acme.exception.CsvImportException;
import org.acme.model.Category;
import org.acme.model.Transaction;
import org.acme.rest.dto.CategorizationResult;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped 
public class CsvImportService {

    @Inject 
    CategorizationService categorizationService;
    
    @Transactional 
    public List<Transaction> importCsv(FileUpload file) {
        List<Transaction> transactions = new ArrayList<>();
        try(BufferedReader reader = Files.newBufferedReader(file.filePath(), StandardCharsets.UTF_8)) {
            String line;
            reader.readLine();

            Category defaultCategory = Category.find("name", "Outros").firstResult();
            
            while((line = reader.readLine()) != null) {
                if(line.trim().isEmpty()) continue;                
                transactions.add(parseTransaction(line, defaultCategory));
            }

        } catch(Exception e) {
            throw new CsvImportException("Erro ao ler o arquivo CSV: " + e.getMessage());
        }

        Transaction.persist(transactions);
        return transactions;
    }

    public Transaction parseTransaction(String line, Category defaultCategory) {
        String[] fields = line.split(",");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate date = LocalDate.parse(fields[0].trim(), formatter);
        BigDecimal rawAmount = new BigDecimal(fields[1].trim());
        String description = fields[3].trim();

        CategorizationResult result = categorizationService.categorize(description, rawAmount, defaultCategory);

        Transaction t = new Transaction();
        t.date = date;
        t.description = description;
        t.category = result.category();
        t.transactionType = result.type();
        t.amount = rawAmount.abs();

        return t;
    }
}
