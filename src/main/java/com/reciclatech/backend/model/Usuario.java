package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @Column(unique = true)
    private String telefone; // ID principal (WhatsApp)

    private String cpf;
    private String email;
    private String endereco;
    private LocalDateTime dataColeta;

    // NOVO CAMPO: Para transportar o status lá da Planilha Google para o HTML
    @Transient
    private String statusPlanilha;

    @Enumerated(EnumType.STRING)
    private StatusColeta status;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL)
    private List<Oferta> ofertas = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private TipoUsuario tipo;

    public enum TipoUsuario {
        CATADOR, COMPRADOR
    }

    public Usuario() {}

    // Getters e Setters Manuais (Resolvem o erro do getTelefone)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public List<Oferta> getOfertas() { return ofertas; }
    public void setOfertas(List<Oferta> ofertas) { this.ofertas = ofertas; }

    public TipoUsuario getTipo() { return tipo; }
    public void setTipo(TipoUsuario tipo) { this.tipo = tipo; }

    @PrePersist
    public void prePersist() {
        if (this.dataColeta == null) {
            this.dataColeta = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = StatusColeta.AGUARDANDO;
        }
    }
}