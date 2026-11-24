package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Fase 34: arquivo (imagem ou PDF, até 5 MB) anexado a uma mensagem do chamado. */
@Entity
@Table(name = "chamado_anexo", indexes = @Index(name = "idx_chamado_anexo_chamado", columnList = "chamado_id"))
public class ChamadoAnexo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chamado_id", nullable = false)
    private Long chamadoId;

    @Column(name = "mensagem_id", nullable = false)
    private Long mensagemId;

    @Column(length = 80, nullable = false)
    private String nome;

    /** Tipo descoberto pelo conteúdo do arquivo (não pelo que o navegador informou). */
    @Column(length = 40, nullable = false)
    private String tipo;

    @Column(nullable = false)
    private int tamanho;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] dados;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChamadoId() { return chamadoId; }
    public void setChamadoId(Long chamadoId) { this.chamadoId = chamadoId; }
    public Long getMensagemId() { return mensagemId; }
    public void setMensagemId(Long mensagemId) { this.mensagemId = mensagemId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public int getTamanho() { return tamanho; }
    public void setTamanho(int tamanho) { this.tamanho = tamanho; }
    public byte[] getDados() { return dados; }
    public void setDados(byte[] dados) { this.dados = dados; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    public boolean isImagem() { return tipo != null && tipo.startsWith("image/"); }
    /** "120 KB" / "1,4 MB" */
    public String getTamanhoTexto() {
        if (tamanho < 1024 * 1024) return Math.max(1, Math.round(tamanho / 1024.0)) + " KB";
        return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f MB", tamanho / (1024.0 * 1024.0));
    }
}
