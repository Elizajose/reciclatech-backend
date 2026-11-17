package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PeriodoComparacaoTest {
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 15);

    @Test void padraoEOMesAtual() {
        PeriodoComparacao p = PeriodoComparacao.resolver(null, null, null, HOJE);
        assertEquals("mes", p.selecionado);
        assertEquals(LocalDate.of(2026, 11, 1), p.de);
        assertEquals(HOJE, p.ate);
    }

    @Test void atalhos() {
        assertEquals(HOJE, PeriodoComparacao.resolver("hoje", null, null, HOJE).de);
        assertEquals(LocalDate.of(2026, 11, 9), PeriodoComparacao.resolver("7d", null, null, HOJE).de);
        assertEquals(LocalDate.of(2026, 10, 17), PeriodoComparacao.resolver("30d", null, null, HOJE).de);
    }

    @Test void datasPersonalizadas() {
        PeriodoComparacao p = PeriodoComparacao.resolver("custom", "2026-10-01", "2026-10-31", HOJE);
        assertEquals("custom", p.selecionado);
        assertEquals(LocalDate.of(2026, 10, 1), p.de);
        assertEquals(LocalDate.of(2026, 10, 31), p.ate);
    }

    @Test void datasInvalidasCaemNoMes() {
        PeriodoComparacao p = PeriodoComparacao.resolver("custom", "abc", "2026-10-31", HOJE);
        assertEquals("mes", p.selecionado);
        assertEquals(LocalDate.of(2026, 11, 1), p.de);
    }

    @Test void futuroEInversaoSaoCorrigidos() {
        PeriodoComparacao p = PeriodoComparacao.resolver("custom", "2026-12-01", "2027-01-01", HOJE);
        assertEquals(HOJE, p.ate);
        assertFalse(p.de.isAfter(p.ate));
        PeriodoComparacao q = PeriodoComparacao.resolver("custom", "2026-11-10", "2026-11-02", HOJE);
        assertFalse(q.de.isAfter(q.ate));
    }

    @Test void periodoMuitoLongoELimitado() {
        PeriodoComparacao p = PeriodoComparacao.resolver("custom", "2000-01-01", "2026-11-15", HOJE);
        assertEquals(366, java.time.temporal.ChronoUnit.DAYS.between(p.de, p.ate));
    }

    @Test void textoDesconhecidoVoltaParaOMes() {
        assertEquals("mes", PeriodoComparacao.resolver("xyz", null, null, HOJE).selecionado);
    }
}
