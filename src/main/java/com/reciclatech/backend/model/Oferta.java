package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Oferta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String material;
    private Double peso;
    private String endereco;

    // Novo campo para armazenar a data vinda da planilha para o Dashboard
    private String data;

    // Coordenadas mantidas para o futuro mapa do Coletaê Salgueiro
    private Double latitude;
    private Double longitude;

    private BigDecimal precoEstimado;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    private StatusOferta status = StatusOferta.DISPONIVEL;

    private LocalDate dataCriacao = LocalDate.now();

    // 👇 A CORREÇÃO ESTÁ AQUI: Adicionamos todos os status que a planilha usa! 👇
    public enum StatusOferta {
        DISPONIVEL, EM_ATENDIMENTO, FINALIZADO, VENDIDO, SAIDA_INDUSTRIA
    }

    public Oferta() {}

    // Getters e Setters
    public String getIdUsuario() {
        return (this.usuario != null && this.usuario.getId() != null)
                ? this.usuario.getId().toString()
                : null;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public Double getPeso() { return peso; }
    public void setPeso(Double peso) { this.peso = peso; }

    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    // Getter e Setter para o campo data (Necessário para o Dashboard)
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public BigDecimal getPrecoEstimado() { return precoEstimado; }
    public void setPrecoEstimado(BigDecimal precoEstimado) { this.precoEstimado = precoEstimado; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public StatusOferta getStatus() { return status; }
    public void setStatus(StatusOferta status) { this.status = status; }

    public LocalDate getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDate dataCriacao) { this.dataCriacao = dataCriacao; }

    // Setter para o Service conseguir preencher o ID vindo da planilha
    public void setIdUsuario(String idUsuarioStr) {
        if (idUsuarioStr != null && !idUsuarioStr.isEmpty()) {
            if (this.usuario == null) {
                this.usuario = new Usuario();
            }
            try {
                this.usuario.setId(Long.parseLong(idUsuarioStr));
            } catch (NumberFormatException e) {
                // Se não for número, ignora para não quebrar o código
            }
        }
    }
}