package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class EngajamentoTest {
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 14);

    @Test void ativoSemMovimentoHaMaisDeSeteDiasEhOcioso() {
        assertTrue(Engajamento.ocioso("ATIVO", HOJE.minusDays(60), HOJE.minusDays(8), HOJE));
        assertTrue(Engajamento.ocioso("ATIVO", HOJE.minusDays(60), null, HOJE));
    }

    @Test void movimentoRecenteNaoEhOcioso() {
        assertFalse(Engajamento.ocioso("ATIVO", HOJE.minusDays(60), HOJE.minusDays(7), HOJE));
        assertFalse(Engajamento.ocioso("ATIVO", HOJE.minusDays(60), HOJE, HOJE));
    }

    @Test void armazemNovoAindaNaoContaComoOcioso() {
        assertFalse(Engajamento.ocioso("TRIAL", HOJE.minusDays(3), null, HOJE));
        assertFalse(Engajamento.ocioso("ATIVO", HOJE.minusDays(7), null, HOJE));
    }

    @Test void bloqueadoOuTesteVencidoNaoEntraNessaConta() {
        assertFalse(Engajamento.ocioso("BLOQUEADO", HOJE.minusDays(60), null, HOJE));
        assertFalse(Engajamento.ocioso("SUSPENSO", HOJE.minusDays(60), null, HOJE));
        assertFalse(Engajamento.ocioso("TRIAL", HOJE.minusDays(30), null, HOJE)); // teste vencido: já aparece em "fora de operação"
    }
}
