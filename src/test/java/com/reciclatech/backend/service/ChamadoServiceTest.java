package com.reciclatech.backend.service;

import com.reciclatech.backend.model.Chamado;
import com.reciclatech.backend.model.ChamadoAnexo;
import com.reciclatech.backend.model.ChamadoMensagem;
import com.reciclatech.backend.repository.ChamadoAnexoRepository;
import com.reciclatech.backend.repository.ChamadoMensagemRepository;
import com.reciclatech.backend.repository.ChamadoRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Fase 32: chamados com conversa e sininho, testados sem banco (repositórios em memória). */
class ChamadoServiceTest {

    private final List<Chamado> chamados = new ArrayList<>();
    private final List<ChamadoMensagem> msgs = new ArrayList<>();
    private final List<ChamadoAnexo> anexos = new ArrayList<>();
    private long seq = 1;

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> c, Map<String, Function<Object[], Object>> h) {
        return (T) Proxy.newProxyInstance(c.getClassLoader(), new Class<?>[]{c}, (p, m, a) -> {
            Function<Object[], Object> f = h.get(m.getName());
            if (f == null) throw new UnsupportedOperationException(m.getName());
            return f.apply(a == null ? new Object[0] : a);
        });
    }

    private ChamadoService servico() {
        ChamadoRepository cr = fake(ChamadoRepository.class, Map.of(
                "save", a -> { Chamado c = (Chamado) a[0]; if (c.getId() == null) { c.setId(seq++); chamados.add(c); } return c; },
                "findById", a -> chamados.stream().filter(c -> c.getId().equals(a[0])).findFirst(),
                "findByIdAndArmazemId", a -> chamados.stream().filter(c -> c.getId().equals(a[0]) && c.getArmazemId().equals(a[1])).findFirst(),
                "findAllByArmazemIdOrderByAtualizadoEmDesc", a -> chamados.stream().filter(c -> c.getArmazemId().equals(a[0])).toList(),
                "countByArmazemIdAndLidoPeloArmazemFalse", a -> chamados.stream().filter(c -> c.getArmazemId().equals(a[0]) && !c.isLidoPeloArmazem()).count(),
                "countByLidoPeloSuporteFalse", a -> chamados.stream().filter(c -> !c.isLidoPeloSuporte()).count(),
                "findTop200ByOrderByAtualizadoEmDesc", a -> new ArrayList<>(chamados)));
        ChamadoMensagemRepository mr = fake(ChamadoMensagemRepository.class, Map.of(
                "save", a -> { ChamadoMensagem m = (ChamadoMensagem) a[0]; m.setId(seq++); msgs.add(m); return m; },
                "findAllByChamadoIdOrderByIdAsc", a -> msgs.stream().filter(m -> m.getChamadoId().equals(a[0])).toList(),
                "countByChamadoId", a -> msgs.stream().filter(m -> m.getChamadoId().equals(a[0])).count()));
        ChamadoAnexoRepository ar = fake(ChamadoAnexoRepository.class, Map.of(
                "save", a -> { ChamadoAnexo x = (ChamadoAnexo) a[0]; x.setId(seq++); anexos.add(x); return x; },
                "findAllByChamadoIdOrderByIdAsc", a -> anexos.stream().filter(x -> x.getChamadoId().equals(a[0])).toList(),
                "countByChamadoId", a -> anexos.stream().filter(x -> x.getChamadoId().equals(a[0])).count(),
                "findById", a -> anexos.stream().filter(x -> x.getId().equals(a[0])).findFirst()));
        return new ChamadoService(cr, mr, ar);
    }

    @Test void abrirGravaChamadoEPrimeiraMensagemSemAcenderOSininho() {
        ChamadoService s = servico();
        Chamado c = s.abrir(1L, "Galpão Sol", "Ana", "Erro no caixa", "URGENTE", "Não fecha\r\no caixa", "a@b.com");
        assertEquals(Chamado.ABERTO, c.getSituacao());
        assertEquals("urgente", c.getPrioridade());
        assertEquals("#00001", c.getCodigo());
        assertEquals(1, s.mensagensDo(c.getId()).size());
        assertEquals("Não fecha\no caixa", s.mensagensDo(c.getId()).get(0).getTexto());
        assertEquals(0, s.naoLidosDoArmazem(1L));
        assertEquals(1, s.naoLidosDoSuporte());
    }

    @Test void respostaDoSuporteAcendeOSininhoEAbrirApaga() {
        ChamadoService s = servico();
        Chamado c = s.abrir(1L, "Galpão", "Ana", "Dúvida", "normal", "Como importo?", null);
        s.responderComoSuporte(c.getId(), "Pela Tabela de Preços.", false);
        assertEquals(Chamado.RESPONDIDO, c.getSituacao());
        assertEquals(1, s.naoLidosDoArmazem(1L));
        assertEquals(0, s.naoLidosDoArmazem(2L), "outro armazém não vê");
        assertEquals(0, s.naoLidosDoSuporte());
        assertNotNull(s.abrirParaArmazem(1L, c.getId()));
        assertEquals(0, s.naoLidosDoArmazem(1L));
    }

    @Test void armazemNaoAbreNemRespondeChamadoDeOutro() {
        ChamadoService s = servico();
        Chamado c = s.abrir(1L, "A", "Ana", "X", "normal", "texto", null);
        assertNull(s.abrirParaArmazem(2L, c.getId()));
        assertThrows(IllegalArgumentException.class, () -> s.responderComoArmazem(2L, c.getId(), "Zé", "oi"));
        assertTrue(s.doArmazem(2L).isEmpty());
    }

    @Test void respostaDoArmazemReabreChamadoResolvido() {
        ChamadoService s = servico();
        Chamado c = s.abrir(1L, "A", "Ana", "X", "normal", "texto", null);
        s.responderComoSuporte(c.getId(), "Resolvido", true);
        assertEquals(Chamado.FECHADO, c.getSituacao());
        s.responderComoArmazem(1L, c.getId(), "Ana", "Voltou o erro");
        assertEquals(Chamado.ABERTO, c.getSituacao());
        assertEquals(1, s.naoLidosDoSuporte());
        assertEquals(3, s.mensagensDo(c.getId()).size());
    }

    @Test void fecharSemTextoNaoCriaMensagemNemSininho() {
        ChamadoService s = servico();
        Chamado c = s.abrir(1L, "A", "Ana", "X", "normal", "texto", null);
        s.responderComoSuporte(c.getId(), "  ", true);
        assertEquals(Chamado.FECHADO, c.getSituacao());
        assertEquals(1, s.mensagensDo(c.getId()).size());
        assertEquals(0, s.naoLidosDoArmazem(1L));
        assertThrows(IllegalArgumentException.class, () -> s.responderComoSuporte(c.getId(), "", false));
    }

    @Test void validaTamanhosEPrioridadeDesconhecida() {
        ChamadoService s = servico();
        assertThrows(IllegalArgumentException.class, () -> s.abrir(1L, "A", "Ana", " ", "normal", "texto", null));
        assertThrows(IllegalArgumentException.class, () -> s.abrir(1L, "A", "Ana", "X", "normal", "a".repeat(5001), null));
        assertThrows(IllegalArgumentException.class, () -> s.abrir(null, "A", "Ana", "X", "normal", "texto", null));
        assertEquals("normal", s.abrir(1L, "A", "Ana", "X", "<script>", "texto", null).getPrioridade());
        Chamado c = s.abrir(1L, "A", "Ana", "Assunto\ncom quebra", "baixa", "texto", null);
        assertEquals("Assunto com quebra", c.getAssunto());
    }

    // ===== Fase 34: anexos =====
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 1, 2};
    private static final byte[] HTML = "<html><script>alert(1)</script></html>".getBytes();

    @Test void anexoDaRespostaFicaNaMensagemCertaESoODonoDoChamadoAbre() {
        ChamadoService s = servico();
        Chamado c = s.abrir(1L, "A", "Ana", "Erro", "normal", "texto", null, new ChamadoService.Anexo("C:\\fotos\\tela 1.png", PNG));
        Long primeira = s.mensagensDo(c.getId()).get(0).getId();
        s.responderComoArmazem(1L, c.getId(), "Ana", "outro print", new ChamadoService.Anexo("print.pdf", PNG));
        Map<Long, List<ChamadoAnexo>> m = s.anexosPorMensagem(c.getId());
        assertEquals(2, m.size());
        ChamadoAnexo a1 = m.get(primeira).get(0);
        assertEquals("tela_1.png", a1.getNome());
        assertEquals("image/png", a1.getTipo());
        assertEquals("print.pdf.png", m.get(s.mensagensDo(c.getId()).get(1).getId()).get(0).getNome(), "o tipo real (PNG) entra no nome");
        assertTrue(s.anexo(a1.getId(), 1L).isPresent());
        assertTrue(s.anexo(a1.getId(), 2L).isEmpty(), "outro armazém não abre");
        assertTrue(s.anexo(a1.getId(), null).isPresent(), "Master abre");
    }

    @Test void anexoComConteudoFalsoEhRecusadoENadaEhGravado() {
        ChamadoService s = servico();
        Chamado c = s.abrir(1L, "A", "Ana", "Erro", "normal", "texto", null);
        assertThrows(IllegalArgumentException.class, () -> s.responderComoArmazem(1L, c.getId(), "Ana", "veja", new ChamadoService.Anexo("foto.png", HTML)));
        assertEquals(1, s.mensagensDo(c.getId()).size(), "a mensagem também não entra");
        assertTrue(anexos.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> s.responderComoSuporte(c.getId(), "", true, new ChamadoService.Anexo("a.png", PNG)));
    }
}
