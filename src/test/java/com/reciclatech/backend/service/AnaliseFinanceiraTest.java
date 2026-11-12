package com.reciclatech.backend.service;

import com.reciclatech.backend.service.AnaliseFinanceira.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AnaliseFinanceiraTest {

    private static final LocalDate HOJE = LocalDate.parse("2026-11-11");
    private long seq = 0;

    private Movimento mov(String material, String status, String peso, String valor, String data, String parceiro) {
        return new Movimento(++seq, material, status, new BigDecimal(peso), new BigDecimal(valor), LocalDate.parse(data), parceiro);
    }

    private static void assertValor(String esperado, BigDecimal real) {
        assertNotNull(real);
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " mas veio " + real);
    }

    /** Cenário do teste do dono: compra 400 kg de latinha e 40 kg de cobre, vende 400 e 30, despesa de 500. */
    private List<Movimento> cenarioDoDono() {
        return new ArrayList<>(List.of(
                mov("Latinha", "VENDIDO", "400", "2960", "2026-11-11", "Joao"),
                mov("Cobre", "VENDIDO", "40", "1520", "2026-11-11", "Joao"),
                mov("Latinha", "SAIDA_INDUSTRIA", "400", "20000", "2026-11-11", "IND"),
                mov("Cobre", "SAIDA_INDUSTRIA", "30", "1500", "2026-11-11", "IND")));
    }

    @Test
    void lucroUsaCustoMedioEDiferenteDoCaixa() {
        Resultado r = AnaliseFinanceira.calcular(cenarioDoDono(), List.of(new DespesaMov(HOJE, new BigDecimal("500"))), HOJE, HOJE);
        assertValor("21500", r.receita);
        assertValor("4100", r.custoVendido);       // 2.960 da latinha + 30 kg x 38 do cobre
        assertValor("16900", r.lucroLiquido);      // o cobre que sobrou (10 kg) não é prejuízo
        assertValor("4480", r.compras);
        assertValor("16520", r.caixaPeriodo);      // mesmo número que o painel de caixa já mostra
        assertValor("78.6", r.margemPercentual);
        assertEquals(1, r.atendimentos);
    }

    @Test
    void estoqueRestanteEValorizadoACustoEARevenda() {
        Resultado r = AnaliseFinanceira.calcular(cenarioDoDono(), List.of(), HOJE, HOJE);
        assertEquals(1, r.estoque.size());
        assertEquals("Cobre", r.estoque.get(0).material);
        assertValor("380", r.estoqueValorCusto);
        assertValor("500", r.estoqueValorRevenda);
    }

    @Test
    void margemPorMaterial() {
        Resultado r = AnaliseFinanceira.calcular(cenarioDoDono(), List.of(), HOJE, HOJE);
        assertEquals("Latinha", r.porMaterial.get(0).material);
        assertValor("17040", r.porMaterial.get(0).lucro);
        LinhaMaterial cobre = r.porMaterial.get(1);
        assertValor("360", cobre.lucro);
        assertValor("24.0", cobre.margemPercentual);
        assertValor("38", cobre.custoMedio);
        assertValor("50", cobre.precoMedioVenda);
    }

    @Test
    void perdaEValorizadaACustoMedio() {
        List<Movimento> ms = cenarioDoDono();
        ms.add(mov("Cobre", "AJUSTE_NEGATIVO", "5", "0", "2026-11-11", ""));
        Resultado r = AnaliseFinanceira.calcular(ms, List.of(), HOJE, HOJE);
        assertValor("190", r.perdas);
        assertValor("17210", r.lucroLiquido);
        assertValor("190", r.estoqueValorCusto);
    }

    @Test
    void vendaNoPeriodoUsaCustoDeCompraAnterior() {
        Resultado r = AnaliseFinanceira.calcular(List.of(
                mov("Ferro", "VENDIDO", "100", "100", "2026-11-01", "Ana"),
                mov("Ferro", "SAIDA_INDUSTRIA", "100", "180", "2026-11-11", "IND")), List.of(), HOJE, HOJE);
        assertValor("80", r.lucroLiquido);
        assertValor("0", r.compras);
    }

    @Test
    void vendaAcimaDoEstoqueSoCustaOQueExiste() {
        Resultado r = AnaliseFinanceira.calcular(List.of(
                mov("Ferro", "VENDIDO", "10", "10", "2026-11-11", "A"),
                mov("Ferro", "SAIDA_INDUSTRIA", "20", "50", "2026-11-11", "I")), List.of(), HOJE, HOJE);
        assertValor("10", r.custoVendido);
        assertValor("40", r.lucroLiquido);
        assertTrue(r.estoque.isEmpty());
    }

    @Test
    void periodoSemMovimentoNaoQuebra() {
        Resultado r = AnaliseFinanceira.calcular(List.of(), List.of(), HOJE, HOJE);
        assertFalse(r.temMovimento);
        assertNull(r.margemPercentual);
        assertNull(r.ticketMedio);
        assertValor("0", r.lucroLiquido);
    }

    @Test
    void comparaComPeriodoAnterior() {
        Resultado r = AnaliseFinanceira.calcular(List.of(
                mov("Ferro", "VENDIDO", "100", "100", "2026-11-09", "A"),
                mov("Ferro", "SAIDA_INDUSTRIA", "100", "180", "2026-11-10", "I"),
                mov("Ferro", "VENDIDO", "100", "100", "2026-11-10", "A"),
                mov("Ferro", "SAIDA_INDUSTRIA", "100", "260", "2026-11-11", "I")), List.of(), HOJE, HOJE);
        assertValor("80", r.lucroPeriodoAnterior);
        assertValor("160", r.lucroLiquido);
        assertValor("100.0", r.variacaoLucroPercentual);
    }

    @Test
    void statusSemDinheiroEDatasFuturasSaoIgnorados() {
        Resultado r = AnaliseFinanceira.calcular(List.of(
                mov("Solicitação de Coleta", "FINALIZADO", "0", "0", "2026-11-11", "A"),
                mov("Solicitação de Coleta", "CANCELADO", "0", "0", "2026-11-11", "A"),
                mov("Ferro", "VENDIDO", "1", "1", "2026-12-01", "A")), List.of(), HOJE, HOJE);
        assertFalse(r.temMovimento);
        assertValor("0", r.compras);
    }
}
