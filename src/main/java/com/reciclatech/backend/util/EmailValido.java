package com.reciclatech.backend.util;

import java.util.regex.Pattern;

/** Conferência simples do formato de um e-mail digitado num formulário (não prova que a caixa existe). */
public final class EmailValido {

    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    private EmailValido() {}

    public static boolean formatoValido(String email) {
        return email != null && email.length() <= 254 && FORMATO.matcher(email.trim()).matches();
    }
}
