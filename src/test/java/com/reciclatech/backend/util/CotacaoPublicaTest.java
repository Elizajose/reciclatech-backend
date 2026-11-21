package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Fase 27: cotação pública por região, busca pelo nome e ranking por material. */
class CotacaoPublicaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 21);

    private static CotacaoPublica.Preco p(String nome, String un, String preco, Integer diasAtras) {
        return new CotacaoPublica.Preco(nome, un, preco == null ? null : new BigDecimal(preco),
                diasAtras == null ? null : HOJE.minusDays(diasAtras));
    }

    private static CotacaoPublica.ArmazemCot a(long id, String nome, String uf, String cidade, CotacaoPublica.Preco... precos) {
        return new CotacaoPublica.ArmazemCot(id, nome, uf, cidade, List.of(precos));
    }

    private static List<CotacaoPublica.ArmazemCot> base() {
        return List.of(
                a(1, "Zeca Sucata", "PE", "Salgueiro", p("Cobre", "KG", "38.00", 2), p("Ferro", "KG", "0.90", 2), p("Papelão", "KG", "0.50", 10)),
                a(2, "Armazém Boa Vista", "pe", " salgueiro ", p("cobre", "kg", "40.00", 0), p("Alumínio", "KG", "6.50", 0)),
                a(3, "Ângelo Recicla", "PE", "SALGUEIRO", p("COBRE ", "KG", "40.00", 40), p("Latinha", "UN", "0.10", null)),
                a(4, "Recicla Petrolina", "PE", "Petrolina", p("Cobre", "KG", "45.00", 1)),
                a(5, "Galpão Antigo", null, null, p("Cobre", "KG", "30.00", null))
        );
    }

    @Test void chaveIgnoraAcentoCaixaEEspacos() {
        assertEquals("sao jose do egito", CotacaoPublica.chave("  São   José do EGITO "));
        assertEquals("", CotacaoPublica.chave(null));
        assertEquals("cobre|KG", CotacaoPublica.chaveMaterial(" Cobre ", null));
        assertEquals("cobre|UN", CotacaoPublica.chaveMaterial("cobre", "un"));
    }

    @Test void regioesAgrupamCidadeComGrafiasDiferentes() {
        Map<String, Object> r = CotacaoPublica.regioes(base());
        assertEquals(1, r.get("semRegiao"));
        assertEquals(5, r.get("total"));
        List<?> estados = (List<?>) r.get("estados");
        assertEquals(1, estados.size());
        Map<?, ?> pe = (Map<?, ?>) estados.get(0);
        assertEquals("PE", pe.get("uf"));
        List<?> cidades = (List<?>) pe.get("cidades");
        assertEquals(2, cidades.size());
        Map<?, ?> petrolina = (Map<?, ?>) cidades.get(0);
        Map<?, ?> salgueiro = (Map<?, ?>) cidades.get(1);
        assertEquals("petrolina", petrolina.get("chave"));
        assertEquals("salgueiro", salgueiro.get("chave"));
        assertEquals(3, salgueiro.get("armazens"));
        assertEquals("Salgueiro", salgueiro.get("nome")); // empate de grafia: fica a com maiúscula e minúscula
    }

    @Test void armazensDaCidadeEmOrdemAlfabeticaSemAcento() {
        Map<String, Object> r = CotacaoPublica.armazens(base(), "PE", "salgueiro", null, 0, HOJE);
        List<?> itens = (List<?>) r.get("itens");
        assertEquals(3, r.get("total"));
        assertEquals("Ângelo Recicla", ((Map<?, ?>) itens.get(0)).get("nome"));
        assertEquals("Armazém Boa Vista", ((Map<?, ?>) itens.get(1)).get("nome"));
        assertEquals("Zeca Sucata", ((Map<?, ?>) itens.get(2)).get("nome"));
        assertEquals(false, r.get("temMais"));
    }

    @Test void outraCidadeNaoAparece() {
        Map<String, Object> r = CotacaoPublica.armazens(base(), "PE", "Petrolina", null, 0, HOJE);
        assertEquals(1, r.get("total"));
        Map<String, Object> nada = CotacaoPublica.armazens(base(), "BA", "Juazeiro", null, 0, HOJE);
        assertEquals(0, nada.get("total"));
    }

    @Test void semRegiaoPegaSoOsArmazensSemEstadoOuCidade() {
        Map<String, Object> r = CotacaoPublica.armazens(base(), CotacaoPublica.SEM_REGIAO, null, null, 0, HOJE);
        assertEquals(1, r.get("total"));
        assertEquals("Galpão Antigo", ((Map<?, ?>) ((List<?>) r.get("itens")).get(0)).get("nome"));
    }

    @Test void buscaPeloNomeSemAcentoEmQualquerParte() {
        Map<String, Object> r = CotacaoPublica.armazens(base(), "PE", "salgueiro", "angelo", 0, HOJE);
        assertEquals(1, r.get("total"));
        assertEquals(3, r.get("totalRegiao"));
        Map<String, Object> r2 = CotacaoPublica.armazens(base(), "PE", "salgueiro", "RECI", 0, HOJE);
        assertEquals(1, r2.get("total"));
        Map<String, Object> r3 = CotacaoPublica.armazens(base(), "PE", "salgueiro", "xyz", 0, HOJE);
        assertEquals(0, r3.get("total"));
    }

    @Test void materiaisMaisComunsDaCidadeVemPrimeiroNoCartao() {
        Map<String, Object> r = CotacaoPublica.armazens(base(), "PE", "salgueiro", "zeca", 0, HOJE);
        Map<?, ?> zeca = (Map<?, ?>) ((List<?>) r.get("itens")).get(0);
        List<?> mats = (List<?>) zeca.get("materiais");
        // cobre está nos 3 armazéns da cidade: vem primeiro; os outros (1 armazém cada) em ordem alfabética
        assertEquals("Cobre", ((Map<?, ?>) mats.get(0)).get("nome"));
        assertEquals("Ferro", ((Map<?, ?>) mats.get(1)).get("nome"));
        assertEquals("Papelão", ((Map<?, ?>) mats.get(2)).get("nome"));
        assertEquals(2, zeca.get("dias")); // última alteração entre os preços do armazém
    }

    @Test void diasSemHistoricoFicaNulo() {
        Map<String, Object> r = CotacaoPublica.armazens(base(), CotacaoPublica.SEM_REGIAO, null, null, 0, HOJE);
        assertNull(((Map<?, ?>) ((List<?>) r.get("itens")).get(0)).get("dias"));
        assertEquals(0, CotacaoPublica.dias(HOJE, HOJE));
        assertEquals(0, CotacaoPublica.dias(HOJE.plusDays(1), HOJE)); // relógio adiantado não dá número negativo
    }

    @Test void paginacaoDeDozeEmDoze() {
        List<CotacaoPublica.ArmazemCot> muitos = new ArrayList<>();
        for (int i = 1; i <= 30; i++) muitos.add(a(i, String.format("Armazém %02d", i), "PE", "Salgueiro", p("Cobre", "KG", "10", 1)));
        Map<String, Object> p0 = CotacaoPublica.armazens(muitos, "PE", "Salgueiro", null, 0, HOJE);
        Map<String, Object> p2 = CotacaoPublica.armazens(muitos, "PE", "Salgueiro", null, 2, HOJE);
        Map<String, Object> p9 = CotacaoPublica.armazens(muitos, "PE", "Salgueiro", null, 9, HOJE);
        assertEquals(12, ((List<?>) p0.get("itens")).size());
        assertEquals(true, p0.get("temMais"));
        assertEquals(6, ((List<?>) p2.get("itens")).size());
        assertEquals(false, p2.get("temMais"));
        assertEquals("Armazém 25", ((Map<?, ?>) ((List<?>) p2.get("itens")).get(0)).get("nome"));
        assertEquals(0, ((List<?>) p9.get("itens")).size());
    }

    @Test void rankingDoMaiorParaOMenorComEmpateDestacado() {
        Map<String, Object> r = CotacaoPublica.ranking(base(), "PE", "salgueiro", "cobre|KG", HOJE);
        List<?> itens = (List<?>) r.get("itens");
        assertEquals(3, itens.size());
        Map<?, ?> primeiro = (Map<?, ?>) itens.get(0);
        Map<?, ?> segundo = (Map<?, ?>) itens.get(1);
        Map<?, ?> terceiro = (Map<?, ?>) itens.get(2);
        // 40 x 40 x 38: os dois de 40 são "melhor", em ordem alfabética
        assertEquals("Ângelo Recicla", primeiro.get("nome"));
        assertEquals("Armazém Boa Vista", segundo.get("nome"));
        assertEquals(true, primeiro.get("melhor"));
        assertEquals(true, segundo.get("melhor"));
        assertEquals(false, terceiro.get("melhor"));
        assertEquals("Zeca Sucata", terceiro.get("nome"));
        assertEquals(40, primeiro.get("dias"));
        assertEquals("Cobre", ((Map<?, ?>) r.get("material")).get("nome"));
    }

    @Test void rankingNaoMisturaUnidadeNemOutraCidade() {
        Map<String, Object> un = CotacaoPublica.ranking(base(), "PE", "salgueiro", "latinha|UN", HOJE);
        assertEquals(1, ((List<?>) un.get("itens")).size());
        Map<String, Object> kg = CotacaoPublica.ranking(base(), "PE", "salgueiro", "latinha|KG", HOJE);
        assertEquals(0, ((List<?>) kg.get("itens")).size());
        Map<String, Object> pet = CotacaoPublica.ranking(base(), "PE", "petrolina", "cobre|KG", HOJE);
        assertEquals(1, ((List<?>) pet.get("itens")).size());
    }

    @Test void materiaisDaCidadeDoMaisComumParaOMenosComum() {
        Map<String, Object> r = CotacaoPublica.materiais(base(), "PE", "salgueiro");
        List<?> lista = (List<?>) r.get("materiais");
        Map<?, ?> primeiro = (Map<?, ?>) lista.get(0);
        assertEquals("cobre|KG", primeiro.get("chave"));
        assertEquals(3, primeiro.get("armazens"));
        assertEquals(5, lista.size()); // cobre, alumínio, ferro, latinha (UN), papelão
    }

    @Test void precoZeradoOuSemNomeNaoEntra() {
        CotacaoPublica.ArmazemCot x = a(9, "X", "PE", "Salgueiro", p("Cobre", "KG", "0", 1), p(" ", "KG", "5", 1), p("Ferro", "KG", null, 1), p("Vidro", "KG", "0.05", 1));
        assertEquals(1, x.precos().size());
        assertEquals("Vidro", x.precos().get(0).nome());
    }
}
