package com.reciclatech.backend.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Garante que /api/usuarios não entra em loop infinito (Usuario.ofertas <-> Oferta.usuario). */
class UsuarioJsonTest {

    @Test
    void usuarioComOfertaSerializaSemRecursao() throws Exception {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setNome("Joao");
        Oferta o = new Oferta();
        o.setUsuario(u);
        u.setOfertas(new ArrayList<>(java.util.List.of(o)));

        String json = new ObjectMapper().writeValueAsString(u);

        assertTrue(json.contains("Joao"));
        assertFalse(json.contains("\"ofertas\""), "a lista de ofertas não deve ir no JSON do usuário");
    }

    @Test
    void jsonDoUsuarioNaoMostraArmazemCodigoNemLogin() throws Exception {
        Armazem a = new Armazem();
        a.setId(7L);
        a.setCodigo("ABC123");
        a.setLogin("login-secreto-do-armazem");
        Usuario u = new Usuario();
        u.setId(1L);
        u.setNome("Joao");
        u.setArmazem(a);

        String json = new ObjectMapper().writeValueAsString(u);

        assertFalse(json.contains("ABC123"));
        assertFalse(json.contains("login-secreto-do-armazem"));
        assertFalse(json.contains("\"armazem\""));
    }

    @Test
    void usuarioRecebidoPorJsonIgnoraArmazemEnviado() throws Exception {
        Usuario u = new ObjectMapper().readValue("{\"nome\":\"X\",\"armazem\":{\"id\":99}}", Usuario.class);
        assertNull(u.getArmazem(), "quem chama a API não escolhe o armazém");
    }
}
