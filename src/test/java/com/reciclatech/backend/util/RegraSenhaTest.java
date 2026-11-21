package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Fase 29: troca de senha do dono. */
class RegraSenhaTest {

    @Test void senhaBoaERepetidaIgualPassa() {
        assertNull(RegraSenha.validarTroca("segura123", "segura123"));
        assertNull(RegraSenha.validarTroca("12345678", null)); // formulário antigo, sem repetição
    }

    @Test void vaziaOuCurtaNaoPassa() {
        assertEquals("Informe a nova senha.", RegraSenha.validarTroca(null, null));
        assertEquals("Informe a nova senha.", RegraSenha.validarTroca("", ""));
        assertEquals("A nova senha deve ter pelo menos 8 caracteres.", RegraSenha.validarTroca("1234567", "1234567"));
    }

    @Test void longaDemaisNaoPassa() {
        String grande = "a".repeat(73);
        assertEquals("A nova senha deve ter no máximo 72 caracteres.", RegraSenha.validarTroca(grande, grande));
        assertNull(RegraSenha.validarTroca("a".repeat(72), "a".repeat(72)));
    }

    @Test void repeticaoDiferenteNaoPassa() {
        assertEquals("A repetição não confere com a nova senha.", RegraSenha.validarTroca("segura123", "segura124"));
    }
}
