package com.reciclatech.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Validação de números que chegam do navegador. O servidor nunca confia no que veio da tela:
 * rejeita nulo, NaN/Infinito, zero, negativo e valores absurdos (erro de digitação com vários zeros).
 */
public final class Valores {

    private Valores() {}

    public static final BigDecimal PESO_MAX = new BigDecimal("1000000");      // 1.000 toneladas numa única linha
    public static final BigDecimal PRECO_MAX = new BigDecimal("100000");      // R$ por unidade
    public static final BigDecimal DINHEIRO_MAX = new BigDecimal("100000000"); // R$ 100 milhões

    /** Converte para BigDecimal com no máximo 3 casas; devolve null se não for um número finito. */
    public static BigDecimal decimal(Double d) {
        if (d == null || d.isNaN() || d.isInfinite()) return null;
        return BigDecimal.valueOf(d).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** Lê "12,5" ou "12.5". Devolve null se vazio ou inválido. */
    public static BigDecimal lerDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Estritamente maior que zero e não maior que o máximo. */
    public static boolean positivoAte(BigDecimal v, BigDecimal max) {
        return v != null && v.signum() > 0 && v.compareTo(max) <= 0;
    }

    public static BigDecimal peso(Double d) {
        BigDecimal v = decimal(d);
        return positivoAte(v, PESO_MAX) ? v : null;
    }

    /** Preço pode ser positivo até o limite; devolve null se inválido. Duas casas decimais. */
    public static BigDecimal preco(Double d) {
        BigDecimal v = d == null || d.isNaN() || d.isInfinite() ? null : BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP);
        return positivoAte(v, PRECO_MAX) ? v : null;
    }

    public static BigDecimal dinheiro(Double d) {
        BigDecimal v = d == null || d.isNaN() || d.isInfinite() ? null : BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP);
        return positivoAte(v, DINHEIRO_MAX) ? v : null;
    }

    /** Texto obrigatório com tamanho máximo; devolve o texto aparado ou null se inválido. */
    public static String texto(String s, int max) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() || t.length() > max ? null : t;
    }
}
