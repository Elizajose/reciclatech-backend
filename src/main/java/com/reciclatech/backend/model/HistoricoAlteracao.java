package com.reciclatech.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro de auditoria: quem criou, alterou ou apagou preços, materiais, despesas e preços VIP.
 * Só existe inserção: nenhuma tela ou rota edita ou apaga estes registros.
 */
@Entity
@Table(name = "historico_alteracao", indexes = @Index(name = "idx_hist_armazem_data", columnList = "armazem_id, data_hora"))
public class HistoricoAlteracao {

    public static final String MATERIAL = "MATERIAL";
    public static final String DESPESA = "DESPESA";
    public static final String PRECO_VIP = "PRECO_VIP";

    public static final String CRIADO = "CRIADO";
    public static final String PRECO_ALTERADO = "PRECO_ALTERADO";
    public static final String EXCLUIDO = "EXCLUIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Só o número do armazém (sem chave estrangeira): o histórico sobrevive mesmo se algo for removido.
    @Column(name = "armazem_id", nullable = false)
    private Long armazemId;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(nullable = false, length = 20)
    private String entidade;

    @Column(nullable = false, length = 20)
    private String acao;

    @Column(length = 255)
    private String referencia;   // nome do material, descrição da despesa, "Material - CPF final 1234"

    @Column(length = 20)
    private String unidade;

    @Column(name = "valor_anterior", precision = 14, scale = 2)
    private BigDecimal valorAnterior;

    @Column(name = "valor_novo", precision = 14, scale = 2)
    private BigDecimal valorNovo;

    @Column(length = 255)
    private String responsavel;

    public HistoricoAlteracao() {}

    public Long getId() { return id; }
    public Long getArmazemId() { return armazemId; }
    public void setArmazemId(Long armazemId) { this.armazemId = armazemId; }
    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }
    public String getEntidade() { return entidade; }
    public void setEntidade(String entidade) { this.entidade = entidade; }
    public String getAcao() { return acao; }
    public void setAcao(String acao) { this.acao = acao; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }
    public BigDecimal getValorAnterior() { return valorAnterior; }
    public void setValorAnterior(BigDecimal valorAnterior) { this.valorAnterior = valorAnterior; }
    public BigDecimal getValorNovo() { return valorNovo; }
    public void setValorNovo(BigDecimal valorNovo) { this.valorNovo = valorNovo; }
    public String getResponsavel() { return responsavel; }
    public void setResponsavel(String responsavel) { this.responsavel = responsavel; }

    /** Variação percentual (só para alteração de preço com valor anterior > 0). */
    @Transient
    public BigDecimal getVariacaoPercentual() {
        if (!PRECO_ALTERADO.equals(acao) || valorAnterior == null || valorNovo == null
                || valorAnterior.signum() <= 0) return null;
        return valorNovo.subtract(valorAnterior).multiply(BigDecimal.valueOf(100))
                .divide(valorAnterior, 1, java.math.RoundingMode.HALF_UP);
    }
}
