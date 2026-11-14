package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CodigoArmazemTest {

    @Test
    void geraSeisCaracteresSemLetrasQueSeConfundem() {
        for (int i = 0; i < 2000; i++) {
            String c = CodigoArmazem.gerar();
            assertEquals(CodigoArmazem.TAMANHO, c.length());
            for (char ch : c.toCharArray()) {
                assertTrue(CodigoArmazem.ALFABETO.indexOf(ch) >= 0, "caractere fora do alfabeto: " + ch);
            }
            assertFalse(c.matches(".*[01OIL].*"), "não pode ter 0, 1, O, I ou L: " + c);
        }
    }

    @Test
    void codigosSaoVariados() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 1000; i++) vistos.add(CodigoArmazem.gerar());
        assertTrue(vistos.size() > 990, "muitas repetições: " + vistos.size());
    }

    @Test
    void normalizaComoOOperadorDigita() {
        assertEquals("K7M2QX", CodigoArmazem.normalizar("k7m-2qx"));
        assertEquals("K7M2QX", CodigoArmazem.normalizar("  K7M 2QX "));
        assertEquals("K7M2QX", CodigoArmazem.normalizar("K7M2QX"));
        assertEquals("", CodigoArmazem.normalizar(null));
        assertEquals("", CodigoArmazem.normalizar(" - "));
    }

    @Test
    void codigoComTextoMaliciosoViraSoLetrasENumeros() {
        assertEquals("ABC", CodigoArmazem.normalizar("A'B\" ;C--"));
    }
}
