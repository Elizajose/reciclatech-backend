package com.reciclatech.backend.service;

/** O novo preço varia muito em relação ao anterior: a tela precisa pedir confirmação ao dono antes de gravar. */
public class VariacaoPrecoException extends IllegalArgumentException {
    public VariacaoPrecoException(String mensagem) { super(mensagem); }
}
