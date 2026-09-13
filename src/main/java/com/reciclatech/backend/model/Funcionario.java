package com.reciclatech.backend.model;

import jakarta.persistence.*;

@Entity
public class Funcionario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // VÍNCULO COM O ARMAZÉM: Garante que o funcionário só veja os dados do galpão onde trabalha
    @ManyToOne
    @JoinColumn(name = "armazem_id", nullable = false)
    private Armazem armazem;

    private String nome;

    @Column(unique = true, nullable = false)
    private String login;

    @Column(nullable = false)
    private String senha;

    // Vai ser sempre "OPERADOR" por padrão, mas deixamos aberto caso você crie outros no futuro
    private String perfil = "OPERADOR";

    // Para o dono poder bloquear um funcionário que foi demitido sem precisar apagar do banco
    private boolean ativo = true;

    public Funcionario() {}

    // ==========================================
    // GETTERS E SETTERS
    // ==========================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Armazem getArmazem() { return armazem; }
    public void setArmazem(Armazem armazem) { this.armazem = armazem; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}