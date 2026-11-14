package com.reciclatech.backend.service;

import com.reciclatech.backend.model.*;
import com.reciclatech.backend.repository.*;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Regras da fase 8 (atendimento) e 8.5 (histórico) testadas sem banco, com repositórios simulados. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatabaseServiceAtendimentoTest {

    @Mock private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Mock private ArmazemRepository armazemRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private OfertaRepository ofertaRepository;
    @Mock private DespesaRepository despesaRepository;
    @Mock private CaixaDiarioRepository caixaDiarioRepository;
    @Mock private PrecoVipRepository precoVipRepository;
    @Mock private FuncionarioRepository funcionarioRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private HistoricoAlteracaoRepository historicoRepository;
    @Mock private ParceiroRepository parceiroRepository;
    @Mock private HttpSession session;

    @InjectMocks private DatabaseService service;

    private Armazem armazem;
    private Usuario cliente;

    @BeforeEach
    void preparar() {
        armazem = new Armazem();
        armazem.setId(1L);
        cliente = new Usuario();
        cliente.setId(10L);
        cliente.setArmazem(armazem);
        cliente.setTelefone("87999990000");

        when(session.getAttribute("idPlanilhaAtiva")).thenReturn("1");
        when(armazemRepository.findById(1L)).thenReturn(Optional.of(armazem));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(cliente));
    }

    private List<List<Object>> loteComUmaVenda() {
        List<Object> linha = Arrays.asList("ID1", "Latinha", 10.0, "ENTREGA NO LOCAL",
                new BigDecimal("8.00"), new BigDecimal("80.00"), "2026-11-11", "10", "VENDIDO", "NÃO INFORMADO");
        return Collections.singletonList(linha);
    }

    @Test
    void finalizarSemPedidoAbertoNaoGravaNada() throws Exception {
        when(ofertaRepository.travarPorUsuariosEStatus(anyCollection(), anyCollection())).thenReturn(new ArrayList<>());

        boolean gravou = service.finalizarAtendimento("10", null, loteComUmaVenda());

        assertFalse(gravou);
        verify(ofertaRepository, never()).save(any());
    }

    @Test
    void finalizarComPedidoAbertoGravaVendaEEncerraPedido() throws Exception {
        Oferta aberto = new Oferta();
        aberto.setUsuario(cliente);
        aberto.setStatus(Oferta.StatusOferta.EM_ATENDIMENTO);
        when(ofertaRepository.travarPorUsuariosEStatus(anyCollection(), anyCollection()))
                .thenReturn(new ArrayList<>(List.of(aberto)));
        // fase 17: o servidor confere se o material existe NESTE armazém (e o preço contra a tabela)
        Material latinha = new Material();
        latinha.setNome("Latinha");
        latinha.setUnidade("KG");
        latinha.setPrecoPorKg(new BigDecimal("8.00"));
        latinha.setArmazem(armazem);
        when(materialRepository.findAllByArmazemIdOrderByNomeAsc(1L)).thenReturn(new ArrayList<>(List.of(latinha)));

        boolean gravou = service.finalizarAtendimento("10", null, loteComUmaVenda());

        assertTrue(gravou);
        assertEquals(Oferta.StatusOferta.FINALIZADO, aberto.getStatus());
        ArgumentCaptor<Oferta> salvos = ArgumentCaptor.forClass(Oferta.class);
        verify(ofertaRepository, atLeastOnce()).save(salvos.capture());
        assertTrue(salvos.getAllValues().stream().anyMatch(o -> o.getStatus() == Oferta.StatusOferta.VENDIDO
                && "Latinha".equals(o.getMaterial())));
    }

    @Test
    void cancelarMarcaComoCancelado() throws Exception {
        Oferta aberto = new Oferta();
        aberto.setUsuario(cliente);
        aberto.setStatus(Oferta.StatusOferta.DISPONIVEL);
        when(ofertaRepository.travarPorUsuariosEStatus(anyCollection(), anyCollection()))
                .thenReturn(new ArrayList<>(List.of(aberto)));

        service.cancelarSolicitacao("10");

        assertEquals(Oferta.StatusOferta.CANCELADO, aberto.getStatus());
    }

    @Test
    void pedidoRepetidoDoMesmoTelefoneComPedidoAbertoNaoCriaOutro() throws Exception {
        when(usuarioRepository.findAllByTelefoneAndArmazemIdOrderByIdAsc("87999990000", 1L)).thenReturn(List.of(cliente));
        when(ofertaRepository.findAllByUsuarioIdAndStatusIn(eq(10L), anyList())).thenReturn(List.of(new Oferta()));

        Usuario novo = new Usuario();
        novo.setNome("Fulano");
        novo.setTelefone("87999990000");
        service.salvarSolicitacaoInicial(novo, "Rua A", "1");

        verify(ofertaRepository, never()).save(any());
    }

    @Test
    void pedidoNovoDeClienteExistenteReaproveitaOUsuario() throws Exception {
        when(usuarioRepository.findAllByTelefoneAndArmazemIdOrderByIdAsc("87999990000", 1L)).thenReturn(List.of(cliente));
        when(ofertaRepository.findAllByUsuarioIdAndStatusIn(eq(10L), anyList())).thenReturn(new ArrayList<>());

        Usuario novo = new Usuario();
        novo.setNome("Fulano");
        novo.setTelefone("87999990000");
        service.salvarSolicitacaoInicial(novo, "Rua A", "1");

        ArgumentCaptor<Oferta> oferta = ArgumentCaptor.forClass(Oferta.class);
        verify(ofertaRepository, times(1)).save(oferta.capture());
        assertSame(cliente, oferta.getValue().getUsuario());
        assertEquals(Oferta.StatusOferta.DISPONIVEL, oferta.getValue().getStatus());
    }

    @Test
    void alterarPrecoRegistraHistoricoComValorAntigoENovo() throws Exception {
        Material m = new Material();
        m.setId(5L);
        m.setNome("Latinha");
        m.setUnidade("KG");
        m.setArmazem(armazem);
        m.setPrecoPorKg(new BigDecimal("7.50"));
        when(materialRepository.findById(5L)).thenReturn(Optional.of(m));

        service.atualizarPrecoMaterial(5L, new BigDecimal("8.00"));

        ArgumentCaptor<HistoricoAlteracao> h = ArgumentCaptor.forClass(HistoricoAlteracao.class);
        verify(historicoRepository).save(h.capture());
        assertEquals(HistoricoAlteracao.PRECO_ALTERADO, h.getValue().getAcao());
        assertEquals(0, new BigDecimal("7.50").compareTo(h.getValue().getValorAnterior()));
        assertEquals(0, new BigDecimal("8.00").compareTo(h.getValue().getValorNovo()));
        assertEquals(new BigDecimal("6.7"), h.getValue().getVariacaoPercentual());
    }

    @Test
    void salvarMesmoPrecoNaoRegistraHistorico() throws Exception {
        Material m = new Material();
        m.setId(5L);
        m.setNome("Latinha");
        m.setArmazem(armazem);
        m.setPrecoPorKg(new BigDecimal("8.00"));
        when(materialRepository.findById(5L)).thenReturn(Optional.of(m));

        service.atualizarPrecoMaterial(5L, new BigDecimal("8.0"));

        verify(historicoRepository, never()).save(any());
    }

}
