package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailValidoTest {

    @Test void aceitaEnderecosComuns() {
        assertTrue(EmailValido.formatoValido("contato@gmail.com"));
        assertTrue(EmailValido.formatoValido("  joao.silva+loja@empresa.com.br "));
    }

    @Test void recusaFormatoErrado() {
        assertFalse(EmailValido.formatoValido(null));
        assertFalse(EmailValido.formatoValido(""));
        assertFalse(EmailValido.formatoValido("semarroba.com"));
        assertFalse(EmailValido.formatoValido("a@b"));
        assertFalse(EmailValido.formatoValido("a b@gmail.com"));
    }

    @Test void recusaQuebraDeLinhaParaNaoPermitirInjecaoDeCabecalho() {
        assertFalse(EmailValido.formatoValido("a@gmail.com\nBcc: outro@x.com"));
        assertFalse(EmailValido.formatoValido("a@gmail.com\r\nBcc: x@y.com"));
    }
}
