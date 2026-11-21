package com.reciclatech.backend.util;

/**
 * Fase 29: conferências da troca de senha do dono, antes de ir ao banco.
 * Mesmos limites do ParceiroService.definirSenha (8 a 72 caracteres).
 */
public final class RegraSenha {

    public static final int MINIMO = 8;
    public static final int MAXIMO = 72;

    private RegraSenha() {}

    /**
     * Nulo = pode seguir. Senão, a mensagem para a tela.
     * {@code confirmacao} nula é aceita (formulário antigo, sem o campo "repita a nova senha").
     */
    public static String validarTroca(String nova, String confirmacao) {
        if (nova == null || nova.isEmpty()) return "Informe a nova senha.";
        if (nova.length() < MINIMO) return "A nova senha deve ter pelo menos " + MINIMO + " caracteres.";
        if (nova.length() > MAXIMO) return "A nova senha deve ter no máximo " + MAXIMO + " caracteres.";
        if (confirmacao != null && !confirmacao.equals(nova)) return "A repetição não confere com a nova senha.";
        return null;
    }
}
