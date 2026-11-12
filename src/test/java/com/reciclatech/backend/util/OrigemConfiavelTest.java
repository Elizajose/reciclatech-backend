package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OrigemConfiavelTest {

    private final Set<String> hosts = Set.of("coletae.com", "localhost");

    @Test void aceitaMesmaOrigem() { assertTrue(OrigemConfiavel.permitida("https://coletae.com", null, hosts)); }
    @Test void aceitaLocalhostComPorta() { assertTrue(OrigemConfiavel.permitida("http://localhost:8080", null, hosts)); }
    @Test void recusaOutraOrigem() { assertFalse(OrigemConfiavel.permitida("https://site-malicioso.com", null, hosts)); }
    @Test void recusaDominioComSufixoParecido() { assertFalse(OrigemConfiavel.permitida("https://coletae.com.evil.com", null, hosts)); }
    @Test void usaRefererQuandoNaoHaOrigin() {
        assertTrue(OrigemConfiavel.permitida(null, "https://coletae.com/admin/precos", hosts));
        assertFalse(OrigemConfiavel.permitida(null, "https://evil.com/x", hosts));
    }
    @Test void semCabecalhosAceita() { assertTrue(OrigemConfiavel.permitida(null, null, hosts)); }
    @Test void originNullSemRefererAceita() { assertTrue(OrigemConfiavel.permitida("null", null, hosts)); }
    @Test void originNullComRefererRuimRecusa() { assertFalse(OrigemConfiavel.permitida("null", "https://evil.com", hosts)); }
    @Test void originInvalidaRecusa() { assertFalse(OrigemConfiavel.permitida("lixo ::", null, hosts)); }
    @Test void hostSemPortaTiraPortaELista() {
        assertEquals("localhost", OrigemConfiavel.hostSemPorta("localhost:8080"));
        assertEquals("a.com", OrigemConfiavel.hostSemPorta("a.com, b.com"));
        assertNull(OrigemConfiavel.hostSemPorta(null));
    }
}
