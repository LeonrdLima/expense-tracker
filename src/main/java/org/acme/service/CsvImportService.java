package org.acme.service;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.acme.exception.CsvImportException;
import org.acme.model.Category;
import org.acme.model.Transaction;
import org.acme.model.TransactionType;
import org.acme.rest.dto.CategorizationResult;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped 
public class CsvImportService {

    @Inject 
    CategorizationService categorizationService;

    private static final List<String> DATE_KEYWORDS = List.of("data", "dt", "date", "dia");
    private static final List<String> DESCRIPTION_KEYWORDS = List.of("descricao", "historico", "detalhe", "lancamento", "memo", "transacao", "title", "estabelecimento");
    private static final List<String> AMOUNT_KEYWORDS = List.of("valor", "quantia", "amount");
    private static final List<String> CATEGORY_KEYWORDS = List.of("categoria", "category", "tag");
    
    @Transactional 
    public List<Transaction> importCsv(FileUpload file) {
        return importCsv(file, false);
    }

    @Transactional
    public List<Transaction> importCreditCardCsv(FileUpload file) {
        return importCsv(file, true);
    }

    public List<Transaction> importCsv(FileUpload file, boolean isCreditCard) {
        List<Transaction> transactions = new ArrayList<>();

        try(BufferedReader reader = Files.newBufferedReader(file.filePath(), StandardCharsets.UTF_8)) {
            List<String> lines = reader.lines()
                                       .filter(line -> !line.trim().isEmpty())
                                       .toList();

            if(lines.isEmpty()) {
                return Collections.emptyList();
            }

            char delimiter = detectDelimiter(lines.get(0));

            int headerIndex = findHeaderLineIndex(lines, delimiter);
            String[] headers = splitLine(lines.get(headerIndex), delimiter);

            int dateIdx = findColumnIndex(headers, DATE_KEYWORDS);
            int descIdx = findColumnIndex(headers, DESCRIPTION_KEYWORDS);
            int amountIdx = findColumnIndex(headers, AMOUNT_KEYWORDS);
            int categoryIdx = findColumnIndex(headers, CATEGORY_KEYWORDS);

            if(dateIdx == -1 || amountIdx == -1 || descIdx == -1) {
                throw new CsvImportException("Não foi possível identificar as colunas obrigatórias (Data, Descrição, Valor) no CSV");
            }

            Category defaultCategory = categorizationService.findOrCreate("Outros");

            for(int i = headerIndex + 1; i < lines.size(); i++) {
                String[] row = splitLine(lines.get(i), delimiter);
                if(row.length <= Math.max(dateIdx, Math.max(descIdx, amountIdx))) continue;

                try {
                    LocalDate date = parseDate(row[dateIdx]);
                    String description = row[descIdx].replace("\"", "").trim();
                    BigDecimal rawAmount = parseAmount(row[amountIdx]);

                    if(rawAmount.compareTo(BigDecimal.ZERO) == 0 && description.isEmpty()) {
                        continue;
                    }

                    if(isCreditCard && rawAmount.compareTo(BigDecimal.ZERO) > 0) {
                        rawAmount = rawAmount.negate();
                    }

                    Category category = null;
                    TransactionType type = (rawAmount.compareTo(BigDecimal.ZERO) < 0)
                                         ? TransactionType.EXPENSE
                                         : TransactionType.INCOME;
                    
                    if(categoryIdx != -1 && categoryIdx < row.length && !row[categoryIdx].isBlank()) {
                        String rawCategory = row[categoryIdx].replace("\"", "").trim();
                        category = categorizationService.findOrCreate(rawCategory);
                    }

                    if(category == null) {
                        CategorizationResult result = categorizationService.categorize(description, rawAmount, defaultCategory);
                        category = result.category();
                        type = result.type();
                    }

                    Transaction t = new Transaction();
                    t.date = date;
                    t.description = description;
                    t.category = category;
                    t.transactionType = type;
                    t.amount = rawAmount.abs();

                    transactions.add(t);
                } catch(Exception ignored) {

                }
            }
        } catch(Exception e) {
            throw new CsvImportException("Erro ao ler o arquivo CSV: " + e.getMessage());
        }

        return transactions;
    }

    private char detectDelimiter(String line) {
        long commas = line.chars().filter(c -> c == ',').count();
        long semicolons = line.chars().filter(c -> c == ';').count();
        long tabs = line.chars().filter(c -> c == '\t').count();
        if(semicolons >= commas && semicolons >= tabs) return ';';
        if(tabs > commas) return '\t';
        return ',';
    }

    private int findHeaderLineIndex(List<String> lines, char delimiter) {
        for(int i = 0; i < Math.min(lines.size(), 10); i++) {
            String[] cols = splitLine(lines.get(i), delimiter);
            for(String col : cols) {
                String norm = normalize(col);
                if(DATE_KEYWORDS.stream().anyMatch(norm::contains)) {
                    return i;
                }
            }
        }
        return 0;
    }

    private int findColumnIndex(String[] headers, List<String> keywords) {
        for(int i = 0; i < headers.length; i++) {
            String col = normalize(headers[i]);
            for(String kw : keywords) {
                if(col.contains(kw)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String[] splitLine(String line, char delimiter) {
        return line.split(delimiter + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
    }

    private String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                         .replaceAll("\\p{M}", "")
                         .toLowerCase()
                         .trim();
    }

    private LocalDate parseDate(String raw) {
        String cleaned = raw.replaceAll("[^0-9/\\-]", "");
        List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy")
        );
        for(DateTimeFormatter format : formatters) {
            try {
                return LocalDate.parse(cleaned, format);
            } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Formato de data não reconhecido " + raw);
    }

    private BigDecimal parseAmount(String raw) {
        if(raw == null || raw.isBlank()) return BigDecimal.ZERO;
        String cleaned = raw.replaceAll("[^0-9,\\-\\.]", "").trim();
        if(cleaned.isEmpty()) return BigDecimal.ZERO;

        if(cleaned.contains(",") && cleaned.lastIndexOf(",") > cleaned.lastIndexOf(".")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if(cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.replace(",", "");
        }
        return new BigDecimal(cleaned);
    }

    
}
