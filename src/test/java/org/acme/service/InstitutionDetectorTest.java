package org.acme.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
public class InstitutionDetectorTest {

    @Inject
    InstitutionDetector institutionDetector;

    @Test
    @DisplayName("Deve detectar instituição no OFX via BANKID, FID, ORG, nome do arquivo ou parâmetro explícito")
    void shouldDetectInstitutionFromOfx() {
        assertEquals("Itaú", institutionDetector.detectFromOfx("<OFX><BANKID>0341</OFX>", "extrato.ofx", null));
        assertEquals("Nubank", institutionDetector.detectFromOfx("<OFX><FID>260</OFX>", "extrato.ofx", null));
        assertEquals("Santander", institutionDetector.detectFromOfx("<OFX><ORG>Banco Santander Brasil</OFX>", "extrato.ofx", null));
        assertEquals("Bradesco", institutionDetector.detectFromOfx("<OFX></OFX>", "extrato_bradesco_out.ofx", null));
        assertEquals("Cooperativa X", institutionDetector.detectFromOfx("<OFX><BANKID>0341</OFX>", "extrato.ofx", "Cooperativa X"));
    }

    @Test
    @DisplayName("Deve detectar instituição no PDF via nome do arquivo, linhas de texto ou parâmetro explícito")
    void shouldDetectInstitutionFromPdf() {
        assertEquals("C6 Bank", institutionDetector.detectFromPdf(List.of("Resumo da Fatura"), "fatura_c6bank_outubro.pdf", null));
        assertEquals("BTG Pactual", institutionDetector.detectFromPdf(List.of("Extrato Mensal", "Banco BTG Pactual S.A."), "documento.pdf", null));
        assertEquals("XP", institutionDetector.detectFromPdf(List.of("Fatura Cartão XP Visa Infinite"), "fatura.pdf", null));
        assertEquals("Nomad", institutionDetector.detectFromPdf(List.of("Banco Inter"), "inter.pdf", "Nomad"));
        assertNull(institutionDetector.detectFromPdf(List.of("Linha qualquer sem banco"), "arquivo.pdf", null));
    }

    @Test
    @DisplayName("Deve detectar instituição no CSV pelo cabeçalho do Nubank, conteúdo, nome do arquivo ou parâmetro explícito")
    void shouldDetectInstitutionFromCsv() {
        assertEquals("Nubank", institutionDetector.detectFromCsv(List.of("date,title,amount", "2026-10-01,Uber,20.00"), "fatura.csv", null));
        assertEquals("Mercado Pago", institutionDetector.detectFromCsv(List.of("Data;Descricao;Valor", "10/10/2026;Mercado Pago Compra;-50,00"), "extrato.csv", null));
        assertEquals("Banco do Brasil", institutionDetector.detectFromCsv(List.of("Data;Historico;Valor"), "extrato_ourocard.csv", null));
        assertEquals("Caixa", institutionDetector.detectFromCsv(List.of("Data;Historico;Valor"), "extrato_caixa_economica.csv", null));
        assertEquals("PicPay", institutionDetector.detectFromCsv(List.of("Data;Historico;Valor"), "picpay_outubro.csv", null));
        assertEquals("PagBank", institutionDetector.detectFromCsv(List.of("Data;Historico;Valor"), "pagseguro_2026.csv", null));
    }
}
