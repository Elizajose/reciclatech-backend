package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Oferta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "armazem_id")
    private Armazem armazem;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private String material; // Ideal futuramente ser @ManyToOne com Material
    private Double peso;
    private String endereco;
    private String data; // Data vinda da planilha para o Dashboard (DD/MM/YYYY)
    private BigDecimal precoEstimado;

    @Enumerated(EnumType.STRING)
    private StatusOferta status = StatusOferta.DISPONIVEL;

    private LocalDate dataCriacao = LocalDate.now();

    public enum StatusOferta {
        DISPONIVEL, EM_ATENDIMENTO, FINALIZADO, VENDIDO, SAIDA_INDUSTRIA, AJUSTE_POSITIVO, AJUSTE_NEGATIVO
    }

    public Oferta() {}

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Armazem getArmazem() { return armazem; }
    public void setArmazem(Armazem armazem) { this.armazem = armazem; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public Double getPeso() { return peso; }
    public void setPeso(Double peso) { this.peso = peso; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public BigDecimal getPrecoEstimado() { return precoEstimado; }
    public void setPrecoEstimado(BigDecimal precoEstimado) { this.precoEstimado = precoEstimado; }
    public StatusOferta getStatus() { return status; }
    public void setStatus(StatusOferta status) { this.status = status; }
    public LocalDate getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDate dataCriacao) { this.dataCriacao = dataCriacao; }

    // Método auxiliar (Compatibilidade com código antigo)
    public String getIdUsuario() {
        return (this.usuario != null && this.usuario.getId() != null) ? this.usuario.getId().toString() : null;
    }
}