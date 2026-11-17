package com.reciclatech.backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComparacaoArmazensTest {

    private static final LocalDate DE = LocalDate.of(2026, 11, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 11, 15);
    private long seq = 0;

    private AnaliseFinanceira.Movimento mov(String material, String status, String peso, String valor, String data) {
        return new AnaliseFinanceira.Movimento(++seq, material, status, new BigDecimal(peso), new BigDecimal(valor), LocalDate.parse(data), "Zé");
    }

    private ComparacaoArmazens.Entrada entrada(Long id, String nome, List<AnaliseFinanceira.Movimento> m, List<AnaliseFinanceira.DespesaMov> d) {
        return new ComparacaoArmazens.Entrada(id, nome, "Recife - PE", m, d);
    }

    /** Compra 100 kg de latinha a R$5 (500) e vende por R$700: lucro 200. */
    private List<AnaliseFinanceira.Movimento> lucroDe200() {
        List<AnaliseFinanceira.Movimento> l = new ArrayList<>();
        l.add(mov("Latinha", "VENDIDO", "100", "500", "2026-11-02"));
        l.add(mov("Latinha", "SAIDA_INDUSTRIA", "100", "700", "2026-11-05"));
        return l;
    }

    @Test void ordenaPorLucroEMarcaMaiorEMenor() {
        List<AnaliseFinanceira.Movimento> menor = new ArrayList<>();
        menor.add(mov("Latinha", "VENDIDO", "100", "500", "2026-11-02"));
        menor.add(mov("Latinha", "SAIDA_INDUSTRIA", "100", "550", "2026-11-05"));
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(
                List.of(entrada(1L, "A", menor, null), entrada(2L, "B", lucroDe200(), null)), DE, ATE);
        assertEquals("B", r.armazens.get(0).nome);
        assertTrue(r.armazens.get(0).maiorLucro);
        assertEquals("A", r.armazens.get(1).nome);
        assertTrue(r.armazens.get(1).menorLucro);
        assertEquals(0, new BigDecimal("200").compareTo(r.armazens.get(0).fin.lucroLiquido));
        assertEquals(0, new BigDecimal("50").compareTo(r.armazens.get(1).fin.lucroLiquido));
        assertEquals(0, new BigDecimal("250").compareTo(r.lucroTotal));
    }

    @Test void despesasEntramNoLucro() {
        List<AnaliseFinanceira.DespesaMov> desp = List.of(new AnaliseFinanceira.DespesaMov(LocalDate.parse("2026-11-03"), new BigDecimal("80")));
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "A", lucroDe200(), desp)), DE, ATE);
        assertEquals(0, new BigDecimal("120").compareTo(r.armazens.get(0).fin.lucroLiquido));
    }

    @Test void lucrosIguaisNaoMarcamVencedor() {
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(
                List.of(entrada(1L, "A", lucroDe200(), null), entrada(2L, "B", lucroDe200(), null)), DE, ATE);
        assertFalse(r.armazens.get(0).maiorLucro);
        assertFalse(r.armazens.get(1).menorLucro);
    }

    @Test void armazemSemMovimentoNaoEntraNoDestaque() {
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(
                List.of(entrada(1L, "A", lucroDe200(), null), entrada(2L, "Parado", List.of(), null)), DE, ATE);
        assertFalse(r.armazens.get(0).menorLucro);
        assertFalse(r.armazens.stream().filter(x -> x.id == 2L).findFirst().get().temMovimento);
    }

    @Test void somaOsQuilosCompradosPorMaterialEArmazem() {
        List<AnaliseFinanceira.Movimento> a = new ArrayList<>();
        a.add(mov("Latinha", "VENDIDO", "100", "500", "2026-11-02"));
        a.add(mov("Cobre", "VENDIDO", "10", "400", "2026-11-03"));
        a.add(mov("Latinha", "VENDIDO", "50", "250", "2026-11-04"));
        List<AnaliseFinanceira.Movimento> b = new ArrayList<>();
        b.add(mov("Latinha", "VENDIDO", "30", "150", "2026-11-02"));
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "A", a, null), entrada(2L, "B", b, null)), DE, ATE);
        ComparacaoArmazens.MaterialLinha latinha = r.materiais.get(0);
        assertEquals("Latinha", latinha.material);
        assertEquals(0, new BigDecimal("180").compareTo(latinha.kgTotal));
        assertEquals(Long.valueOf(1L), latinha.armazemQueMaisComprou);
        assertEquals(Long.valueOf(2L), latinha.armazemQueMenosComprou);
        ComparacaoArmazens.Linha la = r.armazens.stream().filter(x -> x.id == 1L).findFirst().get();
        assertEquals("Latinha", la.maisComprado);
        assertEquals("Cobre", la.menosComprado);
        assertEquals(0, new BigDecimal("160").compareTo(la.kgComprado));
    }

    @Test void materialComprado_soPorUmArmazemNaoTemMenorComprador() {
        List<AnaliseFinanceira.Movimento> a = List.of(mov("Cobre", "VENDIDO", "10", "400", "2026-11-03"));
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "A", a, null), entrada(2L, "B", List.of(), null)), DE, ATE);
        assertNull(r.materiais.get(0).armazemQueMenosComprou);
        assertEquals(Long.valueOf(1L), r.materiais.get(0).armazemQueMaisComprou);
    }

    @Test void comprasForaDoPeriodoNaoContamNosQuilos() {
        List<AnaliseFinanceira.Movimento> a = List.of(
                mov("Latinha", "VENDIDO", "100", "500", "2026-10-20"),
                mov("Latinha", "VENDIDO", "5", "25", "2026-11-10"));
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "A", a, null)), DE, ATE);
        assertEquals(0, new BigDecimal("5").compareTo(r.armazens.get(0).kgComprado));
        assertEquals(0, new BigDecimal("5").compareTo(r.kgTotal));
    }

    @Test void saidasEAjustesNaoSaoCompra() {
        List<AnaliseFinanceira.Movimento> a = List.of(
                mov("Latinha", "AJUSTE_POSITIVO", "100", "0", "2026-11-02"),
                mov("Latinha", "SAIDA_INDUSTRIA", "50", "300", "2026-11-03"));
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "A", a, null)), DE, ATE);
        assertEquals(0, BigDecimal.ZERO.compareTo(r.armazens.get(0).kgComprado));
        assertTrue(r.materiais.isEmpty());
    }

    @Test void listaVaziaNaoQuebra() {
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(), DE, ATE);
        assertTrue(r.armazens.isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.lucroTotal));
    }

    @Test void entradasNulasSaoAceitas() {
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "A", null, null)), DE, ATE);
        assertEquals(1, r.armazens.size());
        assertFalse(r.armazens.get(0).temMovimento);
    }
}
