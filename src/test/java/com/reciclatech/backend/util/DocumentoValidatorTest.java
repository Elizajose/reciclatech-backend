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
}
