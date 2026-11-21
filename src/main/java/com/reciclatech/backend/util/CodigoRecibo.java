package com.reciclatech.backend.util;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Fase 31: código de cada recibo (atendimento), ex.: "K7M2-QX9A".
 * 8 letras/números sem os que confundem (0/O, 1/I/L): 31^8 ≈ 850 bilhões de combinações.
 * Guardado sem o traço (8 caracteres) na coluna oferta.lote.
 */
public final class CodigoRecibo {

    private static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"; // sem 0, 1, I, L, O
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final int TAMANHO = 8;

    private CodigoRecibo() {}

    public static String gerar() {
        StringBuilder sb = new StringBuilder(TAMANHO);
        for (int i = 0; i < TAMANHO; i++) sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        return sb.toString();
    }

    /** "k7m2-qx9a " -> "K7M2QX9A"; nulo se não tiver o formato de um código. */
    public static String normalizar(String texto) {
        if (texto == null) return null;
        String s = texto.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (s.length() != TAMANHO) return null;
        for (char c : s.toCharArray()) if (ALFABETO.indexOf(c) < 0) return null;
        return s;
    }

    /** "K7M2QX9A" -> "K7M2-QX9A" (para mostrar). */
    public static String formatar(String codigo) {
        if (codigo == null || codigo.length() != TAMANHO) return codigo;
        return codigo.substring(0, 4) + "-" + codigo.substring(4);
    }
}
