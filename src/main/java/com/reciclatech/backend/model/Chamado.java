package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Fase 32: chamado de suporte guardado no sistema (antes só ia por e-mail).
 * Situação e prioridade em texto (não enum) para o Hibernate não criar restrição CHECK que depois não atualiza
 * (foi a causa do erro ao cancelar na Fase 30).
 */
@Entity
@Table(name = "chamado", indexes = {
        @Index(name = "idx_chamado_armazem", columnList = "armazem_id"),
        @Index(name = "idx_chamado_atualizado", columnList = "atualizado_em")})
public class Chamado {

    public static final String ABERTO = "ABERTO";          // esperando o suporte
    public static final String RESPONDIDO = "RESPONDIDO";  // o suporte respondeu; esperando o armazém
    public static final String FECHADO = "FECHADO";        // resolvido

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "armazem_id", nullable = false)
    private Long armazemId;

    @Column(name = "nome_armazem", length = 150)
    private String nomeArmazem;

    @Column(length = 150, nullable = false)
    private String assunto;

    @Column(length = 20)
    private String prioridade;

    @Column(length = 20, nullable = false)
    private String situacao = ABERTO;

    @Column(name = "email_retorno", length = 150)
    private String emailRetorno;

    @Column(name = "aberto_por", length = 120)
    private String abertoPor;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    /** O armazém já viu a última resposta do suporte? (o sininho conta os que estão "false") */
    @Column(name = "lido_armazem", nullable = false)
    private boolean lidoPeloArmazem = true;

    /** O suporte já viu a última mensagem do armazém? */
    @Column(name = "lido_suporte", nullable = false)
    private boolean lidoPeloSuporte = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getArmazemId() { return armazemId; }
    public void setArmazemId(Long armazemId) { this.armazemId = armazemId; }
    public String getNomeArmazem() { return nomeArmazem; }
    public void setNomeArmazem(String nomeArmazem) { this.nomeArmazem = nomeArmazem; }
    public String getAssunto() { return assunto; }
    public void setAssunto(String assunto) { this.assunto = assunto; }
    public String getPrioridade() { return prioridade; }
    public void setPrioridade(String prioridade) { this.prioridade = prioridade; }
    public String getSituacao() { return situacao; }
    public void setSituacao(String situacao) { this.situacao = situacao; }
    public String getEmailRetorno() { return emailRetorno; }
    public void setEmailRetorno(String emailRetorno) { this.emailRetorno = emailRetorno; }
    public String getAbertoPor() { return abertoPor; }
    public void setAbertoPor(String abertoPor) { this.abertoPor = abertoPor; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public boolean isLidoPeloArmazem() { return lidoPeloArmazem; }
    public void setLidoPeloArmazem(boolean lidoPeloArmazem) { this.lidoPeloArmazem = lidoPeloArmazem; }
    public boolean isLidoPeloSuporte() { return lidoPeloSuporte; }
    public void setLidoPeloSuporte(boolean lidoPeloSuporte) { this.lidoPeloSuporte = lidoPeloSuporte; }

    /** Código curto para mostrar e para o assunto do e-mail: #00012. */
    public String getCodigo() { return id == null ? "#-----" : String.format("#%05d", id); }
}
