package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Fase 30: SQL que atualiza a trava CHECK de colunas enum (testado também num PostgreSQL 16 real). */
class RestricaoEnumTest {

    @Test void sqlTemTodosOsValoresENomeDaTrava() {
        String sql = RestricaoEnum.sqlAtualizar("oferta", "status", List.of("DISPONIVEL", "CANCELADO"));
        assertTrue(sql.contains("NOT LIKE '%''CANCELADO''%'"));
        assertTrue(sql.contains("ADD CONSTRAINT oferta_status_check CHECK (status IS NULL OR status IN (''DISPONIVEL'', ''CANCELADO''))"));
        assertTrue(sql.startsWith("DO $$"));
    }

    @Test void recusaNomesOuValoresEstranhos() {
        assertThrows(IllegalArgumentException.class, () -> RestricaoEnum.sqlAtualizar("oferta; drop", "status", List.of("A")));
        assertThrows(IllegalArgumentException.class, () -> RestricaoEnum.sqlAtualizar("oferta", "status", List.of("A'B")));
        assertThrows(IllegalArgumentException.class, () -> RestricaoEnum.sqlAtualizar("oferta", "status", List.of()));
    }
}
