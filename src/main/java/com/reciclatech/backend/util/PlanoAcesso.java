package com.reciclatech.backend.util;

/**
 * Quem pode usar o quê. Regra única, aplicada no servidor (a tela só esconde o botão):
 * - plano PRO e plano PERSONALIZADO (vários armazéns): tudo liberado (inclusive durante o teste de 7 dias);
 * - plano START (Básico): sem os recursos exclusivos do PRO (também durante o teste).
 * O teste grátis respeita o plano escolhido no cadastro.
 * Os dados do cliente nunca são apagados: só o acesso às telas fica trancado até o upgrade.
 */
public final class PlanoAcesso {

    private PlanoAcesso() {}

    public static boolean liberaPro(String plano, String status) {
        return "PRO".equalsIgnoreCase(plano) || PERSONALIZADO.equalsIgnoreCase(plano);
    }

    public static final String PERSONALIZADO = "PERSONALIZADO";

    /** Endereços exclusivos do plano PRO: Financeiro, Estoque e Saída, Despesas/Caixa, Histórico de Vendas, Relatório e VIPs. */
    public static boolean recursoPro(String uri) {
        if (uri == null) return false;
        return igualOuFilho(uri, "/admin/analises")
                || igualOuFilho(uri, "/admin/saida")
                || igualOuFilho(uri, "/admin/registrar-saida-lote")
                || igualOuFilho(uri, "/admin/ajuste-estoque")
                || igualOuFilho(uri, "/admin/despesas")
                || igualOuFilho(uri, "/admin/fechar-caixa")
                || igualOuFilho(uri, "/admin/relatorio-gerencial")
                || igualOuFilho(uri, "/admin/historico")          // histórico de vendas (NÃO inclui /admin/historico-alteracoes)
                || igualOuFilho(uri, "/admin/fornecedores-vip");
    }

    private static boolean igualOuFilho(String uri, String base) {
        return uri.equals(base) || uri.startsWith(base + "/");
    }
}
