package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class Armazem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String cnpj;
    private String telefone;
    private String endereco;

    @Column(unique = true)
    private String login;

    private String senha;

    @Column(name = "senha_financeira")
    private String senhaFinanceira;

    private String perfil;
    private String status; // ATIVO, SUSPENSO, TRIAL, BLOQUEADO
    private String plano;
    private LocalDate ultimoLogin;

    // CORREÇÃO AQUI: Mantém o nome no banco com underline, mas no Java fica no padrão CamelCase
    @Column(name = "data_cadastro")
    private LocalDate dataCadastro;

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
    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

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

    public String getPlano() { return plano; }
    public void setPlano(String plano) { this.plano = plano; }

    public LocalDate getDataCadastro() { return dataCadastro; }
    public void setDataCadastro(LocalDate dataCadastro) { this.dataCadastro = dataCadastro; }

    public LocalDate getUltimoLogin() { return ultimoLogin;}
    public void setUltimoLogin(LocalDate ultimoLogin) { this.ultimoLogin = ultimoLogin; }


}