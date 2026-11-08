package com.reciclatech.backend.service;

/** Lançada quando uma operação precisa de um armazém na sessão e o usuário não está logado. */
public class SessaoInvalidaException extends RuntimeException {
    public SessaoInvalidaException() {
        super("Sessão sem armazém ativo");
    }
}
