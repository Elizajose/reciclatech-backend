package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanoAcessoTest {

    @Test void proEstaSempreLiberado() {
        assertTrue(PlanoAcesso.liberaPro("PRO", "ATIVO"));
        assertTrue(PlanoAcesso.liberaPro("pro", "ATIVO"));
    }

    @Test void startAtivoNaoTemRecursosPro() {
        assertFalse(PlanoAcesso.liberaPro("START", "ATIVO"));
        assertFalse(PlanoAcesso.liberaPro(null, "ATIVO"));
        assertFalse(PlanoAcesso.liberaPro("START", null));
    }

    @Test void oTesteGratisRespeitaOPlanoEscolhido() {
        assertFalse(PlanoAcesso.liberaPro("START", "TRIAL"));
        assertTrue(PlanoAcesso.liberaPro("PRO", "TRIAL"));
    }

    @Test void recursosProSaoReconhecidosPeloEndereco() {
        for (String uri : new String[]{"/admin/analises", "/admin/analises/exportar", "/admin/saida", "/admin/registrar-saida-lote",
                "/admin/ajuste-estoque", "/admin/ajuste-estoque/novo", "/admin/despesas", "/admin/despesas/nova", "/admin/despesas/deletar",
                "/admin/fechar-caixa", "/admin/relatorio-gerencial", "/admin/historico", "/admin/fornecedores-vip",
                "/admin/fornecedores-vip/novo", "/admin/fornecedores-vip/deletar"}) {
            assertTrue(PlanoAcesso.recursoPro(uri), uri);
        }
    }

    @Test void telasDoBasicoNaoSaoBloqueadas() {
        for (String uri : new String[]{"/admin/coletas", "/admin/pesagem-rapida", "/admin/atender/5", "/admin/confirmar-finalizacao",
                "/admin/precos", "/admin/atualizar", "/admin/material/novo", "/admin/operadores", "/admin/assinatura",
                "/admin/suporte/enviar", "/admin/historico-alteracoes", "/extrato/3", "/meus-extratos", "/admin/perfil/atualizar-senhas"}) {
            assertFalse(PlanoAcesso.recursoPro(uri), uri);
        }
        assertFalse(PlanoAcesso.recursoPro(null));
    }

    @Test void personalizadoTemTodosOsRecursosPro() {
        assertTrue(PlanoAcesso.liberaPro("PERSONALIZADO", "ATIVO"));
        assertTrue(PlanoAcesso.liberaPro("personalizado", "TRIAL"));
    }
}
