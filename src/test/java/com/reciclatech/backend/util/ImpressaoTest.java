package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImpressaoTest {
    @Test void mesmaSenhaMesmaImpressao() {
        assertEquals(Impressao.de("$2a$10$abc"), Impressao.de("$2a$10$abc"));
    }
    @Test void senhaTrocadaMudaAImpressao() {
        assertFalse(Impressao.de("$2a$10$abc").equals(Impressao.de("$2a$10$abd")));
    }
    @Test void naoGuardaOHashEmSi() {
        String fp = Impressao.de("$2a$10$abcdefghijk");
        assertEquals(16, fp.length());
        assertFalse(fp.contains("abcdefghijk"));
    }
    @Test void nuloViraVazio() { assertEquals("", Impressao.de(null)); }
}
