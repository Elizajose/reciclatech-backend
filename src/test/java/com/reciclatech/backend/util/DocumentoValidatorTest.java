package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentoValidatorTest {

    @Test void cpfValidoComESemMascara() {
        assertTrue(DocumentoValidator.valido("529.982.247-25"));
        assertTrue(DocumentoValidator.valido("52998224725"));
    }
    @Test void cpfComDigitoErradoRecusa() { assertFalse(DocumentoValidator.valido("529.982.247-26")); }
    @Test void cpfComTodosDigitosIguaisRecusa() { assertFalse(DocumentoValidator.valido("111.111.111-11")); }
    @Test void cnpjValido() { assertTrue(DocumentoValidator.valido("11.222.333/0001-81")); }
    @Test void nuloEVazioRecusam() {
        assertFalse(DocumentoValidator.valido(null));
        assertFalse(DocumentoValidator.valido(""));
        assertFalse(DocumentoValidator.valido("123"));
    }

    @Test void formataCpfECnpjEmUmUnicoPadrao() {
        assertEquals("529.982.247-25", DocumentoValidator.formatar("52998224725"));
        assertEquals("529.982.247-25", DocumentoValidator.formatar(" 529.982.247-25 "));
        assertEquals("11.222.333/0001-81", DocumentoValidator.formatar("11222333000181"));
        assertEquals("abc", DocumentoValidator.formatar(" abc "));
        assertEquals("", DocumentoValidator.formatar(null));
    }

    @Test void normalizarIgnoraPontuacaoEMaiusculas() {
        assertEquals("11222333000181", DocumentoValidator.normalizar("11.222.333/0001-81"));
        assertEquals("AB12", DocumentoValidator.normalizar("ab.12"));
    }
}
