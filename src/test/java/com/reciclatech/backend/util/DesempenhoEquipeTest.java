package com.reciclatech.backend.util;

import com.reciclatech.backend.model.Oferta;
import com.reciclatech.backend.model.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Fase 33: atendimentos por funcionário e recibos visíveis só para quem atendeu. */
class DesempenhoEquipeTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 21);
    private long seq = 1;

    private Oferta venda(long cliente, String lote, Long func, String atendente, String material, double peso, String valor, LocalDate dia) {
        Usuario u = new Usuario(); u.setId(cliente); u.setNome("Cliente " + cliente);
        Oferta o = new Oferta();
        o.setId(seq++);
        o.setUsuario(u); o.setLote(lote); o.setFuncionarioId(func); o.setAtendente(atendente);
        o.setMaterial(material); o.setPeso(peso); o.setPrecoEstimado(new BigDecimal(valor));
        o.setStatus(Oferta.StatusOferta.VENDIDO);
        o.setCriadoEm(lote == null ? null : dia.atTime(10, 0));
        o.setData(dia.toString());
        return o;
    }

    private static final Map<String, String> UN = Map.of("Latinhas", "KG", "Cobre", "KG", "Litro Pitú", "UN");
    private static final List<DesempenhoEquipe.Funcionario> EQUIPE = List.of(
            new DesempenhoEquipe.Funcionario(10L, "Luiz", true),
            new DesempenhoEquipe.Funcionario(11L, "Pablo", true),
            new DesempenhoEquipe.Funcionario(12L, "Dedé", false));

    @Test void contaAtendimentosMaterialEValorPorFuncionario() {
        List<Oferta> v = new ArrayList<>(List.of(
                venda(1, "AAAA1111", 10L, "Luiz", "Latinhas", 30, "222.00", HOJE),
                venda(1, "AAAA1111", 10L, "Luiz", "Cobre", 2, "80.00", HOJE),
                venda(2, "BBBB2222", 10L, "Luiz", "Litro Pitú", 50, "25.00", HOJE.minusDays(2)),
                venda(3, "CCCC3333", 11L, "Pablo", "Latinhas", 10, "74.00", HOJE),
                venda(4, "DDDD4444", null, "Proprietário", "Cobre", 5, "200.00", HOJE),
                venda(5, "EEEE5555", null, "Pablo", "Latinhas", 1, "7.40", HOJE),          // Fase 31-32: só o nome
                venda(6, null, null, null, "Latinhas", 3, "21.00", HOJE),                   // antiga
                venda(7, "FFFF6666", 10L, "Luiz", "Latinhas", 99, "999.00", HOJE.minusMonths(2))));  // fora do período
        Oferta cancelada = venda(8, "GGGG7777", 11L, "Pablo", "Cobre", 1, "40.00", HOJE);
        cancelada.setStatus(Oferta.StatusOferta.CANCELADO);
        v.add(cancelada);

        DesempenhoEquipe.Resultado r = DesempenhoEquipe.calcular(v, EQUIPE, UN, HOJE.withDayOfMonth(1), HOJE);
        Map<String, DesempenhoEquipe.Linha> p = new HashMap<>();
        for (DesempenhoEquipe.Linha l : r.linhas) p.put(l.nome, l);

        DesempenhoEquipe.Linha luiz = p.get("Luiz");
        assertEquals(2, luiz.atendimentos);
        assertEquals(0, new BigDecimal("32").compareTo(luiz.kg));
        assertEquals(0, new BigDecimal("50").compareTo(luiz.un));
        assertEquals(0, new BigDecimal("327.00").compareTo(luiz.total));
        assertEquals("Latinhas", luiz.principais.get(0));

        DesempenhoEquipe.Linha pablo = p.get("Pablo");
        assertEquals(2, pablo.atendimentos, "o recibo só com o nome (Fase 31-32) soma no Pablo cadastrado; o cancelado não conta");
        assertEquals(0, new BigDecimal("81.40").compareTo(pablo.total));

        assertEquals(1, p.get("Proprietário").atendimentos);
        assertFalse(p.get("Proprietário").funcionario);
        assertEquals(1, p.get(DesempenhoEquipe.SEM_REGISTRO).atendimentos);
        assertFalse(p.containsKey("Dedé"), "bloqueado e sem movimento não aparece");

        assertEquals("Luiz", r.linhas.get(0).nome, "ordem: quem pagou mais primeiro");
        assertEquals(6, r.atendimentos);
        assertEquals(0, new BigDecimal("629.40").compareTo(r.total));
    }

    @Test void operadorAtivoSemMovimentoApareceComZero() {
        DesempenhoEquipe.Resultado r = DesempenhoEquipe.calcular(List.of(), EQUIPE, UN, HOJE, HOJE);
        assertEquals(2, r.linhas.size());
        assertTrue(r.linhas.stream().allMatch(l -> l.atendimentos == 0));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.linhas.get(0).participacao));
    }

    @Test void operadorVeSoOsRecibosDele() {
        Recibos.Grupo doLuiz = Recibos.agrupar(List.of(venda(1, "AAAA1111", 10L, "Luiz", "Cobre", 1, "40", HOJE))).get(0);
        Recibos.Grupo soNome = Recibos.agrupar(List.of(venda(1, "BBBB2222", null, "Luiz", "Cobre", 1, "40", HOJE))).get(0);
        Recibos.Grupo antigo = Recibos.agrupar(List.of(venda(1, null, null, null, "Cobre", 1, "40", HOJE))).get(0);
        assertTrue(Recibos.doAtendente(doLuiz, 10L, "Luiz"));
        assertFalse(Recibos.doAtendente(doLuiz, 11L, "Luiz"), "mesmo nome, outro operador: não vê");
        assertTrue(Recibos.doAtendente(soNome, 10L, " luiz "), "recibo das Fases 31-32: vale o nome");
        assertFalse(Recibos.doAtendente(soNome, 11L, "Pablo"));
        assertFalse(Recibos.doAtendente(antigo, 10L, "Luiz"), "venda antiga sem código: só o dono vê");
    }
}
