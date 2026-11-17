package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Foto do dono (tipo DONO, ref = id do parceiro) ou logo de um armazém (tipo ARMAZEM, ref = id do armazém).
 * Fica numa tabela própria para a imagem não ser carregada junto com o dono/armazém a cada requisição.
 * Sempre JPEG pequeno (ver ImagemPerfil).
 */
@Entity
@Table(name = "imagem_conta", uniqueConstraints = @UniqueConstraint(columnNames = {"tipo", "ref_id"}))
public class ImagemConta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] dados;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    protected ImagemConta() {}

    public ImagemConta(String tipo, Long refId) {
        this.tipo = tipo;
        this.refId = refId;
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public Long getRefId() { return refId; }
    public byte[] getDados() { return dados; }
    public void setDados(byte[] dados) { this.dados = dados; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
