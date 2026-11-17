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
        assertTrue(r.materiais.stream().noneMatch(m -> m.kgTotal.signum() > 0));   // nenhuma COMPRA
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

    private boolean temDestaque(ComparacaoArmazens.Resultado r, String tipo, String trecho) {
        return r.destaques.stream().anyMatch(d -> d.tipo.equals(tipo) && d.texto.contains(trecho));
    }

    @Test void compraSemVendaNaoEhMenorLucroEViraAvisoDeEstoque() {
        List<AnaliseFinanceira.Movimento> verde = List.of(mov("Ferro", "VENDIDO", "100", "400", "2026-11-02"));
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(
                List.of(entrada(1L, "Norte", lucroDe200(), null), entrada(2L, "Verde", verde, null)), DE, ATE);
        ComparacaoArmazens.Linha v = r.armazens.stream().filter(x -> x.id == 2L).findFirst().get();
        assertEquals("SEM_VENDAS", v.situacao);
        assertFalse(v.menorLucro);
        assertFalse(r.armazens.stream().anyMatch(x -> x.maiorLucro));      // só um armazém vendeu: sem disputa
        assertEquals(0, new BigDecimal("400").compareTo(v.estoqueCusto));
        assertEquals(0, new BigDecimal("100").compareTo(v.estoqueKg));
        assertEquals(0, new BigDecimal("-400").compareTo(v.fin.caixaPeriodo));
        assertTrue(temDestaque(r, "aviso", "Verde comprou"));
    }

    @Test void vendaAbaixoDoCustoViraPrejuizoEAlerta() {
        List<AnaliseFinanceira.Movimento> a = new ArrayList<>();
        a.add(mov("Latinha", "VENDIDO", "100", "500", "2026-11-02"));
        a.add(mov("Latinha", "SAIDA_INDUSTRIA", "100", "300", "2026-11-05"));
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "A", a, null), entrada(2L, "B", lucroDe200(), null)), DE, ATE);
        ComparacaoArmazens.Linha la = r.armazens.stream().filter(x -> x.id == 1L).findFirst().get();
        assertEquals("PREJUIZO", la.situacao);
        assertTrue(temDestaque(r, "perigo", "A teve prejuízo de R$ 200,00"));
        assertTrue(temDestaque(r, "perigo", "abaixo do custo médio"));
    }

    @Test void precoMedioDeCompraEVendaPorArmazem() {
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "A", lucroDe200(), null)), DE, ATE);
        ComparacaoArmazens.Linha l = r.armazens.get(0);
        assertEquals(0, new BigDecimal("5.00").compareTo(l.precoMedioCompra));
        assertEquals(0, new BigDecimal("7.00").compareTo(l.precoMedioVenda));
        assertEquals("Latinha", l.materialMaisLucrativo);
        assertEquals(0, new BigDecimal("200").compareTo(r.materiais.get(0).lucroTotal));
        assertEquals(0, new BigDecimal("5.00").compareTo(r.materiais.get(0).precoMedioCompra.get(1L)));
    }

    @Test void mesmoMaterialComNomeEmCaixaDiferenteSomaJunto() {
        List<AnaliseFinanceira.Movimento> a = List.of(mov("Latinhas", "VENDIDO", "10", "50", "2026-11-02"));
        List<AnaliseFinanceira.Movimento> b = List.of(mov("latinhas ", "VENDIDO", "20", "100", "2026-11-02"));
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "A", a, null), entrada(2L, "B", b, null)), DE, ATE);
        assertEquals(1, r.materiais.size());
        assertEquals(0, new BigDecimal("30").compareTo(r.materiais.get(0).kgTotal));
    }

    @Test void precoPagoMuitoDiferenteEntreArmazensViraDestaque() {
        List<AnaliseFinanceira.Movimento> a = List.of(mov("Cobre", "VENDIDO", "10", "500", "2026-11-02"));   // 50/kg
        List<AnaliseFinanceira.Movimento> b = List.of(mov("Cobre", "VENDIDO", "10", "400", "2026-11-03"));   // 40/kg
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "Norte", a, null), entrada(2L, "Verde", b, null)), DE, ATE);
        assertTrue(temDestaque(r, "info", "Cobre: Norte paga R$ 50,00/kg e Verde paga R$ 40,00/kg (20% a menos)"));
    }

    @Test void precoPagoParecidoNaoGeraDestaque() {
        List<AnaliseFinanceira.Movimento> a = List.of(mov("Cobre", "VENDIDO", "10", "500", "2026-11-02"));
        List<AnaliseFinanceira.Movimento> b = List.of(mov("Cobre", "VENDIDO", "10", "480", "2026-11-03"));
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "Norte", a, null), entrada(2L, "Verde", b, null)), DE, ATE);
        assertFalse(r.destaques.stream().anyMatch(d -> d.texto.contains("paga R$")));
    }

    @Test void quemSustentaOLucroEDespesasPesadas() {
        List<AnaliseFinanceira.DespesaMov> desp = List.of(new AnaliseFinanceira.DespesaMov(LocalDate.parse("2026-11-03"), new BigDecimal("20")));
        List<AnaliseFinanceira.Movimento> pequeno = new ArrayList<>();
        pequeno.add(mov("Latinha", "VENDIDO", "10", "50", "2026-11-02"));
        pequeno.add(mov("Latinha", "SAIDA_INDUSTRIA", "10", "60", "2026-11-05"));
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(
                List.of(entrada(1L, "Grande", lucroDe200(), null), entrada(2L, "Pequeno", pequeno, desp)), DE, ATE);
        assertTrue(temDestaque(r, "info", "Grande gerou 100%"));
        assertTrue(temDestaque(r, "aviso", "despesas de Pequeno consomem"));
    }

    @Test void armazemSemMovimentoGeraAvisoInformativo() {
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "A", lucroDe200(), null), entrada(2L, "Parado", List.of(), null)), DE, ATE);
        assertTrue(temDestaque(r, "info", "Parado não teve nenhuma movimentação"));
    }

    @Test void barrasSaoRelativasAoMaior() {
        List<AnaliseFinanceira.Movimento> metade = new ArrayList<>();
        metade.add(mov("Latinha", "VENDIDO", "100", "500", "2026-11-02"));
        metade.add(mov("Latinha", "SAIDA_INDUSTRIA", "100", "600", "2026-11-05"));   // lucro 100
        ComparacaoArmazens.Resultado r = ComparacaoArmazens.comparar(List.of(entrada(1L, "A", lucroDe200(), null), entrada(2L, "B", metade, null)), DE, ATE);
        assertEquals(100, r.armazens.get(0).pctLucro);
        assertEquals(50, r.armazens.get(1).pctLucro);
        assertEquals(100, r.armazens.get(0).pctCompras);
    }
}
