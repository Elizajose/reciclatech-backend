package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ValoresTest {

    @Test void rejeitaZeroNegativoNuloENaoNumero() {
        assertNull(Valores.peso(0.0));
        assertNull(Valores.peso(-1.0));
        assertNull(Valores.peso(null));
        assertNull(Valores.peso(Double.NaN));
        assertNull(Valores.peso(Double.POSITIVE_INFINITY));
        assertNull(Valores.preco(0.0));
        assertNull(Valores.preco(-0.5));
        assertNull(Valores.dinheiro(-10.0));
    }

    @Test void rejeitaValorAbsurdo() {
        assertNull(Valores.peso(1_000_001.0));
        assertNull(Valores.preco(100_001.0));
        assertNotNull(Valores.peso(1_000_000.0));
    }

    @Test void aceitaValoresNormaisComCasasLimitadas() {
        assertEquals(0, new BigDecimal("12.5").compareTo(Valores.peso(12.5)));
        assertEquals(new BigDecimal("7.40"), Valores.preco(7.4));
        assertEquals(new BigDecimal("0.01"), Valores.preco(0.0100001));
    }

    @Test void precoQueArredondaParaZeroERejeitado() {
        assertNull(Valores.preco(0.004));
    }

    @Test void lerDecimalComVirgulaOuPonto() {
        assertEquals(0, new BigDecimal("12.5").compareTo(Valores.lerDecimal("12,5")));
        assertEquals(0, new BigDecimal("12.5").compareTo(Valores.lerDecimal(" 12.5 ")));
        assertNull(Valores.lerDecimal("abc"));
        assertNull(Valores.lerDecimal(""));
        assertNull(Valores.lerDecimal(null));
    }

    @Test void textoObrigatorioComLimite() {
        assertEquals("a b", Valores.texto("  a   b ", 10));
        assertNull(Valores.texto("   ", 10));
        assertNull(Valores.texto("abcdef", 5));
        assertNull(Valores.texto(null, 5));
    }

    // ---- Assinatura ----
    private static final LocalDate HOJE = LocalDate.parse("2026-11-14");

    @Test void trialDeSeteDiasAindaValeNoSetimoEVenceNoOitavo() {
        assertFalse(Assinatura.trialVencido("TRIAL", HOJE.minusDays(7), HOJE));
        assertTrue(Assinatura.trialVencido("TRIAL", HOJE.minusDays(8), HOJE));
    }

    @Test void trialSemDataOuOutroStatusNaoVence() {
        assertFalse(Assinatura.trialVencido("TRIAL", null, HOJE));
        assertFalse(Assinatura.trialVencido("ATIVO", HOJE.minusDays(400), HOJE));
    }

    @Test void bloqueadoEmBloqueioSuspensaoOuTrialVencido() {
        assertTrue(Assinatura.bloqueado("BLOQUEADO", null, HOJE));
        assertTrue(Assinatura.bloqueado("SUSPENSO", null, HOJE));
        assertTrue(Assinatura.bloqueado("TRIAL", HOJE.minusDays(30), HOJE));
        assertFalse(Assinatura.bloqueado("TRIAL", HOJE.minusDays(2), HOJE));
        assertFalse(Assinatura.bloqueado("ATIVO", HOJE.minusDays(900), HOJE));
    }

    @Test void paginaPublicaSegueMesmaRegraDoTrial() {
        assertTrue(Assinatura.aceitaPedidosPublicos("ATIVO", null, HOJE));
        assertTrue(Assinatura.aceitaPedidosPublicos("TRIAL", HOJE.minusDays(7), HOJE));
        assertFalse(Assinatura.aceitaPedidosPublicos("TRIAL", HOJE.minusDays(8), HOJE));
        assertFalse(Assinatura.aceitaPedidosPublicos("TRIAL", null, HOJE));
        assertFalse(Assinatura.aceitaPedidosPublicos("BLOQUEADO", HOJE, HOJE));
    }
}
