package com.reciclatech.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Cálculo do resultado financeiro de um armazém (sem Spring, sem banco: só regras de negócio).
 *
 * Vocabulário do sistema:
 *   VENDIDO          = compra de material de um catador (dinheiro SAI)
 *   SAIDA_INDUSTRIA  = venda do armazém para a indústria/atacado (dinheiro ENTRA)
 *   AJUSTE_POSITIVO  = entrada manual de estoque (sem custo)
 *   AJUSTE_NEGATIVO  = perda/quebra manual de estoque
 *
 * Lucro = vendas - custo do que foi vendido - perdas - despesas.
 * O custo do que foi vendido usa o CUSTO MÉDIO do material no momento da venda
 * (o que sobra no pátio continua guardado como estoque, não como prejuízo).
 */
public final class AnaliseFinanceira {

    private AnaliseFinanceira() {}

    // ------------------------------------------------------------------ entrada
    public static final class Movimento {
        public final Long id;
        public final String material;
        public final String status;
        public final BigDecimal peso;
        public final BigDecimal valor;
        public final LocalDate data;
        public final String parceiro; // fornecedor (catador) nas compras

        public Movimento(Long id, String material, String status, BigDecimal peso, BigDecimal valor, LocalDate data, String parceiro) {
            this.id = id;
            this.material = material;
            this.status = status;
            this.peso = peso == null ? BigDecimal.ZERO : peso;
            this.valor = valor == null ? BigDecimal.ZERO : valor;
            this.data = data;
            this.parceiro = parceiro;
        }
    }

    public static final class DespesaMov {
        public final LocalDate data;
        public final BigDecimal valor;
        public DespesaMov(LocalDate data, BigDecimal valor) {
            this.data = data;
            this.valor = valor == null ? BigDecimal.ZERO : valor;
        }
    }

    // ------------------------------------------------------------------ saída
    public static final class LinhaMaterial {
        public String material;
        public BigDecimal qtdVendida = BigDecimal.ZERO;
        public BigDecimal receita = BigDecimal.ZERO;
        public BigDecimal custo = BigDecimal.ZERO;
        public BigDecimal lucro = BigDecimal.ZERO;
        public BigDecimal precoMedioVenda;   // receita / qtd
        public BigDecimal custoMedio;        // custo / qtd
        public BigDecimal margemPercentual;  // lucro / receita
    }

    public static final class LinhaEstoque {
        public String material;
        public BigDecimal qtd = BigDecimal.ZERO;
        public BigDecimal custoMedio = BigDecimal.ZERO;
        public BigDecimal valorCusto = BigDecimal.ZERO;
        public BigDecimal precoUltimaVenda;  // pode ser nulo (nunca vendeu)
        public BigDecimal valorRevenda;      // qtd x último preço de venda (ou custo, se nunca vendeu)
    }

    public static final class LinhaFornecedor {
        public String nome;
        public BigDecimal valorPago = BigDecimal.ZERO;
        public BigDecimal qtd = BigDecimal.ZERO;
    }

    public static final class Resultado {
        public LocalDate de, ate;
        public BigDecimal receita = BigDecimal.ZERO;          // vendas à indústria
        public BigDecimal custoVendido = BigDecimal.ZERO;     // custo médio do que foi vendido
        public BigDecimal lucroBruto = BigDecimal.ZERO;
        public BigDecimal perdas = BigDecimal.ZERO;           // quebra/ajuste negativo, a custo médio
        public BigDecimal despesas = BigDecimal.ZERO;
        public BigDecimal lucroLiquido = BigDecimal.ZERO;
        public BigDecimal margemPercentual;                   // lucro líquido / receita (nulo sem vendas)
        public BigDecimal compras = BigDecimal.ZERO;          // pago aos catadores no período
        public BigDecimal caixaPeriodo = BigDecimal.ZERO;     // receita - compras - despesas
        public int atendimentos;                              // cliente/dia com compra
        public BigDecimal ticketMedio;                        // compras / atendimentos
        public BigDecimal lucroPeriodoAnterior;               // mesmo tamanho, imediatamente antes
        public BigDecimal variacaoLucroPercentual;            // vs período anterior (nulo se não dá para comparar)
        public List<LinhaMaterial> porMaterial = new ArrayList<>();
        public List<LinhaEstoque> estoque = new ArrayList<>();
        public BigDecimal estoqueValorCusto = BigDecimal.ZERO;
        public BigDecimal estoqueValorRevenda = BigDecimal.ZERO;
        public List<LinhaFornecedor> topFornecedores = new ArrayList<>();
        public boolean temMovimento;                          // houve compra/venda/despesa no período
    }

