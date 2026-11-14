package com.reciclatech.backend.util;

import com.reciclatech.backend.model.Usuario;

/** Casa um cliente com a chave da tela (telefone ou id), sem quebrar quando o cliente não tem telefone. */
public final class BuscaCliente {

    private BuscaCliente() {}

    public static boolean corresponde(Usuario u, String chave) {
        if (u == null || chave == null || chave.isBlank()) return false;
        if (chave.equals(u.getTelefone())) return true;
        return u.getId() != null && chave.equals(u.getId().toString());
    }
}
