package com.reciclatech.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class Armazem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Dono (conta Parceiro). Nullable de propósito: armazéns antigos ainda não têm dono vinculado.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proprietario_id")
    private Parceiro proprietario;

    private String nome;
    private String cnpj;
    private String telefone;
    private String endereco;

    /** Estado (sigla) e município: usados no filtro por região da página inicial. */
    @Column(length = 2)
    private String uf;

    @Column(length = 100)
    private String cidade;

    @Column(unique = true)
    private String login;

    /** Código curto que o operador digita no login (único). Gerado automaticamente. */
    @Column(length = 12, unique = true)
    private String codigo;

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
    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }
    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    @PrePersist
    void gerarCodigoSeFaltar() {
        if (codigo == null || codigo.isBlank()) codigo = com.reciclatech.backend.util.CodigoArmazem.gerar();
    }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    @JsonIgnore
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    @JsonIgnore
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

    @JsonIgnore // nunca serializar o dono (evita erro de proxy lazy e vazamento em respostas JSON)
    public Parceiro getProprietario() { return proprietario; }
    public void setProprietario(Parceiro proprietario) { this.proprietario = proprietario; }

    public LocalDate getUltimoLogin() { return ultimoLogin;}
    public void setUltimoLogin(LocalDate ultimoLogin) { this.ultimoLogin = ultimoLogin; }


}