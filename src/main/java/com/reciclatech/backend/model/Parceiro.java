package com.reciclatech.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Parceiro = DONO do negócio (conta de acesso): e-mail, senha e, no futuro, recuperação de senha.
 * Um Parceiro pode ter vários Armazéns (1 para N).
 * Não confundir com Usuario, que é quem pede/leva material à cooperativa.
 */
@Entity
@Table(name = "parceiro")
public class Parceiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Sempre salvo em minúsculas. */
    @Column(nullable = false, unique = true, length = 160)
    private String email;

    /** Hash BCrypt (60 caracteres). Nunca a senha em texto. */
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    @OneToMany(mappedBy = "proprietario")
    private List<Armazem> armazens = new ArrayList<>();

    protected Parceiro() {}

    public Parceiro(String nome, String email, String senhaHash) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @JsonIgnore
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getUltimoLogin() { return ultimoLogin; }
    public void setUltimoLogin(LocalDateTime ultimoLogin) { this.ultimoLogin = ultimoLogin; }

    @JsonIgnore
    public List<Armazem> getArmazens() { return armazens; }
}
