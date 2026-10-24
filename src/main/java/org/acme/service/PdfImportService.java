package org.acme.service;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.acme.exception.PdfImportException;
import org.acme.model.Category;
import org.acme.model.Transaction;
import org.acme.rest.dto.CategorizationResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PdfImportService {
    
    @Inject
    CategorizationService categorizationService;

    @Inject
    InstitutionDetector institutionDetector;

    // Detecta datas no formato "dd MMM yyyy" ou "dd/MM/yyyy" (com ou sem ano)
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{1,2}\\s+(?:JAN|FEV|MAR|ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ)(?:\\s+\\d{4})?|\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?)\\b", Pattern.CASE_INSENSITIVE);

    // Detecta valor monetário no final: exige centavos (,xx ou .xx) OU símbolo R$ explícito (evita números de casa como 91 ou anos como 2026)
    private static final Pattern AMOUNT_END_PATTERN = Pattern.compile("([+−\\-]?\\s*(?:R\\$\\s*)?\\d{1,3}(?:[.]\\d{3})*[,.]\\d{2}\\s*[DC]?)$", Pattern.CASE_INSENSITIVE);

    // Detecta parcelas: "01 DE 03", "Parcela 5/12", "01/03" (o negative lookahead impede casar datas como 25/10/2026)
    private static final Pattern INSTALLMENT_PATTERN = Pattern.compile("\\b(?:parcela\\s+)?(\\d{1,2})\\s*(?:/|DE)\\s*(\\d{1,2})(?!/\\d{2,4})\\b", Pattern.CASE_INSENSITIVE);

    // Linhas que não devem ser processadas, como totais, saldos, cabeçalhos, rodapés e conversões cambiais
    private static final List<String> IGNORE_KEYWORDS = List.of(
        "total de entradas", "total de saidas", "total da fatura", "saldo", 
        "data descricao", "valores em r$", "ouvidoria", "pagina", "folha",
        "credito/debito", "valor u$$", "cidade/pais", "total mínimo", "opções para pagamento",
        "limite total", "crédito rotativo", "cet para o período",
        "total final", "total compras", "pontos fatura", "bonificação", "bonificacao",
        "fator conversão", "fator conversao", "valor total desta fatura",
        "conversão:", "conversao:", "obrigado pelo pagamento", "guia de consumo",
        "= usd", "pagamentos", "pagamento recebido", "pagamento em "
    );

    @Transactional
    public List<Transaction> importPdf(FileUpload file) {
        return importPdf(file, null);
    }

    @Transactional
    public List<Transaction> importPdf(FileUpload file, String explicitInstitution) {
        List<Transaction> transactions = new ArrayList<>();
        Category defaultCategory = categorizationService.findOrCreate("Outros");
        int fallbackYear = LocalDate.now().getYear();

        try {
            List<String> lines = extractLinesFromPdf(file.filePath().toFile());
            String detectedInstitution = institutionDetector.detectFromPdf(lines, file.fileName(), explicitInstitution);
            LocalDate currentDate = null;
            boolean inTransactionsSection = false;
            boolean isNubankCreditCard = false;

            for(String rawLine : lines) {
                String line = rawLine.trim();
                if(line.isEmpty()) continue;

                String lower = line.toLowerCase();

                // Gatilho de Início: Liga o parser quando chega na tabela de compras/lançamentos
                if(!inTransactionsSection) {
                    if(lower.contains("transações de") || lower.contains("transacoes de")) {
                        inTransactionsSection = true;
                        isNubankCreditCard = true;
                        continue;
                    } else if((lower.contains("data") && (lower.contains("descri") || lower.contains("lançamento") || lower.contains("lancamento") || lower.contains("histórico") || lower.contains("historico"))) ||
                              lower.contains("credito/debito") ||
                              lower.contains("resgate rdb") || lower.contains("compra no débito") || lower.contains("compra no debito")) {
                        inTransactionsSection = true;
                        continue;
                    } else {
                        // Ainda na capa/dados cadastrais (nome, endereço, juros): pula
                        continue;
                    }
                }

                if(shouldIgnore(line)) continue;

                // 1. Detecta a data na linha, se houver
                Matcher dateMatcher = DATE_PATTERN.matcher(line);
                LocalDate lineDate = null;
                int dateEndIndex = -1;

                if(dateMatcher.find()) {
                    try {
                        lineDate = parseFlexibleDate(dateMatcher.group(1), fallbackYear);
                        dateEndIndex = dateMatcher.end();
                        currentDate = lineDate;
                    } catch (Exception ignored) {}
                }

                // Na fatura de cartão do Nubank, toda transação válida começa com a data na própria linha (ex: "20 SET ...").
                // Linhas sem data na própria linha (como o nome do titular "Leonardo S Lima R$ 2.066,14" ou "Pagamento em 28 SET") são ignoradas.
                if(isNubankCreditCard && (lineDate == null || dateMatcher.start() > 0)) {
                    continue;
                }

                // 2. Detecta o valor monetário estrito no final da linha
                Matcher amountMatcher = AMOUNT_END_PATTERN.matcher(line);
                if(!amountMatcher.find()) {
                    continue; // Se não tiver valor com centavos, ignora
                }

                String rawAmount = amountMatcher.group(1);
                int amountStartIndex = amountMatcher.start();

                // 3. Detecta a descrição da transação
                String middle = (dateEndIndex != -1 && dateEndIndex < amountStartIndex)
                              ? line.substring(dateEndIndex, amountStartIndex).trim()
                              : line.substring(0, amountStartIndex).trim();
                
                // 4. Detecta parcelas (ignorando se for PIX/TED ou se parcela atual > total)
                Integer currentInst = null;
                Integer totalInst = null;
                boolean isTransferOrPix = middle.toUpperCase().contains("PIX") || middle.toUpperCase().contains("TED") || middle.toUpperCase().contains("APLICACAO");
                Matcher instMatcher = INSTALLMENT_PATTERN.matcher(middle);
                if(!isTransferOrPix && instMatcher.find()) {
                    try {
                        int c = Integer.parseInt(instMatcher.group(1));
                        int t = Integer.parseInt(instMatcher.group(2));
                        if(c >= 1 && t > 1 && c <= t) {
                            currentInst = c;
                            totalInst = t;
                            middle = instMatcher.replaceFirst("").trim();
                        }
                    } catch (Exception ignored) {}
                }

                // 5. Limpa a descrição
                String description = cleanDescription(middle);
                if(description.isBlank() || description.equalsIgnoreCase("isento")) continue;

                try {
                    BigDecimal amount = parseAmount(rawAmount);

                    // Ignora lançamentos zerados (ex: "ISENTO 0,00" ou "Guia de Consumo 0,00")
                    if(amount.compareTo(BigDecimal.ZERO) == 0) {
                        continue;
                    }

                    // Em fatura de cartão do Nubank: compras vêm positivas mas são despesas (EXPENSE),
                    // exceto se for um estorno (que contém "estorno" ou sinal de menos Unicode '−')
                    if(isNubankCreditCard) {
                        boolean isRefund = description.toLowerCase().contains("estorno") || line.contains("−");
                        amount = isRefund ? amount.abs() : amount.abs().negate();
                    }

                    LocalDate finalDate = (lineDate != null) ? lineDate : currentDate;

                    if(finalDate != null) {
                        CategorizationResult catResult = categorizationService.categorize(description, amount, defaultCategory);

                        Transaction transaction = new Transaction();
                        transaction.date = finalDate;
                        transaction.description = (currentInst != null && totalInst != null)
                                                ? description + " - Parcela " + currentInst + "/" + totalInst
                                                : description;
                        transaction.amount = amount.abs();
                        transaction.transactionType = catResult.type();
                        transaction.category = catResult.category();
                        transaction.currentInstallment = currentInst;
                        transaction.totalInstallments = totalInst;
                        transaction.isCreditCard = isNubankCreditCard;
                        transaction.institution = (detectedInstitution != null) ? detectedInstitution : (isNubankCreditCard ? "Nubank" : null);

                        transactions.add(transaction);
                    }
                } catch (Exception ignored) {
                    // Ignora linhas atípicas
                }
            }
        } catch (Exception e) {
            throw new PdfImportException("Erro ao processar o arquivo PDF: " + e.getMessage());
        }

        return transactions;
    }

    private List<String> extractLinesFromPdf(File file) throws Exception {
        try(PDDocument doc = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            return Arrays.asList(text.split("\\r?\\n"));
        }
    }

    private boolean shouldIgnore(String line) {
        String lower = line.toLowerCase();
        return IGNORE_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private LocalDate parseFlexibleDate(String raw, int fallbackYear) {
        String text = raw.trim().toUpperCase();
        Map<String, String> months = Map.ofEntries(
            Map.entry("JAN", "01"), Map.entry("FEV", "02"), Map.entry("MAR", "03"),
            Map.entry("ABR", "04"), Map.entry("MAI", "05"), Map.entry("JUN", "06"),
            Map.entry("JUL", "07"), Map.entry("AGO", "08"), Map.entry("SET", "09"),
            Map.entry("OUT", "10"), Map.entry("NOV", "11"), Map.entry("DEZ", "12")
        );

        for(var entry : months.entrySet()) {
            if(text.contains(entry.getKey())) {
                String[] parts = text.split("\\s+");
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(entry.getValue());
                int year = (parts.length >= 3) ? Integer.parseInt(parts[2]) : fallbackYear;
                return LocalDate.of(year, month, day);
            }
        }

        String[] parts = text.split("/");
        int day = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int year = (parts.length >= 3)
                 ? (parts[2].length() == 2 ? 2000 + Integer.parseInt(parts[2]) : Integer.parseInt(parts[2]))
                 : fallbackYear;
        return LocalDate.of(year, month, day);
    }

    private String cleanDescription(String text) {
        return text.replaceAll("^[•*\\s\\d\\-]{4,10}", "")
                   .replace("\"", "") // remove aspas duplas (ex: IOF de "Dramawave" -> IOF de Dramawave)
                   .replaceAll("\\s+(?:BRA|BR|USA|ARG)\\b$", "")
                   .replaceAll("^-\\s*", "")
                   .replaceAll("[−\\s\\-]+$", "") // remove hífens ou sinais de menos que sobrarem no final
                   .replaceAll("\\s{2,}", " ")
                   .trim();
    }

    public BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Valor vazio");
        }

        String upper = raw.toUpperCase().trim();
        boolean isDebit = upper.endsWith("D") || upper.startsWith("-") || upper.startsWith("−");

        // Remove moedas (R$, U$$, US$), letras D/C soltas e sinais
        String clean = upper.replaceAll("[R$U\\s]", "")
                            .replaceAll("[DC+\\-−]", "");

        // Pega apenas dígitos, pontos e vírgulas
        clean = clean.replaceAll("[^0-9,.]", "");

        if (!clean.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Nenhum dígito encontrado em: " + raw);
        }

        // Formato brasileiro: 1.234,56 -> 1234.56
        if (clean.contains(",") && clean.lastIndexOf(",") > clean.lastIndexOf(".")) {
            clean = clean.replace(".", "").replace(",", ".");
        } else if (clean.contains(",") && clean.contains(".")) {
            clean = clean.replace(",", "");
        }

        BigDecimal val = new BigDecimal(clean);
        return isDebit ? val.negate() : val;
    }
}
