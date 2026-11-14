package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Material;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Material com nome repetido ou campo vazio derrubava análises, relatório e extrato (Collectors.toMap). */
class TelaControllerMapasTest {

    private static Material m(String nome, String unidade, String preco) {
        Material x = new Material();
        x.setNome(nome);
        x.setUnidade(unidade);
        x.setPrecoPorKg(preco == null ? null : new BigDecimal(preco));
        return x;
    }

    @Test void nomeRepetidoNaoQuebraEFicaComOPrimeiro() {
        List<Material> lista = List.of(m("Latinha", "KG", "8.00"), m("Latinha", "UN", "9.00"));
        Map<String, String> un = TelaController.mapaUnidades(lista);
        Map<String, BigDecimal> pr = TelaController.mapaPrecos(lista);
        assertEquals("KG", un.get("Latinha"));
        assertEquals(0, new BigDecimal("8.00").compareTo(pr.get("Latinha")));
    }

    @Test void campoVazioNaoQuebra() {
        List<Material> lista = List.of(m("Papel", null, null));
        assertEquals("KG", TelaController.mapaUnidades(lista).get("Papel"));
        assertEquals(0, BigDecimal.ZERO.compareTo(TelaController.mapaPrecos(lista).get("Papel")));
    }
}
