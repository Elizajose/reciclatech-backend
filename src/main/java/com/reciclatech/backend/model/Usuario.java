package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // VÍNCULO COM O ARMAZÉM (MUITO IMPORTANTE)
    @ManyToOne
    @JoinColumn(name = "armazem_id")
    private Armazem armazem;

    private String nome;
    private String telefone;
    private String cpf;
    private String email;
    private String endereco;
    private LocalDateTime dataColeta;

    @Transient
    private String statusPlanilha;

    @Enumerated(EnumType.STRING)
    private StatusColeta status;

    @Enumerated(EnumType.STRING)
    private TipoUsuario tipo;

    public enum TipoUsuario { CATADOR, COMPRADOR, INDUSTRIA }

    public Usuario() {}

    @PrePersist
    public void prePersist() {
        if (this.dataColeta == null) this.dataColeta = LocalDateTime.now();
        if (this.status == null) this.status = StatusColeta.AGUARDANDO;
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Armazem getArmazem() { return armazem; }
    public void setArmazem(Armazem armazem) { this.armazem = armazem; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }
    public LocalDateTime getDataColeta() { return dataColeta; }
    public void setDataColeta(LocalDateTime dataColeta) { this.dataColeta = dataColeta; }
    public String getStatusPlanilha() { return statusPlanilha; }
    public void setStatusPlanilha(String statusPlanilha) { this.statusPlanilha = statusPlanilha; }
    public StatusColeta getStatus() { return status; }
    public void setStatus(StatusColeta status) { this.status = status; }
    public TipoUsuario getTipo() { return tipo; }
    public void setTipo(TipoUsuario tipo) { this.tipo = tipo; }
}