package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RegrasVendaTest {

    private static void recusa(Runnable r) {
        try { r.run(); } catch (IllegalArgumentException ok) { return; }
        fail("deveria recusar");
    }

    @Test void aceitaPrecoNaTabelaEAteODobro() {
        RegrasVenda.validarLinha("Latinha", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("8.00"), 2.0);
        RegrasVenda.validarLinha("Latinha", new BigDecimal("10"), new BigDecimal("16.00"), new BigDecimal("8.00"), 2.0);
        RegrasVenda.validarLinha("Latinha", new BigDecimal("10"), new BigDecimal("1.00"), new BigDecimal("8.00"), 2.0); // pagar menos é livre
    }

    @Test void recusaAcimaDoDobroDaTabela() {
        recusa(() -> RegrasVenda.validarLinha("Latinha", new BigDecimal("10"), new BigDecimal("16.01"), new BigDecimal("8.00"), 2.0));
    }

    @Test void recusaPesoOuPrecoNuloZeroNegativoOuAbsurdo() {
        recusa(() -> RegrasVenda.validarLinha("L", null, new BigDecimal("8"), new BigDecimal("8"), 2.0));
        recusa(() -> RegrasVenda.validarLinha("L", BigDecimal.ZERO, new BigDecimal("8"), new BigDecimal("8"), 2.0));
        recusa(() -> RegrasVenda.validarLinha("L", new BigDecimal("-1"), new BigDecimal("8"), new BigDecimal("8"), 2.0));
        recusa(() -> RegrasVenda.validarLinha("L", new BigDecimal("1"), BigDecimal.ZERO, new BigDecimal("8"), 2.0));
        recusa(() -> RegrasVenda.validarLinha("L", new BigDecimal("1"), new BigDecimal("-3"), new BigDecimal("8"), 2.0));
        recusa(() -> RegrasVenda.validarLinha("L", new BigDecimal("9999999"), new BigDecimal("8"), new BigDecimal("8"), 2.0));
    }

    @Test void semReferenciaSoValidaPesoEPreco() {
        RegrasVenda.validarLinha("L", new BigDecimal("1"), new BigDecimal("5000"), null, 2.0);
    }

    @Test void totalEPrecoVezesPesoComDuasCasas() {
        assertEquals(new BigDecimal("95.00"), RegrasVenda.total(new BigDecimal("9.50"), new BigDecimal("10")));
        assertEquals(new BigDecimal("3.33"), RegrasVenda.total(new BigDecimal("1.111"), new BigDecimal("3")));
    }

    @Test void variacaoDePrecoGrandeExigeConfirmacao() {
        BigDecimal oito = new BigDecimal("8.00");
        assertTrue(RegrasVenda.variacaoSuspeita(oito, new BigDecimal("800")));   // 8 -> 800
        assertTrue(RegrasVenda.variacaoSuspeita(new BigDecimal("7"), new BigDecimal("700")));
        assertTrue(RegrasVenda.variacaoSuspeita(new BigDecimal("16"), new BigDecimal("160")));
        assertTrue(RegrasVenda.variacaoSuspeita(oito, new BigDecimal("3.99")));  // menos da metade
        assertFalse(RegrasVenda.variacaoSuspeita(oito, new BigDecimal("16.00"))); // exatamente o dobro: normal
        assertFalse(RegrasVenda.variacaoSuspeita(oito, new BigDecimal("4.00")));  // exatamente a metade: normal
        assertFalse(RegrasVenda.variacaoSuspeita(oito, new BigDecimal("12")));    // VIP 8 -> 12
        assertFalse(RegrasVenda.variacaoSuspeita(new BigDecimal("38"), new BigDecimal("70"))); // cobre 38 -> 70
        assertFalse(RegrasVenda.variacaoSuspeita(null, new BigDecimal("800")));   // primeiro preço: sem trava
    }

    @Test void textoDaConfirmacaoMostraOsDoisValores() {
        String t = RegrasVenda.textoVariacao("Latinha", new BigDecimal("8"), new BigDecimal("800"));
        assertTrue(t.contains("R$ 8,00"));
        assertTrue(t.contains("R$ 800,00"));
        assertTrue(t.contains("100,0"));
    }
}
