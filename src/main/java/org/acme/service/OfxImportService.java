package org.acme.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.acme.exception.OfxImportException;
import org.acme.model.Category;
import org.acme.model.Transaction;
import org.acme.rest.dto.CategorizationResult;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped 
public class OfxImportService {
    
    @Inject 
    CategorizationService categorizationService;

    // Captura cada bloco <STMTTRN>
    private static final Pattern STMTTRN_BLOCK = Pattern.compile("<STMTTRN>(.*?)</STMTTRN>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTALLMENT_PATTERN = Pattern.compile("\\b(?:parcela\\s+)?(\\d{1,2})\\s*(?:/|DE)\\s*(\\d{1,2})(?!/\\d{2,4})\\b", Pattern.CASE_INSENSITIVE);

    @Transactional
    public List<Transaction> importOfx(FileUpload file) {
        List<Transaction> transactions = new ArrayList<>();
        Category defaultCategory = categorizationService.findOrCreate("Outros");

        try {
            String content = Files.readString(file.filePath(), StandardCharsets.UTF_8);
            Matcher blockMatcher = STMTTRN_BLOCK.matcher(content);

            while(blockMatcher.find()) {
                String block = blockMatcher.group(1);

                String rawDate = extractTagValue(block, "DTPOSTED");
                String rawAmount = extractTagValue(block, "TRNAMT");
                String memo = extractTagValue(block, "MEMO");
                String name = extractTagValue(block, "NAME");

                String description = (memo != null && !memo.isBlank()) ? memo : name;
                if(rawDate == null || rawAmount == null || description == null || description.isBlank()) {
                    continue;
                }
                description = description.replace("\"", "");

                LocalDate date = LocalDate.parse(rawDate.trim().substring(0, 8), DateTimeFormatter.ofPattern("yyyyMMdd"));

                BigDecimal amount = new BigDecimal(rawAmount.trim().replace(",", "."));

                Integer currentInstallment = null;
                Integer totalInstallments = null;
                Matcher installmentMatcher = INSTALLMENT_PATTERN.matcher(description);
                if(installmentMatcher.find()) {
                    currentInstallment = Integer.parseInt(installmentMatcher.group(1));
                    totalInstallments = Integer.parseInt(installmentMatcher.group(2));
                    description = installmentMatcher.replaceFirst("").replaceAll("[\\-\\s]+$", "").trim();
                }

                CategorizationResult categorizationResult = categorizationService.categorize(description, amount, defaultCategory);

                Transaction transaction = new Transaction();
                transaction.date = date;
                transaction.description = (currentInstallment != null && totalInstallments != null)
                                        ? description + " - Parcela " + currentInstallment + "/" + totalInstallments
                                        : description.trim();
                transaction.amount = amount.abs();
                transaction.transactionType = categorizationResult.type();
                transaction.category = categorizationResult.category();
                transaction.currentInstallment = currentInstallment;
                transaction.totalInstallments = totalInstallments;
                transactions.add(transaction);
            }
        } catch (Exception e) {
            throw new OfxImportException("Erro ao importar arquivo OFX: " + e.getMessage());
        }

        return transactions;
    }

    private String extractTagValue(String block, String tagName) {
        Pattern pattern = Pattern.compile("<" + tagName + ">([^<\\r\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(block);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}
