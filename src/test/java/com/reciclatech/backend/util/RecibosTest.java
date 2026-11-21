package com.reciclatech.backend.util;

import com.reciclatech.backend.model.Oferta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Fase 31: um recibo por atendimento (antes o recibo somava todas as compras do cliente). */
class RecibosTest {

    private static long seq = 1;

    private static Oferta o(String lote, LocalDateTime quando, String data, String total, String atendente) {
        Oferta x = new Oferta();
        x.setId(seq++);
        x.setLote(lote);
        x.setCriadoEm(quando);
        x.setData(data);
        x.setPrecoEstimado(new BigDecimal(total));
        x.setAtendente(atendente);
        return x;
    }

    @Test void separaPorAtendimentoEMostraOMaisRecentePrimeiro() {
        LocalDateTime ontem = LocalDateTime.of(2026, 11, 20, 9, 0);
        LocalDateTime hoje = LocalDateTime.of(2026, 11, 21, 14, 30);
        List<Recibos.Grupo> g = Recibos.agrupar(List.of(
                o("AAAA2222", ontem, "2026-11-20", "10.00", "Luiz"),
                o("BBBB3333", hoje, "2026-11-21", "18.00", "Ana"),
                o("BBBB3333", hoje, "2026-11-21", "24.60", "Ana")));
        assertEquals(2, g.size());
        assertEquals("BBBB3333", g.get(0).chave);
        assertEquals(0, new BigDecimal("42.60").compareTo(g.get(0).total));
        assertEquals("Ana", g.get(0).atendente);
        assertEquals("BBBB-3333", g.get(0).getCodigo());
        assertEquals(0, new BigDecimal("10.00").compareTo(g.get(1).total));
    }

    @Test void vendasAntigasSemLoteAgrupamPeloDia() {
        List<Recibos.Grupo> g = Recibos.agrupar(List.of(
                o(null, null, "2026-11-19", "5.00", null),
                o(null, null, "2026-11-19", "7.00", null),
                o(null, null, "2026-11-18", "1.00", null)));
        assertEquals(2, g.size());
        assertEquals("D2026-11-19", g.get(0).chave);
        assertEquals(LocalDate.of(2026, 11, 19), g.get(0).data);
        assertNull(g.get(0).lote);
        assertNull(g.get(0).getCodigo());
    }

    @Test void escolherPelaChaveOuOMaisRecente() {
        List<Recibos.Grupo> g = Recibos.agrupar(List.of(
                o("AAAA2222", LocalDateTime.of(2026, 11, 20, 9, 0), "2026-11-20", "10.00", null),
                o("BBBB3333", LocalDateTime.of(2026, 11, 21, 9, 0), "2026-11-21", "20.00", null)));
        assertEquals("BBBB3333", Recibos.escolher(g, null).get().chave);
        assertEquals("AAAA2222", Recibos.escolher(g, "aaaa2222").get().chave);
        assertTrue(Recibos.escolher(g, "ZZZZ9999").isEmpty());
        assertTrue(Recibos.escolher(List.of(), null).isEmpty());
    }

    @Test void nomeResumidoParaAVerificacaoPublica() {
        assertEquals("Maria S.", Recibos.nomeResumido("MARIA DA SILVA"));
        assertEquals("Cooperativa", Recibos.nomeResumido("cooperativa (SAÍDA)"));
        assertEquals("", Recibos.nomeResumido(null));
    }

    @Test void codigoDoRecibo() {
        String c = CodigoRecibo.gerar();
        assertEquals(8, c.length());
        assertEquals(c, CodigoRecibo.normalizar(CodigoRecibo.formatar(c).toLowerCase()));
        assertNull(CodigoRecibo.normalizar("ABCD-12O0")); // 0, 1 e O não existem no código
        assertNull(CodigoRecibo.normalizar("curto"));
        assertNull(CodigoRecibo.normalizar(null));
    }
}
