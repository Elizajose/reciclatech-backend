package com.reciclatech.backend.util;

import com.reciclatech.backend.service.AnaliseFinanceira;
import com.reciclatech.backend.service.AnaliseFinanceira.DespesaMov;
import com.reciclatech.backend.service.AnaliseFinanceira.Movimento;
import com.reciclatech.backend.service.AnaliseFinanceira.Resultado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExportacaoCsvTest {

    private static final LocalDate HOJE = LocalDate.parse("2026-11-11");

    private Resultado cenario(String fornecedor) {
        List<Movimento> movs = List.of(
                new Movimento(1L, "Latinha", "VENDIDO", new BigDecimal("400"), new BigDecimal("2960"), HOJE, fornecedor),
                new Movimento(2L, "Latinha", "SAIDA_INDUSTRIA", new BigDecimal("300"), new BigDecimal("15000"), HOJE, "IND"));
        return AnaliseFinanceira.calcular(movs, List.of(new DespesaMov(HOJE, new BigDecimal("500"))), HOJE, HOJE);
    }

    @Test
    void usaPadraoBrasileiroComBomEPontoEVirgula() {
        String csv = ExportacaoCsv.resultado(cenario("Joao"));
        assertTrue(csv.startsWith(ExportacaoCsv.BOM));
        assertTrue(csv.contains("Período;11/11/2026;11/11/2026"));
        assertTrue(csv.contains("Vendas;15000,00"));
        assertTrue(csv.contains("Despesas;500,00"));
        assertTrue(csv.contains("\r\n"));
        assertFalse(csv.contains("15000.00"), "decimal deve usar vírgula");
    }

    @Test
    void lucroLiquidoBateComOPainel() {
        // custo médio = 2960/400 = 7,40; vendeu 300 => custo 2.220; lucro = 15.000 - 2.220 - 500 = 12.280
        String csv = ExportacaoCsv.resultado(cenario("Joao"));
        assertTrue(csv.contains("Custo do material vendido;2220,00"));
        assertTrue(csv.contains("Lucro líquido;12280,00"));
    }

    @Test
    void neutralizaFormulaEProtegeSeparadorNosTextos() {
        assertEquals("'=SOMA(A1)", ExportacaoCsv.celula("=SOMA(A1)"));
        assertEquals("'@cmd", ExportacaoCsv.celula("@cmd"));
        assertEquals("\"Silva; Filho\"", ExportacaoCsv.celula("Silva; Filho"));
        assertEquals("\"Ele disse \"\"oi\"\"\"", ExportacaoCsv.celula("Ele disse \"oi\""));
        assertEquals("", ExportacaoCsv.celula(null));
    }

    @Test
    void nomeDeFornecedorMaliciosoNaoViraFormulaNoArquivo() {
        String csv = ExportacaoCsv.resultado(cenario("=HYPERLINK(\"http://x\")"));
        assertFalse(csv.contains(";=HYPERLINK"));
        assertFalse(csv.contains("\r\n=HYPERLINK"));
        assertTrue(csv.contains("'=HYPERLINK"));
    }

    @Test
    void periodoSemMovimentoGeraArquivoValido() {
        Resultado r = AnaliseFinanceira.calcular(List.of(), List.of(), HOJE, HOJE);
        String csv = ExportacaoCsv.resultado(r);
        assertTrue(csv.contains("Lucro líquido;0,00"));
        assertTrue(csv.contains("MAIORES FORNECEDORES"));
    }
}
