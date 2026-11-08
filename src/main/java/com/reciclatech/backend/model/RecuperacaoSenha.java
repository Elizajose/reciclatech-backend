package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Pedido de redefinição de senha. Guarda só o HASH do token (o token real vai apenas no e-mail). */
@Entity
@Table(name = "recuperacao_senha")
public class RecuperacaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parceiro_id", nullable = false)
    private Parceiro parceiro;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    protected RecuperacaoSenha() {}

    public RecuperacaoSenha(Parceiro parceiro, String tokenHash, LocalDateTime expiraEm) {
        this.parceiro = parceiro;
        this.tokenHash = tokenHash;
        this.expiraEm = expiraEm;
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Parceiro getParceiro() { return parceiro; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public LocalDateTime getUsadoEm() { return usadoEm; }
    public void setUsadoEm(LocalDateTime usadoEm) { this.usadoEm = usadoEm; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
