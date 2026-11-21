package com.reciclatech.backend.service;

import com.reciclatech.backend.model.Chamado;
import com.reciclatech.backend.model.ChamadoMensagem;
import com.reciclatech.backend.repository.ChamadoMensagemRepository;
import com.reciclatech.backend.repository.ChamadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fase 32: chamados de suporte com conversa dentro do Coletaê.
 * O armazém abre e responde; o suporte (Master) responde pelo painel. Cada resposta do suporte acende o sininho do armazém.
 * Resposta dada direto no Gmail NÃO volta para cá: para isso o sistema teria de ler a caixa de entrada (IMAP),
 * guardando a senha do e-mail no servidor. Por isso a resposta é feita no painel Master, que também manda um e-mail de aviso.
 */
@Service
public class ChamadoService {

    public static final int ASSUNTO_MAX = 150;
    public static final int TEXTO_MAX = 5000;
    /** Limite de mensagens por chamado (evita conversa infinita ou abuso). */
    public static final int MENSAGENS_MAX = 200;
    private static final Set<String> PRIORIDADES = Set.of("baixa", "normal", "urgente");
    private static final ZoneId FUSO = ZoneId.of("America/Recife");

    private final ChamadoRepository chamados;
    private final ChamadoMensagemRepository mensagens;

    public ChamadoService(ChamadoRepository chamados, ChamadoMensagemRepository mensagens) {
        this.chamados = chamados;
        this.mensagens = mensagens;
    }

    private static LocalDateTime agora() { return LocalDateTime.now(FUSO); }

    static String limpar(String t, int max, String erro) {
        String v = t == null ? "" : t.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (v.isEmpty() || v.length() > max) throw new IllegalArgumentException(erro);
        return v;
    }

    private static String curto(String t, int max) {
        if (t == null) return null;
        String v = t.replaceAll("[\\r\\n]+", " ").strip();
        if (v.isEmpty()) return null;
        return v.length() > max ? v.substring(0, max) : v;
    }

    @Transactional(rollbackFor = Exception.class)
    public Chamado abrir(Long armazemId, String nomeArmazem, String autor, String assunto, String prioridade,
                         String descricao, String emailRetorno) {
        if (armazemId == null) throw new IllegalArgumentException("Armazém não identificado.");
        String a = limpar(assunto, ASSUNTO_MAX, "Preencha o assunto (até " + ASSUNTO_MAX + " letras).").replace('\n', ' ');
        String d = limpar(descricao, TEXTO_MAX, "Descreva o problema (até " + TEXTO_MAX + " letras).");
        String p = prioridade == null ? "normal" : prioridade.trim().toLowerCase(Locale.ROOT);
        if (!PRIORIDADES.contains(p)) p = "normal";
        LocalDateTime t = agora();
        Chamado c = new Chamado();
        c.setArmazemId(armazemId);
        c.setNomeArmazem(curto(nomeArmazem, 150));
        c.setAbertoPor(curto(autor, 120));
        c.setAssunto(a);
        c.setPrioridade(p);
        c.setEmailRetorno(curto(emailRetorno, 150));
        c.setSituacao(Chamado.ABERTO);
        c.setCriadoEm(t);
        c.setAtualizadoEm(t);
        c.setLidoPeloArmazem(true);
        c.setLidoPeloSuporte(false);
        c = chamados.save(c);
        mensagens.save(mensagem(c.getId(), false, c.getAbertoPor(), d, t));
        return c;
    }

    private static ChamadoMensagem mensagem(Long chamadoId, boolean doSuporte, String autor, String texto, LocalDateTime t) {
        ChamadoMensagem m = new ChamadoMensagem();
        m.setChamadoId(chamadoId);
        m.setDoSuporte(doSuporte);
        m.setAutor(autor);
        m.setTexto(texto);
        m.setCriadoEm(t);
        return m;
    }

