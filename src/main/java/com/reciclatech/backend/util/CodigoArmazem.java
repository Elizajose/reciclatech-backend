package com.reciclatech.backend.util;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Código curto do armazém: o dono passa esse código à equipe e o operador o digita no login.
 * Alfabeto sem caracteres que se confundem (sem 0/O e 1/I/L), 6 posições.
 */
public final class CodigoArmazem {

    private CodigoArmazem() {}

    static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int TAMANHO = 6;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    public static String gerar() {
        StringBuilder sb = new StringBuilder(TAMANHO);
        for (int i = 0; i < TAMANHO; i++) sb.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        return sb.toString();
    }

    /** Aceita "k7m-2qx", " K7M 2QX " etc.: tira tudo que não é letra/número e põe em maiúsculas. */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        return texto.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
