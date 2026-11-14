package com.reciclatech.backend.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Regra única de "este armazém pode operar?" (usada no login, na troca de armazém e na revalidação da sessão). */
public final class Assinatura {

    private Assinatura() {}

    public static final int DIAS_TRIAL = 7;

    /** Teste grátis acabou: status TRIAL com mais de 7 dias desde o cadastro. Sem data de cadastro não expira. */
    public static boolean trialVencido(String status, LocalDate dataCadastro, LocalDate hoje) {
        if (!"TRIAL".equals(status) || dataCadastro == null || hoje == null) return false;
        return ChronoUnit.DAYS.between(dataCadastro, hoje) > DIAS_TRIAL;
    }

    /** Bloqueado para operar: BLOQUEADO, SUSPENSO ou teste vencido. */
    public static boolean bloqueado(String status, LocalDate dataCadastro, LocalDate hoje) {
        return "BLOQUEADO".equals(status) || "SUSPENSO".equals(status) || trialVencido(status, dataCadastro, hoje);
    }

    /** Aparece na página pública e aceita pedidos: ATIVO, ou TRIAL dentro dos 7 dias. */
    public static boolean aceitaPedidosPublicos(String status, LocalDate dataCadastro, LocalDate hoje) {
        if ("ATIVO".equals(status)) return true;
        return "TRIAL".equals(status) && dataCadastro != null && !trialVencido(status, dataCadastro, hoje);
    }
}
