package org.acme.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InstitutionDetector {

    private static final Map<String, String> BANK_CODES = Map.ofEntries(
        Map.entry("260", "Nubank"),
        Map.entry("0260", "Nubank"),
        Map.entry("77", "Inter"),
        Map.entry("077", "Inter"),
        Map.entry("0077", "Inter"),
        Map.entry("341", "Itaú"),
        Map.entry("0341", "Itaú"),
        Map.entry("1", "Banco do Brasil"),
        Map.entry("001", "Banco do Brasil"),
        Map.entry("0001", "Banco do Brasil"),
        Map.entry("237", "Bradesco"),
        Map.entry("0237", "Bradesco"),
        Map.entry("33", "Santander"),
        Map.entry("033", "Santander"),
        Map.entry("0033", "Santander"),
        Map.entry("104", "Caixa"),
        Map.entry("0104", "Caixa"),
        Map.entry("336", "C6 Bank"),
        Map.entry("0336", "C6 Bank"),
        Map.entry("323", "Mercado Pago"),
        Map.entry("0323", "Mercado Pago"),
        Map.entry("290", "PagBank"),
        Map.entry("0290", "PagBank"),
        Map.entry("380", "PicPay"),
        Map.entry("0380", "PicPay"),
        Map.entry("208", "BTG Pactual"),
        Map.entry("0208", "BTG Pactual")
    );

    public String detectFromOfx(String content, String fileName, String explicitInstitution) {
        if (explicitInstitution != null && !explicitInstitution.isBlank()) {
            return explicitInstitution.trim();
        }

        if (content != null) {
            String bankId = extractTag(content, "BANKID");
            if (bankId != null && BANK_CODES.containsKey(bankId.trim())) {
                return BANK_CODES.get(bankId.trim());
            }

            String fid = extractTag(content, "FID");
            if (fid != null && BANK_CODES.containsKey(fid.trim())) {
                return BANK_CODES.get(fid.trim());
            }

            String org = extractTag(content, "ORG");
            if (org != null && !org.isBlank()) {
                String matchedFromOrg = matchKeywords(org);
                if (matchedFromOrg != null) {
                    return matchedFromOrg;
                }
                return org.trim();
            }

            String matchedFromContent = matchKeywords(content);
            if (matchedFromContent != null) {
                return matchedFromContent;
            }
        }

        return matchKeywords(fileName);
    }

    public String detectFromPdf(List<String> lines, String fileName, String explicitInstitution) {
        if (explicitInstitution != null && !explicitInstitution.isBlank()) {
            return explicitInstitution.trim();
        }

        String fromFileName = matchKeywords(fileName);
        if (fromFileName != null) {
            return fromFileName;
        }

        if (lines != null) {
            String joined = String.join(" ", lines);
            String fromText = matchKeywords(joined);
            if (fromText != null) {
                return fromText;
            }
        }

        return null;
    }

    public String detectFromCsv(List<String> lines, String fileName, String explicitInstitution) {
        if (explicitInstitution != null && !explicitInstitution.isBlank()) {
            return explicitInstitution.trim();
        }

        String fromFileName = matchKeywords(fileName);
        if (fromFileName != null) {
            return fromFileName;
        }

        if (lines != null && !lines.isEmpty()) {
            String firstLine = normalize(lines.get(0));
            // Formato clássico de fatura/extrato CSV do Nubank
            if (firstLine.equals("date,title,amount") || firstLine.equals("date,category,title,amount") || firstLine.contains("identificador")) {
                return "Nubank";
            }

            String sample = String.join(" ", lines.subList(0, Math.min(lines.size(), 30)));
            String fromContent = matchKeywords(sample);
            if (fromContent != null) {
                return fromContent;
            }
        }

        return null;
    }

    public String matchKeywords(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        String norm = normalize(rawText);

        if (norm.contains("nubank") || norm.contains("nu pagamentos") || norm.contains("nupay")) {
            return "Nubank";
        }
        if (norm.contains("banco inter") || norm.matches(".*\\binter\\b.*")) {
            return "Inter";
        }
        if (norm.contains("itau") || norm.contains("uniclass") || norm.contains("personnalite")) {
            return "Itaú";
        }
        if (norm.contains("bradesco") || norm.contains("next")) {
            return "Bradesco";
        }
        if (norm.contains("santander")) {
            return "Santander";
        }
        if (norm.contains("banco do brasil") || norm.contains("ourocard")) {
            return "Banco do Brasil";
        }
        if (norm.contains("caixa economica") || norm.matches(".*\\bcaixa\\b.*")) {
            return "Caixa";
        }
        if (norm.contains("c6 bank") || norm.contains("c6bank") || norm.matches(".*\\bc6\\b.*")) {
            return "C6 Bank";
        }
        if (norm.contains("mercado pago") || norm.contains("mercadopago")) {
            return "Mercado Pago";
        }
        if (norm.contains("picpay")) {
            return "PicPay";
        }
        if (norm.contains("pagbank") || norm.contains("pagseguro")) {
            return "PagBank";
        }
        if (norm.contains("btg")) {
            return "BTG Pactual";
        }
        if (norm.matches(".*\\bxp\\b.*")) {
            return "XP";
        }

        return null;
    }

    private String extractTag(String content, String tagName) {
        Pattern pattern = Pattern.compile("<" + tagName + ">([^<\\r\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                         .replaceAll("\\p{M}", "")
                         .toLowerCase()
                         .trim();
    }
}
