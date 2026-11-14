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
}