    private void conferirLimite(Long chamadoId) {
        if (mensagens.countByChamadoId(chamadoId) >= MENSAGENS_MAX) {
            throw new IllegalArgumentException("Este chamado já tem mensagens demais. Abra um chamado novo.");
        }
    }

    /** O armazém responde (ou reabre um chamado fechado). Só no chamado do próprio armazém. */
    @Transactional(rollbackFor = Exception.class)
    public Chamado responderComoArmazem(Long armazemId, Long chamadoId, String autor, String texto) {
        Chamado c = chamados.findByIdAndArmazemId(chamadoId, armazemId)
                .orElseThrow(() -> new IllegalArgumentException("Chamado não encontrado."));
        String v = limpar(texto, TEXTO_MAX, "Escreva a mensagem (até " + TEXTO_MAX + " letras).");
        conferirLimite(c.getId());
        LocalDateTime t = agora();
        mensagens.save(mensagem(c.getId(), false, curto(autor, 120), v, t));
        c.setSituacao(Chamado.ABERTO);
        c.setLidoPeloSuporte(false);
        c.setLidoPeloArmazem(true);
        c.setAtualizadoEm(t);
        return chamados.save(c);
    }

    /** O suporte responde pelo painel Master; acende o sininho do armazém. {@code fechar}: marca como resolvido. */
    @Transactional(rollbackFor = Exception.class)
    public Chamado responderComoSuporte(Long chamadoId, String texto, boolean fechar) {
        Chamado c = chamados.findById(chamadoId).orElseThrow(() -> new IllegalArgumentException("Chamado não encontrado."));
        LocalDateTime t = agora();
        boolean temTexto = texto != null && !texto.isBlank();
        if (!temTexto && !fechar) throw new IllegalArgumentException("Escreva a resposta.");
        if (temTexto) {
            String v = limpar(texto, TEXTO_MAX, "Resposta com mais de " + TEXTO_MAX + " letras.");
            conferirLimite(c.getId());
            mensagens.save(mensagem(c.getId(), true, "Suporte Coletaê", v, t));
            c.setLidoPeloArmazem(false);
        }
        c.setSituacao(fechar ? Chamado.FECHADO : Chamado.RESPONDIDO);
        c.setLidoPeloSuporte(true);
        c.setAtualizadoEm(t);
        return chamados.save(c);
    }

    public List<Chamado> doArmazem(Long armazemId) {
        return armazemId == null ? List.of() : chamados.findAllByArmazemIdOrderByAtualizadoEmDesc(armazemId);
    }

    /** Quantos chamados têm resposta do suporte que o armazém ainda não abriu (número do sininho). */
    public long naoLidosDoArmazem(Long armazemId) {
        return armazemId == null ? 0 : chamados.countByArmazemIdAndLidoPeloArmazemFalse(armazemId);
    }

    /** Abre o chamado para o armazém (só o dele) e apaga o aviso do sininho. */
    @Transactional(rollbackFor = Exception.class)
    public Chamado abrirParaArmazem(Long armazemId, Long chamadoId) {
        Chamado c = chamados.findByIdAndArmazemId(chamadoId, armazemId).orElse(null);
        if (c != null && !c.isLidoPeloArmazem()) {
            c.setLidoPeloArmazem(true);
            c = chamados.save(c);
        }
        return c;
    }

    public List<Chamado> todos() { return chamados.findTop200ByOrderByAtualizadoEmDesc(); }

    public long naoLidosDoSuporte() { return chamados.countByLidoPeloSuporteFalse(); }

    @Transactional(rollbackFor = Exception.class)
    public Chamado abrirParaSuporte(Long chamadoId) {
        Chamado c = chamados.findById(chamadoId).orElse(null);
        if (c != null && !c.isLidoPeloSuporte()) {
            c.setLidoPeloSuporte(true);
            c = chamados.save(c);
        }
        return c;
    }

    public List<ChamadoMensagem> mensagensDo(Long chamadoId) {
        return chamadoId == null ? List.of() : mensagens.findAllByChamadoIdOrderByIdAsc(chamadoId);
    }
}
