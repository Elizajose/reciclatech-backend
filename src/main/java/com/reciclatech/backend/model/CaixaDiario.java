package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class CaixaDiario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "armazem_id")
    private Armazem armazem;

    private LocalDate dataFechamento;
    private BigDecimal entradas;
    private BigDecimal saidas;
    private BigDecimal despesas;
    private BigDecimal lucroLiquido;
    private Double pesoComprado;
    private String status;

    public CaixaDiario() {}

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Armazem getArmazem() { return armazem; }
    public void setArmazem(Armazem armazem) { this.armazem = armazem; }
    public LocalDate getDataFechamento() { return dataFechamento; }
    public void setDataFechamento(LocalDate dataFechamento) { this.dataFechamento = dataFechamento; }
    public BigDecimal getEntradas() { return entradas; }
    public void setEntradas(BigDecimal entradas) { this.entradas = entradas; }
    public BigDecimal getSaidas() { return saidas; }
    public void setSaidas(BigDecimal saidas) { this.saidas = saidas; }
    public BigDecimal getDespesas() { return despesas; }
    public void setDespesas(BigDecimal despesas) { this.despesas = despesas; }
    public BigDecimal getLucroLiquido() { return lucroLiquido; }
    public void setLucroLiquido(BigDecimal lucroLiquido) { this.lucroLiquido = lucroLiquido; }
    public Double getPesoComprado() { return pesoComprado; }
    public void setPesoComprado(Double pesoComprado) { this.pesoComprado = pesoComprado; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}