package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Fase 32: uma mensagem da conversa do chamado (do armazém ou do suporte). */
@Entity
@Table(name = "chamado_mensagem", indexes = @Index(name = "idx_chamado_msg_chamado", columnList = "chamado_id"))
public class ChamadoMensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chamado_id", nullable = false)
    private Long chamadoId;

    @Column(name = "do_suporte", nullable = false)
    private boolean doSuporte;

    @Column(length = 120)
    private String autor;

    @Column(length = 5000, nullable = false)
    private String texto;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChamadoId() { return chamadoId; }
    public void setChamadoId(Long chamadoId) { this.chamadoId = chamadoId; }
    public boolean isDoSuporte() { return doSuporte; }
    public void setDoSuporte(boolean doSuporte) { this.doSuporte = doSuporte; }
    public String getAutor() { return autor; }
    public void setAutor(String autor) { this.autor = autor; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