    // ------------------------------------------------------------------ cálculo
    public static Resultado calcular(List<Movimento> movimentos, List<DespesaMov> despesas, LocalDate de, LocalDate ate) {
        Resultado r = calcularPeriodo(movimentos, despesas, de, ate);

        long dias = ChronoUnit.DAYS.between(de, ate) + 1;
        LocalDate antAte = de.minusDays(1);
        LocalDate antDe = antAte.minusDays(dias - 1);
        BigDecimal anterior = calcularPeriodo(movimentos, despesas, antDe, antAte).lucroLiquido;
        r.lucroPeriodoAnterior = anterior;
        if (anterior.signum() != 0) {
            r.variacaoLucroPercentual = r.lucroLiquido.subtract(anterior)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(anterior.abs(), 1, RoundingMode.HALF_UP);
        }
        return r;
    }

    private static final class Pilha {
        BigDecimal qtd = BigDecimal.ZERO;
        BigDecimal custo = BigDecimal.ZERO;
        BigDecimal precoUltimaVenda;

        BigDecimal custoMedio() {
            return qtd.signum() > 0 ? custo.divide(qtd, 10, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        }
    }

    private static Resultado calcularPeriodo(List<Movimento> movimentos, List<DespesaMov> despesas, LocalDate de, LocalDate ate) {
        Resultado r = new Resultado();
        r.de = de;
        r.ate = ate;

        List<Movimento> ordenados = new ArrayList<>();
        for (Movimento m : movimentos) {
            if (m != null && m.data != null && m.material != null && m.status != null && !m.data.isAfter(ate)) ordenados.add(m);
        }
        ordenados.sort(Comparator.comparing((Movimento m) -> m.data)
                .thenComparing(m -> m.id == null ? Long.MAX_VALUE : m.id));

        Map<String, Pilha> pilhas = new LinkedHashMap<>();
        Map<String, LinhaMaterial> linhas = new LinkedHashMap<>();
        Map<String, LinhaFornecedor> fornecedores = new HashMap<>();
        Set<String> atendimentos = new HashSet<>();

        for (Movimento m : ordenados) {
            Pilha p = pilhas.computeIfAbsent(m.material, k -> new Pilha());
            boolean noPeriodo = !m.data.isBefore(de);

            switch (m.status) {
                case "VENDIDO": {
                    p.qtd = p.qtd.add(m.peso);
                    p.custo = p.custo.add(m.valor);
                    if (noPeriodo) {
                        r.compras = r.compras.add(m.valor);
                        r.temMovimento = true;
                        String nome = m.parceiro == null || m.parceiro.isBlank() ? "(sem nome)" : m.parceiro.trim();
                        atendimentos.add(nome + "|" + m.data);
                        LinhaFornecedor f = fornecedores.computeIfAbsent(nome, k -> { LinhaFornecedor x = new LinhaFornecedor(); x.nome = k; return x; });
                        f.valorPago = f.valorPago.add(m.valor);
                        f.qtd = f.qtd.add(m.peso);
                    }
                    break;
                }
                case "AJUSTE_POSITIVO": {
                    p.qtd = p.qtd.add(m.peso);
                    p.custo = p.custo.add(m.valor);
                    break;
                }
                case "SAIDA_INDUSTRIA": {
                    BigDecimal medio = p.custoMedio();
                    BigDecimal comCusto = m.peso.min(p.qtd.max(BigDecimal.ZERO));
                    BigDecimal cogs = medio.multiply(comCusto);
                    p.qtd = p.qtd.subtract(m.peso).max(BigDecimal.ZERO);
                    p.custo = p.custo.subtract(cogs).max(BigDecimal.ZERO);
                    if (m.peso.signum() > 0) p.precoUltimaVenda = m.valor.divide(m.peso, 6, RoundingMode.HALF_UP);
                    if (noPeriodo) {
                        r.receita = r.receita.add(m.valor);
                        r.custoVendido = r.custoVendido.add(cogs);
                        r.temMovimento = true;
                        LinhaMaterial l = linhas.computeIfAbsent(m.material, k -> { LinhaMaterial x = new LinhaMaterial(); x.material = k; return x; });
                        l.qtdVendida = l.qtdVendida.add(m.peso);
                        l.receita = l.receita.add(m.valor);
                        l.custo = l.custo.add(cogs);
                    }
                    break;
                }
                case "AJUSTE_NEGATIVO": {
                    BigDecimal medio = p.custoMedio();
                    BigDecimal retirada = m.peso.min(p.qtd.max(BigDecimal.ZERO));
                    BigDecimal valorPerda = medio.multiply(retirada);
                    p.qtd = p.qtd.subtract(retirada);
                    p.custo = p.custo.subtract(valorPerda).max(BigDecimal.ZERO);
                    if (noPeriodo) {
                        r.perdas = r.perdas.add(valorPerda);
                        r.temMovimento = true;
                    }
                    break;
                }
                default:
                    break; // outros status (DISPONIVEL, FINALIZADO, CANCELADO...) não mexem em dinheiro nem estoque
            }
        }

        for (DespesaMov d : despesas) {
            if (d != null && d.data != null && !d.data.isBefore(de) && !d.data.isAfter(ate)) {
                r.despesas = r.despesas.add(d.valor);
                r.temMovimento = true;
            }
        }

        r.lucroBruto = r.receita.subtract(r.custoVendido);
        r.lucroLiquido = r.lucroBruto.subtract(r.perdas).subtract(r.despesas);
        r.caixaPeriodo = r.receita.subtract(r.compras).subtract(r.despesas);
        if (r.receita.signum() > 0) {
            r.margemPercentual = r.lucroLiquido.multiply(BigDecimal.valueOf(100)).divide(r.receita, 1, RoundingMode.HALF_UP);
        }
        r.atendimentos = atendimentos.size();
        if (r.atendimentos > 0) r.ticketMedio = r.compras.divide(BigDecimal.valueOf(r.atendimentos), 2, RoundingMode.HALF_UP);

        for (LinhaMaterial l : linhas.values()) {
            l.lucro = l.receita.subtract(l.custo);
            if (l.qtdVendida.signum() > 0) {
                l.precoMedioVenda = l.receita.divide(l.qtdVendida, 2, RoundingMode.HALF_UP);
                l.custoMedio = l.custo.divide(l.qtdVendida, 2, RoundingMode.HALF_UP);
            }
            if (l.receita.signum() > 0) l.margemPercentual = l.lucro.multiply(BigDecimal.valueOf(100)).divide(l.receita, 1, RoundingMode.HALF_UP);
            l.receita = dinheiro(l.receita); l.custo = dinheiro(l.custo); l.lucro = dinheiro(l.lucro);
            r.porMaterial.add(l);
        }
        r.porMaterial.sort((a, b) -> b.lucro.compareTo(a.lucro));

        for (Map.Entry<String, Pilha> e : pilhas.entrySet()) {
            Pilha p = e.getValue();
            if (p.qtd.signum() <= 0) continue;
            LinhaEstoque s = new LinhaEstoque();
            s.material = e.getKey();
            s.qtd = p.qtd.setScale(3, RoundingMode.HALF_UP);
            s.custoMedio = p.custoMedio().setScale(2, RoundingMode.HALF_UP);
            s.valorCusto = dinheiro(p.custo);
            s.precoUltimaVenda = p.precoUltimaVenda == null ? null : p.precoUltimaVenda.setScale(2, RoundingMode.HALF_UP);
            s.valorRevenda = dinheiro(p.precoUltimaVenda == null ? p.custo : p.precoUltimaVenda.multiply(p.qtd));
            r.estoque.add(s);
            r.estoqueValorCusto = r.estoqueValorCusto.add(s.valorCusto);
            r.estoqueValorRevenda = r.estoqueValorRevenda.add(s.valorRevenda);
        }
        r.estoque.sort(Comparator.comparing((LinhaEstoque s) -> s.valorCusto).reversed());

        List<LinhaFornecedor> lista = new ArrayList<>(fornecedores.values());
        lista.sort((a, b) -> b.valorPago.compareTo(a.valorPago));
        for (int i = 0; i < Math.min(5, lista.size()); i++) {
            LinhaFornecedor f = lista.get(i);
            f.valorPago = dinheiro(f.valorPago);
            f.qtd = f.qtd.setScale(1, RoundingMode.HALF_UP);
            r.topFornecedores.add(f);
        }

        r.receita = dinheiro(r.receita);
        r.custoVendido = dinheiro(r.custoVendido);
        r.lucroBruto = dinheiro(r.lucroBruto);
        r.perdas = dinheiro(r.perdas);
        r.despesas = dinheiro(r.despesas);
        r.lucroLiquido = dinheiro(r.lucroLiquido);
        r.compras = dinheiro(r.compras);
        r.caixaPeriodo = dinheiro(r.caixaPeriodo);
        r.estoqueValorCusto = dinheiro(r.estoqueValorCusto);
        r.estoqueValorRevenda = dinheiro(r.estoqueValorRevenda);
        return r;
    }

    private static BigDecimal dinheiro(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
