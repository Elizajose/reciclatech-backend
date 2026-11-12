package com.reciclatech.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LimitadorPublicoTest {

    @Test void permiteAteOLimiteEBloqueiaDepois() {
        LimitadorPublico l = new LimitadorPublico();
        for (int i = 0; i < 3; i++) assertTrue(l.permitir("ip:1", 3, 60_000));
        assertFalse(l.permitir("ip:1", 3, 60_000));
    }

    @Test void chavesDiferentesNaoSeAfetam() {
        LimitadorPublico l = new LimitadorPublico();
        for (int i = 0; i < 3; i++) l.permitir("ip:1", 3, 60_000);
        assertTrue(l.permitir("ip:2", 3, 60_000));
    }

    @Test void liberaDepoisDaJanela() throws Exception {
        LimitadorPublico l = new LimitadorPublico();
        assertTrue(l.permitir("ip:1", 1, 50));
        assertFalse(l.permitir("ip:1", 1, 50));
        Thread.sleep(80);
        assertTrue(l.permitir("ip:1", 1, 50));
    }
}
