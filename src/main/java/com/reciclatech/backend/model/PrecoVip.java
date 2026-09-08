package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class PrecoVip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Trocamos o Telefone pelo CPF
    private String cpfCatador;

    private String materialNome;
    private BigDecimal precoEspecial;

    @ManyToOne
    @JoinColumn(name = "armazem_id")
    private Armazem armazem;

    // --- GETTERS E SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCpfCatador() { return cpfCatador; }
    public void setCpfCatador(String cpfCatador) { this.cpfCatador = cpfCatador; }

    public String getMaterialNome() { return materialNome; }
    public void setMaterialNome(String materialNome) { this.materialNome = materialNome; }

    public BigDecimal getPrecoEspecial() { return precoEspecial; }
    public void setPrecoEspecial(BigDecimal precoEspecial) { this.precoEspecial = precoEspecial; }

    public Armazem getArmazem() { return armazem; }
    public void setArmazem(Armazem armazem) { this.armazem = armazem; }
}