package com.reciclatech.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Fase 31: valor em reais por extenso para o recibo ("quarenta e dois reais e sessenta centavos"). */
public final class ValorPorExtenso {

    private static final String[] UNIDADES = {"zero", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove",
            "dez", "onze", "doze", "treze", "quatorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove"};
    private static final String[] DEZENAS = {"", "", "vinte", "trinta", "quarenta", "cinquenta", "sessenta", "setenta", "oitenta", "noventa"};
    private static final String[] CENTENAS = {"", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos", "seiscentos",
            "setecentos", "oitocentos", "novecentos"};

    private ValorPorExtenso() {}

    /** Até 999.999.999,99. Negativo ou nulo: texto vazio. */
    public static String reais(BigDecimal valor) {
        if (valor == null || valor.signum() < 0) return "";
        BigDecimal v = valor.setScale(2, RoundingMode.HALF_UP);
        long inteiro = v.longValue();
        int centavos = v.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        if (inteiro > 999_999_999L) return "";
        StringBuilder sb = new StringBuilder();
        if (inteiro > 0) {
            sb.append(numero(inteiro));
            boolean milhaoRedondo = inteiro >= 1_000_000 && inteiro % 1_000_000 == 0;
            sb.append(milhaoRedondo ? " de reais" : (inteiro == 1 ? " real" : " reais"));
        }
        if (centavos > 0) {
            if (sb.length() > 0) sb.append(" e ");
            sb.append(ate999(centavos)).append(centavos == 1 ? " centavo" : " centavos");
        }
        if (sb.length() == 0) return "zero real";
        return sb.toString();
    }

    static String numero(long n) {
        if (n == 0) return "zero";
        int milhoes = (int) (n / 1_000_000);
        int milhares = (int) ((n / 1000) % 1000);
        int resto = (int) (n % 1000);
        StringBuilder sb = new StringBuilder();
        if (milhoes > 0) sb.append(ate999(milhoes)).append(milhoes == 1 ? " milhão" : " milhões");
        if (milhares > 0) {
            if (sb.length() > 0) sb.append(resto == 0 && (milhares < 100 || milhares % 100 == 0) ? " e " : ", ");
            sb.append(milhares == 1 ? "mil" : ate999(milhares) + " mil");
        }
        if (resto > 0) {
            if (sb.length() > 0) sb.append(resto < 100 || resto % 100 == 0 ? " e " : " ");
            sb.append(ate999(resto));
        }
        return sb.toString();
    }

    static String ate999(int n) {
        if (n == 100) return "cem";
        StringBuilder sb = new StringBuilder();
        int c = n / 100, d = n % 100;
        if (c > 0) sb.append(CENTENAS[c]);
        if (d > 0) {
            if (sb.length() > 0) sb.append(" e ");
            if (d < 20) sb.append(UNIDADES[d]);
            else {
                sb.append(DEZENAS[d / 10]);
                if (d % 10 > 0) sb.append(" e ").append(UNIDADES[d % 10]);
            }
        }
        return sb.toString();
    }
}
