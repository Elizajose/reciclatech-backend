package com.reciclatech.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginThrottleTest {

    @Test void bloqueiaDepoisDeCincoFalhas() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 4; i++) t.falhou("login:a@a.com");
        assertFalse(t.bloqueado("login:a@a.com"));
        t.falhou("login:a@a.com");
        assertTrue(t.bloqueado("login:a@a.com"));
    }

    @Test void ignoraMaiusculasNaChave() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 5; i++) t.falhou("LOGIN:A@A.COM");
        assertTrue(t.bloqueado("login:a@a.com"));
    }

    @Test void sucessoZeraAsFalhas() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 5; i++) t.falhou("x");
        t.sucesso("x");
        assertFalse(t.bloqueado("x"));
    }

    @Test void outraChaveNaoEBloqueada() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 5; i++) t.falhou("x");
        assertFalse(t.bloqueado("y"));
    }
}
