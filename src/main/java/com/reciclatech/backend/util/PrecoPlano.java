package com.reciclatech.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Preço mensal dos planos. START e PRO são por armazém (1 armazém por dono).
 * PERSONALIZADO é por DONO: o primeiro armazém custa o mesmo que o PRO e cada armazém a mais custa o valor "extra"
 * (padrão R$ 129,90, configurável em coletae.preco-armazem-extra / variável PRECO_ARMAZEM_EXTRA).
 */
public final class PrecoPlano {

    public static final double START = 149.90;
    public static final double PRO = 199.90;
    public static final double EXTRA_PADRAO = 129.90;

    private PrecoPlano() {}

    /** Mensalidade do dono no plano Personalizado com N armazéns (N menor que 1 conta como 1). */
    public static double personalizado(int armazens, double extra) {
        int n = Math.max(1, armazens);
        double extraValido = extra > 0 ? extra : EXTRA_PADRAO;
        return arredondar(PRO + (n - 1) * extraValido);
    }

    /** Mensalidade de um armazém START/PRO (qualquer outro texto cai no START). */
    public static double individual(String plano) {
        return "PRO".equalsIgnoreCase(plano) ? PRO : START;
    }

    private static double arredondar(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
