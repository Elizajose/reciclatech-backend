package com.reciclatech.backend.util;

import com.reciclatech.backend.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuscaClienteTest {

    private Usuario cliente(Long id, String telefone) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setTelefone(telefone);
        return u;
    }

    @Test
    void clienteSemTelefoneNaoQuebraABusca() {
        Usuario semTelefone = cliente(7L, null);
        assertFalse(BuscaCliente.corresponde(semTelefone, "87999999999"));
        assertTrue(BuscaCliente.corresponde(semTelefone, "7"), "ainda deve casar pelo id");
    }

    @Test
    void casaPorTelefoneOuPorId() {
        Usuario u = cliente(3L, "87988887777");
        assertTrue(BuscaCliente.corresponde(u, "87988887777"));
        assertTrue(BuscaCliente.corresponde(u, "3"));
        assertFalse(BuscaCliente.corresponde(u, "4"));
    }

    @Test
    void chaveVaziaOuNulaNaoCasaComNinguem() {
        Usuario u = cliente(3L, "87988887777");
        assertFalse(BuscaCliente.corresponde(u, null));
        assertFalse(BuscaCliente.corresponde(u, " "));
        assertFalse(BuscaCliente.corresponde(null, "3"));
    }

    @Test
    void clienteSemIdENemTelefoneNaoQuebra() {
        assertFalse(BuscaCliente.corresponde(cliente(null, null), "1"));
    }
}
