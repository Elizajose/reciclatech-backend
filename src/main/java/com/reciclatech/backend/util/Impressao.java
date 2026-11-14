package com.reciclatech.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * "Impressão digital" curta de um hash de senha. Fica na sessão no lugar do hash em si:
 * se a senha mudar, a impressão muda e a sessão antiga deixa de valer, sem guardar o hash na memória da sessão.
 */
public final class Impressao {

    private Impressao() {}

    public static String de(String hashDeSenha) {
        if (hashDeSenha == null) return "";
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("coletae|" + hashDeSenha).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
