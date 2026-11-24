package com.reciclatech.backend.service;

import com.reciclatech.backend.model.Chamado;
import com.reciclatech.backend.model.ChamadoAnexo;
import com.reciclatech.backend.model.ChamadoMensagem;
import com.reciclatech.backend.repository.ChamadoAnexoRepository;
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

    /** Fase 34: até 5 MB por anexo e 20 anexos por chamado. */
    public static final int ANEXO_MAX_BYTES = 5 * 1024 * 1024;
    public static final int ANEXOS_POR_CHAMADO = 20;

    /** Fase 34: arquivo enviado junto com a mensagem (nome como veio + conteúdo). */
    public record Anexo(String nome, byte[] dados) {
        public boolean vazio() { return dados == null || dados.length == 0; }
    }

    private final ChamadoRepository chamados;
    private final ChamadoMensagemRepository mensagens;
    private final ChamadoAnexoRepository anexos;

    public ChamadoService(ChamadoRepository chamados, ChamadoMensagemRepository mensagens, ChamadoAnexoRepository anexos) {
        this.chamados = chamados;
        this.mensagens = mensagens;
        this.anexos = anexos;
    }

    /** Confere o arquivo antes de gravar qualquer coisa (tipo pelo conteúdo, tamanho, limite por chamado). */
    static String conferirAnexo(Anexo a) {
        if (a == null || a.vazio()) return null;
        if (a.dados().length > ANEXO_MAX_BYTES) throw new IllegalArgumentException("O anexo passa de 5 MB.");
        String tipo = com.reciclatech.backend.util.TipoArquivo.detectar(a.dados());
        if (tipo == null) throw new IllegalArgumentException("Anexo não aceito. Envie imagem (PNG, JPG, WEBP, GIF) ou PDF.");
        return tipo;
    }

    private void gravarAnexo(Long chamadoId, Long mensagemId, Anexo a, String tipo, LocalDateTime t) {
        if (tipo == null) return;
        if (anexos.countByChamadoId(chamadoId) >= ANEXOS_POR_CHAMADO) {
            throw new IllegalArgumentException("Este chamado já tem " + ANEXOS_POR_CHAMADO + " anexos. Abra um chamado novo.");
        }
        ChamadoAnexo x = new ChamadoAnexo();
        x.setChamadoId(chamadoId);
        x.setMensagemId(mensagemId);
        x.setNome(com.reciclatech.backend.util.TipoArquivo.nomeSeguro(a.nome(), tipo));
        x.setTipo(tipo);
        x.setTamanho(a.dados().length);
        x.setDados(a.dados());
        x.setCriadoEm(t);
        anexos.save(x);
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

    public Chamado abrir(Long armazemId, String nomeArmazem, String autor, String assunto, String prioridade,
                         String descricao, String emailRetorno) {
        return abrir(armazemId, nomeArmazem, autor, assunto, prioridade, descricao, emailRetorno, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Chamado abrir(Long armazemId, String nomeArmazem, String autor, String assunto, String prioridade,
                         String descricao, String emailRetorno, Anexo anexo) {
        if (armazemId == null) throw new IllegalArgumentException("Armazém não identificado.");
        String a = limpar(assunto, ASSUNTO_MAX, "Preencha o assunto (até " + ASSUNTO_MAX + " letras).").replace('\n', ' ');
        String d = limpar(descricao, TEXTO_MAX, "Descreva o problema (até " + TEXTO_MAX + " letras).");
        String tipoAnexo = conferirAnexo(anexo);
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
        ChamadoMensagem m = mensagens.save(mensagem(c.getId(), false, c.getAbertoPor(), d, t));
        gravarAnexo(c.getId(), m.getId(), anexo, tipoAnexo, t);
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
    public Chamado responderComoArmazem(Long armazemId, Long chamadoId, String autor, String texto) {
        return responderComoArmazem(armazemId, chamadoId, autor, texto, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Chamado responderComoArmazem(Long armazemId, Long chamadoId, String autor, String texto, Anexo anexo) {
        Chamado c = chamados.findByIdAndArmazemId(chamadoId, armazemId)
                .orElseThrow(() -> new IllegalArgumentException("Chamado não encontrado."));
        String v = limpar(texto, TEXTO_MAX, "Escreva a mensagem (até " + TEXTO_MAX + " letras).");
        String tipoAnexo = conferirAnexo(anexo);
        conferirLimite(c.getId());
        LocalDateTime t = agora();
        ChamadoMensagem m = mensagens.save(mensagem(c.getId(), false, curto(autor, 120), v, t));
        gravarAnexo(c.getId(), m.getId(), anexo, tipoAnexo, t);
        c.setSituacao(Chamado.ABERTO);
        c.setLidoPeloSuporte(false);
        c.setLidoPeloArmazem(true);
        c.setAtualizadoEm(t);
        return chamados.save(c);
    }

    /** O suporte responde pelo painel Master; acende o sininho do armazém. {@code fechar}: marca como resolvido. */
    public Chamado responderComoSuporte(Long chamadoId, String texto, boolean fechar) {
        return responderComoSuporte(chamadoId, texto, fechar, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Chamado responderComoSuporte(Long chamadoId, String texto, boolean fechar, Anexo anexo) {
        Chamado c = chamados.findById(chamadoId).orElseThrow(() -> new IllegalArgumentException("Chamado não encontrado."));
        LocalDateTime t = agora();
        boolean temTexto = texto != null && !texto.isBlank();
        if (!temTexto && !fechar) throw new IllegalArgumentException("Escreva a resposta.");
        String tipoAnexo = conferirAnexo(anexo);
        if (tipoAnexo != null && !temTexto) throw new IllegalArgumentException("Escreva uma mensagem junto com o anexo.");
        if (temTexto) {
            String v = limpar(texto, TEXTO_MAX, "Resposta com mais de " + TEXTO_MAX + " letras.");
            conferirLimite(c.getId());
            ChamadoMensagem m = mensagens.save(mensagem(c.getId(), true, "Suporte Coletaê", v, t));
            gravarAnexo(c.getId(), m.getId(), anexo, tipoAnexo, t);
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

    /** Fase 34: anexos do chamado, agrupados pela mensagem (para a tela). Sem os bytes não dá: a entidade vem inteira; são poucos. */
    public java.util.Map<Long, List<ChamadoAnexo>> anexosPorMensagem(Long chamadoId) {
        java.util.Map<Long, List<ChamadoAnexo>> m = new java.util.HashMap<>();
        if (chamadoId == null) return m;
        for (ChamadoAnexo a : anexos.findAllByChamadoIdOrderByIdAsc(chamadoId)) {
            m.computeIfAbsent(a.getMensagemId(), k -> new java.util.ArrayList<>()).add(a);
        }
        return m;
    }

    /** Fase 34: o anexo, só se o chamado for do armazém (nulo = qualquer armazém, usado pelo Master). */
    public java.util.Optional<ChamadoAnexo> anexo(Long anexoId, Long armazemId) {
        return anexos.findById(anexoId).filter(a -> armazemId == null
                || chamados.findByIdAndArmazemId(a.getChamadoId(), armazemId).isPresent());
    }
}
