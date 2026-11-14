package com.reciclatech.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Regras que o SERVIDOR aplica a cada linha de compra, independentemente do que o navegador enviou. */
public final class RegrasVenda {

    private RegrasVenda() {}

    /** Quantas vezes acima do preço de tabela (ou VIP) o preço negociado pode ficar. Acima disso é quase certo erro de digitação. */
    public static final double MULTIPLO_PADRAO = 2.0;

    /**
     * Valida peso e preço negociado de uma linha. {@code referencia} é o preço de tabela do material
     * (ou o preço VIP do fornecedor). Lança IllegalArgumentException com texto pronto para a tela.
     */
    public static void validarLinha(String material, BigDecimal peso, BigDecimal preco, BigDecimal referencia, double multiplo) {
        if (!Valores.positivoAte(peso, Valores.PESO_MAX)) {
            throw new IllegalArgumentException("Peso inválido para \"" + material + "\": use um valor maior que zero.");
        }
        if (!Valores.positivoAte(preco, Valores.PRECO_MAX)) {
            throw new IllegalArgumentException("Preço inválido para \"" + material + "\": use um valor maior que zero.");
        }
        if (referencia != null && referencia.signum() > 0 && multiplo > 0) {
            BigDecimal teto = referencia.multiply(BigDecimal.valueOf(multiplo)).setScale(2, RoundingMode.HALF_UP);
            if (preco.compareTo(teto) > 0) {
                throw new IllegalArgumentException("O preço de \"" + material + "\" (R$ " + preco.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
                        + ") está muito acima da tabela (R$ " + referencia.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
                        + "). Confira o valor digitado.");
            }
        }
    }

    /** Total da linha = preço × peso, 2 casas. Sempre calculado aqui, nunca recebido da tela. */
    public static BigDecimal total(BigDecimal preco, BigDecimal peso) {
        return preco.multiply(peso).setScale(2, RoundingMode.HALF_UP);
    }

    /** Mudança de preço grande demais para ser rotina (mais que o dobro ou menos que a metade do valor anterior): pedir confirmação. */
    public static final double VARIACAO_LIMITE = 2.0;

    public static boolean variacaoSuspeita(BigDecimal anterior, BigDecimal novo) {
        if (anterior == null || novo == null || anterior.signum() <= 0 || novo.signum() <= 0) return false;
        BigDecimal limite = BigDecimal.valueOf(VARIACAO_LIMITE);
        return novo.compareTo(anterior.multiply(limite)) > 0 || novo.compareTo(anterior.divide(limite, 4, RoundingMode.HALF_UP)) < 0;
    }

    /** Texto de confirmação mostrado ao dono, ex.: "Latinha: de R$ 8,00 para R$ 800,00 (100,0 vezes o valor anterior)". */
    public static String textoVariacao(String nome, BigDecimal anterior, BigDecimal novo) {
        String fator = novo.divide(anterior, 1, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        return nome + ": de R$ " + anterior.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
                + " para R$ " + novo.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
                + " (" + fator + " vez(es) o valor anterior).";
    }
}
