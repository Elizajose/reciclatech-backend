package com.reciclatech.backend.service;

import com.reciclatech.backend.model.Armazem;
import com.reciclatech.backend.model.Material;
import com.reciclatech.backend.repository.ArmazemRepository;
import com.reciclatech.backend.repository.HistoricoAlteracaoRepository;
import com.reciclatech.backend.repository.MaterialRepository;
import com.reciclatech.backend.util.Assinatura;
import com.reciclatech.backend.util.CotacaoPublica;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Fase 27: dados da cotação pública (página inicial).
 *
 * Monta uma "fotografia" dos armazéns com acesso ativo (Ativo ou Em teste) e dos seus preços com poucas consultas
 * (armazéns, materiais e histórico) e guarda por até 60 segundos, para que muitas visitas à página inicial não pesem no banco.
 * Quando um armazém cria, altera ou exclui um preço, a fotografia é descartada na hora ({@link #invalidar()}).
 */
@Service
public class CotacaoPublicaService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CotacaoPublicaService.class);
    static final long VALIDADE_MS = 60_000L;
    private static final ZoneId FUSO = ZoneId.of("America/Recife");

    @Autowired private ArmazemRepository armazemRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired(required = false) private HistoricoAlteracaoRepository historicoRepository;

    private record Foto(long criadaEm, LocalDate dia, List<CotacaoPublica.ArmazemCot> armazens,
                        List<Map<String, Object>> paraPedido) {}

    private volatile Foto foto;
    private final Object trava = new Object();
    /** Muda a cada {@link #invalidar()}: uma fotografia montada durante uma alteração de preço não é guardada. */
    private final java.util.concurrent.atomic.AtomicLong geracao = new java.util.concurrent.atomic.AtomicLong();

    public LocalDate hoje() {
        return LocalDate.now(FUSO);
    }

    /** Descarta a fotografia: a próxima visita já vê os preços novos. */
    public void invalidar() {
        geracao.incrementAndGet();
        foto = null;
    }

    /** Armazéns com acesso ativo e os seus preços (sem telefone nem dados internos). */
    public List<CotacaoPublica.ArmazemCot> armazens() {
        return atual().armazens();
    }

    /**
     * Lista leve para o formulário "Agendar Coleta": idPlanilha, nome, telefone, uf e cidade (sem os preços).
     * Mesmas chaves que a página já usava.
     */
    public List<Map<String, Object>> listaParaPedido() {
        return atual().paraPedido();
    }

    private Foto atual() {
        Foto f = foto;
        LocalDate hoje = hoje();
        if (f != null && System.currentTimeMillis() - f.criadaEm() < VALIDADE_MS && f.dia().equals(hoje)) return f;
        synchronized (trava) {
            f = foto;
            if (f != null && System.currentTimeMillis() - f.criadaEm() < VALIDADE_MS && f.dia().equals(hoje)) return f;
            long g = geracao.get();
            f = montar(hoje);
            if (geracao.get() == g) foto = f;
            return f;
        }
    }

    private Foto montar(LocalDate hoje) {
        List<Armazem> ativos = new ArrayList<>();
        for (Armazem a : armazemRepository.findAll()) {
            if (a.getId() != null && Assinatura.aceitaPedidosPublicos(a.getStatus(), a.getDataCadastro(), hoje)) ativos.add(a);
        }
        List<Long> ids = ativos.stream().map(Armazem::getId).toList();

        Map<Long, List<Material>> materiaisPorArmazem = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Material m : materialRepository.findAllByArmazemIdIn(ids)) {
                if (m.getArmazem() == null || m.getArmazem().getId() == null) continue;
                materiaisPorArmazem.computeIfAbsent(m.getArmazem().getId(), k -> new ArrayList<>()).add(m);
            }
        }

        // armazém -> (nome do material normalizado -> última criação/alteração de preço)
        Map<Long, Map<String, LocalDate>> datas = new HashMap<>();
        if (!ids.isEmpty() && historicoRepository != null) {
            try {
                for (Object[] linha : historicoRepository.ultimasAlteracoesDePreco(ids)) {
                    if (linha == null || linha.length < 3 || !(linha[0] instanceof Long arm)
                            || !(linha[2] instanceof LocalDateTime quando)) continue;
                    String ref = CotacaoPublica.chave(linha[1] == null ? null : linha[1].toString());
                    datas.computeIfAbsent(arm, k -> new HashMap<>())
                         .merge(ref, quando.toLocalDate(), (x, y) -> x.isAfter(y) ? x : y);
                }
            } catch (RuntimeException e) {
                // sem a data a cotação continua funcionando; só não mostra "atualizado há X dias"
                log.warn("Não foi possível ler as datas dos preços para a cotação pública: {}", e.getMessage());
            }
        }

        List<CotacaoPublica.ArmazemCot> lista = new ArrayList<>();
        List<Map<String, Object>> paraPedido = new ArrayList<>();
        for (Armazem a : ativos) {
            Map<String, LocalDate> datasDoArmazem = datas.getOrDefault(a.getId(), Map.of());
            List<CotacaoPublica.Preco> precos = new ArrayList<>();
            for (Material m : materiaisPorArmazem.getOrDefault(a.getId(), List.of())) {
                precos.add(new CotacaoPublica.Preco(m.getNome(), m.getUnidade(), m.getPrecoPorKg(),
                        datasDoArmazem.get(CotacaoPublica.chave(m.getNome()))));
            }
            lista.add(new CotacaoPublica.ArmazemCot(a.getId(), a.getNome(), a.getUf(), a.getCidade(), precos));

            Map<String, Object> dados = new HashMap<>();
            dados.put("idPlanilha", a.getId().toString());
            dados.put("nome", a.getNome());
            dados.put("telefone", a.getTelefone());
            dados.put("uf", a.getUf());
            dados.put("cidade", a.getCidade());
            paraPedido.add(dados);
        }
        paraPedido.sort(Comparator.comparing(d -> CotacaoPublica.chave((String) d.get("nome"))));
        return new Foto(System.currentTimeMillis(), hoje, List.copyOf(lista), List.copyOf(paraPedido));
    }
}
