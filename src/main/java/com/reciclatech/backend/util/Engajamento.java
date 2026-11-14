package com.reciclatech.backend.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** "Este armazém está parado?" para o painel master. Movimento = compras e vendas finalizadas. */
public final class Engajamento {

    private Engajamento() {}

    public static final int DIAS_SEM_MOVIMENTO = 7;

    /**
     * Ocioso = armazém que deveria estar operando (ATIVO ou teste dentro do prazo), já cadastrado há mais de 7 dias
     * e sem nenhuma movimentação nos últimos 7 dias. Armazém novo não conta (ainda está começando).
     */
    public static boolean ocioso(String status, LocalDate cadastro, LocalDate ultimaMovimentacao, LocalDate hoje) {
        if (hoje == null) return false;
        boolean operando = "ATIVO".equals(status) || ("TRIAL".equals(status) && !Assinatura.trialVencido(status, cadastro, hoje));
        if (!operando) return false;
        if (cadastro != null && ChronoUnit.DAYS.between(cadastro, hoje) <= DIAS_SEM_MOVIMENTO) return false;
        return ultimaMovimentacao == null || ChronoUnit.DAYS.between(ultimaMovimentacao, hoje) > DIAS_SEM_MOVIMENTO;
    }
}
