package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private BigDecimal precoPorKg;
    private String unidade;

    public Material() {}

    public Material(Long id, String nome, BigDecimal precoPorKg, String unidade) {
        this.id = id;
        this.nome = nome;
        this.precoPorKg = precoPorKg;
        this.unidade = unidade;
    }

    // Getters e Setters Manuais
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public BigDecimal getPrecoPorKg() { return precoPorKg; }
    public void setPrecoPorKg(BigDecimal precoPorKg) { this.precoPorKg = precoPorKg; }

    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }
}