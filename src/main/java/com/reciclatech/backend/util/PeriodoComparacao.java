package com.reciclatech.backend.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Período escolhido na tela de comparação (hoje, 7d, 30d, mes, custom). Sempre devolve um período válido. */
public final class PeriodoComparacao {

    public final String selecionado;
    public final LocalDate de;
    public final LocalDate ate;

    private PeriodoComparacao(String selecionado, LocalDate de, LocalDate ate) {
        this.selecionado = selecionado;
        this.de = de;
        this.ate = ate;
    }

    public static PeriodoComparacao resolver(String periodo, String deTexto, String ateTexto, LocalDate hoje) {
        String sel = periodo == null ? "mes" : periodo.trim();
        LocalDate de = hoje, ate = hoje;
        switch (sel) {
            case "hoje": break;
            case "7d": de = hoje.minusDays(6); break;
            case "30d": de = hoje.minusDays(29); break;
            case "mes": de = hoje.withDayOfMonth(1); break;
            case "custom":
                try {
                    de = LocalDate.parse(deTexto);
                    ate = LocalDate.parse(ateTexto);
                } catch (Exception e) {
                    sel = "mes"; de = hoje.withDayOfMonth(1); ate = hoje;
                }
                break;
            default: sel = "mes"; de = hoje.withDayOfMonth(1);
        }
        if (ate.isAfter(hoje)) ate = hoje;
        if (de.isAfter(ate)) de = ate;
        if (ChronoUnit.DAYS.between(de, ate) > 366) de = ate.minusDays(366);
        return new PeriodoComparacao(sel, de, ate);
    }
}
