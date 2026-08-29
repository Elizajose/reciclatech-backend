package com.reciclatech.backend.model;

import jakarta.persistence.*;

@Entity
public class Armazem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String cnpj;
    private String telefone;

    @Column(unique = true)
    private String login;

    private String senha;
    private String senhaFinanceira;
    private String perfil;
    private String status; // ATIVO, SUSPENSO

    public Armazem() {}

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    public String getSenhaFinanceira() { return senhaFinanceira; }
    public void setSenhaFinanceira(String senhaFinanceira) { this.senhaFinanceira = senhaFinanceira; }
    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}