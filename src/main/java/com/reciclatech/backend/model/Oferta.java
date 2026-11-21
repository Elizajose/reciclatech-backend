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

    // Fuso de Recife (o mesmo do resto do sistema), não o relógio do servidor
    private LocalDate dataCriacao = LocalDate.now(java.time.ZoneId.of("America/Recife"));

    /** Quem está atendendo este pedido (preenchido ao iniciar o atendimento). */
    @Column(length = 120)
    private String atendente;

    // Fase 31: identifica o atendimento (recibo) e a hora; nulos nas vendas antigas
    @Column(length = 16)
    private String lote;

    @Column(name = "criado_em")
    private java.time.LocalDateTime criadoEm;

    /** Fase 33: operador (funcionário) que atendeu. Nulo = dono/gestor ou venda antiga. Coluna nova e opcional. */
    @Column(name = "funcionario_id")
    private Long funcionarioId;

    public enum StatusOferta {
        DISPONIVEL, EM_ATENDIMENTO, FINALIZADO, CANCELADO, VENDIDO, SAIDA_INDUSTRIA, AJUSTE_POSITIVO, AJUSTE_NEGATIVO
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
    public String getLote() { return lote; }
    public void setLote(String lote) { this.lote = lote; }
    public java.time.LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(java.time.LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
    public Long getFuncionarioId() { return funcionarioId; }
    public void setFuncionarioId(Long funcionarioId) { this.funcionarioId = funcionarioId; }
    public StatusOferta getStatus() { return status; }
    public void setStatus(StatusOferta status) { this.status = status; }
    public String getAtendente() { return atendente; }
    public void setAtendente(String atendente) { this.atendente = atendente; }
    public LocalDate getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDate dataCriacao) { this.dataCriacao = dataCriacao; }

    // Método auxiliar (Compatibilidade com código antigo)
    public String getIdUsuario() {
        return (this.usuario != null && this.usuario.getId() != null) ? this.usuario.getId().toString() : null;
    }
}