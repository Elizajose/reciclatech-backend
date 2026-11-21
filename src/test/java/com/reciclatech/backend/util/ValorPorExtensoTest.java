package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Fase 31: valor por extenso no recibo. */
class ValorPorExtensoTest {

    private static String r(String v) { return ValorPorExtenso.reais(new BigDecimal(v)); }

    @Test void valoresComuns() {
        assertEquals("quarenta e dois reais e sessenta centavos", r("42.60"));
        assertEquals("um real", r("1.00"));
        assertEquals("um centavo", r("0.01"));
        assertEquals("zero real", r("0"));
        assertEquals("cem reais", r("100"));
        assertEquals("cento e um reais", r("101"));
        assertEquals("dezenove reais e noventa e nove centavos", r("19.99"));
    }

    @Test void milharesEMilhoes() {
        assertEquals("mil reais", r("1000"));
        assertEquals("mil e cem reais", r("1100"));
        assertEquals("mil duzentos e trinta e quatro reais e cinquenta centavos", r("1234.50"));
        assertEquals("dois mil e quinze reais", r("2015"));
        assertEquals("um milhão de reais", r("1000000"));
        assertEquals("dois milhões e quinhentos mil reais", r("2500000"));
    }

    @Test void arredondaECuidaDoInvalido() {
        assertEquals("dez reais e sete centavos", r("10.065"));
        assertEquals("", ValorPorExtenso.reais(null));
        assertEquals("", r("-5"));
    }
}
