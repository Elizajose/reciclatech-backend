package com.reciclatech.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Compara os armazéns de um mesmo dono (sem Spring e sem banco). O lucro vem da mesma conta da tela Financeiro
 * (AnaliseFinanceira), então o número aqui é igual ao que cada armazém vê no próprio painel.
 * Também mostra quanto de cada material cada armazém COMPROU dos catadores no período.
 */
public final class ComparacaoArmazens {

    private ComparacaoArmazens() {}

    public static final class Entrada {
        public final Long id;
        public final String nome;
        public final String local;
        public final List<AnaliseFinanceira.Movimento> movimentos;
        public final List<AnaliseFinanceira.DespesaMov> despesas;

        public Entrada(Long id, String nome, String local, List<AnaliseFinanceira.Movimento> movimentos, List<AnaliseFinanceira.DespesaMov> despesas) {
            this.id = id;
            this.nome = nome;
            this.local = local;
            this.movimentos = movimentos == null ? List.of() : movimentos;
            this.despesas = despesas == null ? List.of() : despesas;
        }
    }

    public static final class Linha {
        public Long id;
        public String nome;
        public String local;
        public AnaliseFinanceira.Resultado fin;
        public BigDecimal kgComprado = BigDecimal.ZERO;
        public Map<String, BigDecimal> kgPorMaterial = new LinkedHashMap<>();
        public Map<String, BigDecimal> valorPorMaterial = new LinkedHashMap<>();
        public boolean temMovimento;
        public boolean maiorLucro;
        public boolean menorLucro;
        public String maisComprado;    // material com mais kg comprados (nulo sem compras)
        public String menosComprado;   // material com menos kg comprados (nulo se só há um)
        public String situacao;        // LUCRO, PREJUIZO, SEM_VENDAS (comprou e não vendeu) ou SEM_MOVIMENTO
        public BigDecimal precoMedioCompra;   // R$ por kg pago aos catadores no período (nulo sem compras)
        public BigDecimal kgVendido = BigDecimal.ZERO;
        public BigDecimal precoMedioVenda;    // R$ por kg vendido à indústria (nulo sem vendas)
        public BigDecimal estoqueKg = BigDecimal.ZERO;        // estoque ao fim do período
        public BigDecimal estoqueCusto = BigDecimal.ZERO;
        public BigDecimal estoqueRevenda = BigDecimal.ZERO;
        public int pctLucro, pctVendas, pctCompras;           // largura das barras (0 a 100, relativa ao maior armazém)
        public String materialMaisLucrativo;                  // material que mais deu lucro (nulo sem vendas)
    }

    /** Frase pronta para a tela: tipo = bom, aviso, perigo ou info. */
    public static final class Destaque {
        public final String tipo;
        public final String texto;
        public Destaque(String tipo, String texto) { this.tipo = tipo; this.texto = texto; }
    }

    public static final class MaterialLinha {
        public String material;
        public BigDecimal kgTotal = BigDecimal.ZERO;
        public Map<Long, BigDecimal> kgPorArmazem = new LinkedHashMap<>();
        public Long armazemQueMaisComprou;
        public Long armazemQueMenosComprou;   // só entre os que compraram algo
        public Map<Long, BigDecimal> valorPorArmazem = new LinkedHashMap<>();
        public Map<Long, BigDecimal> precoMedioCompra = new LinkedHashMap<>();   // R$/kg pago, por armazém
        public Map<Long, AnaliseFinanceira.LinhaMaterial> venda = new LinkedHashMap<>();   // vendas e lucro do material, por armazém
        public BigDecimal lucroTotal = BigDecimal.ZERO;
    }

    public static final class Resultado {
        public LocalDate de, ate;
        public List<Linha> armazens = new ArrayList<>();       // ordenados por lucro líquido, do maior ao menor
        public List<MaterialLinha> materiais = new ArrayList<>(); // do mais ao menos comprado (kg, todos os armazéns)
        public BigDecimal lucroTotal = BigDecimal.ZERO;
        public BigDecimal receitaTotal = BigDecimal.ZERO;
        public BigDecimal comprasTotal = BigDecimal.ZERO;
        public BigDecimal kgTotal = BigDecimal.ZERO;
        public BigDecimal caixaTotal = BigDecimal.ZERO;          // vendas - compras - despesas
        public BigDecimal despesasTotal = BigDecimal.ZERO;
        public BigDecimal estoqueCustoTotal = BigDecimal.ZERO;
        public List<Destaque> destaques = new ArrayList<>();
    }

    /** Limites usados nos avisos (ajustáveis num só lugar). */
    static final BigDecimal DESPESA_PESADA = new BigDecimal("0.30");   // despesas / vendas
    static final BigDecimal QUEDA_FORTE = new BigDecimal("-20");        // % de variação do lucro
    static final BigDecimal DIFERENCA_PRECO = new BigDecimal("0.10");   // diferença relativa de preço pago por kg

    public static Resultado comparar(List<Entrada> entradas, LocalDate de, LocalDate ate) {
        Resultado r = new Resultado();
        r.de = de;
        r.ate = ate;
        Map<String, MaterialLinha> porMaterial = new LinkedHashMap<>();

        for (Entrada e : entradas) {
            Linha l = new Linha();
            l.id = e.id;
            l.nome = e.nome;
            l.local = e.local;
            l.fin = AnaliseFinanceira.calcular(e.movimentos, e.despesas, de, ate);
            l.temMovimento = l.fin.temMovimento;

            for (AnaliseFinanceira.Movimento m : e.movimentos) {
                if (m == null || m.data == null || m.material == null || !"VENDIDO".equals(m.status)) continue;
                if (m.data.isBefore(de) || m.data.isAfter(ate)) continue;
                String mat = m.material.trim();
                l.kgComprado = l.kgComprado.add(m.peso);
                l.kgPorMaterial.merge(mat, m.peso, BigDecimal::add);
                l.valorPorMaterial.merge(mat, m.valor, BigDecimal::add);
                MaterialLinha ml = porMaterial.computeIfAbsent(chave(mat), k -> { MaterialLinha x = new MaterialLinha(); x.material = mat; return x; });
                ml.kgTotal = ml.kgTotal.add(m.peso);
                ml.kgPorArmazem.merge(e.id, m.peso, BigDecimal::add);
                ml.valorPorArmazem.merge(e.id, m.valor, BigDecimal::add);
            }
            if (!l.kgPorMaterial.isEmpty()) {
                l.maisComprado = extremo(l.kgPorMaterial, true);
                if (l.kgPorMaterial.size() > 1) l.menosComprado = extremo(l.kgPorMaterial, false);
            }
            if (l.kgComprado.signum() > 0) l.precoMedioCompra = l.fin.compras.divide(l.kgComprado, 2, RoundingMode.HALF_UP);
            BigDecimal bestLucro = null;
            for (AnaliseFinanceira.LinhaMaterial lm : l.fin.porMaterial) {
                l.kgVendido = l.kgVendido.add(lm.qtdVendida);
                if (bestLucro == null || lm.lucro.compareTo(bestLucro) > 0) { bestLucro = lm.lucro; l.materialMaisLucrativo = lm.material; }
                MaterialLinha ml = porMaterial.computeIfAbsent(chave(lm.material), k -> { MaterialLinha x = new MaterialLinha(); x.material = lm.material.trim(); return x; });
                ml.venda.put(e.id, lm);
                ml.lucroTotal = ml.lucroTotal.add(lm.lucro);
            }
            if (l.kgVendido.signum() > 0) l.precoMedioVenda = l.fin.receita.divide(l.kgVendido, 2, RoundingMode.HALF_UP);
            for (AnaliseFinanceira.LinhaEstoque le : l.fin.estoque) l.estoqueKg = l.estoqueKg.add(le.qtd);
            l.estoqueCusto = l.fin.estoqueValorCusto;
            l.estoqueRevenda = l.fin.estoqueValorRevenda;
            if (!l.temMovimento) l.situacao = "SEM_MOVIMENTO";
            else if (l.fin.lucroLiquido.signum() < 0) l.situacao = "PREJUIZO";
            else if (l.fin.receita.signum() == 0 && l.fin.compras.signum() > 0) l.situacao = "SEM_VENDAS";
            else l.situacao = "LUCRO";
            r.lucroTotal = r.lucroTotal.add(l.fin.lucroLiquido);
            r.receitaTotal = r.receitaTotal.add(l.fin.receita);
            r.comprasTotal = r.comprasTotal.add(l.fin.compras);
            r.despesasTotal = r.despesasTotal.add(l.fin.despesas);
            r.caixaTotal = r.caixaTotal.add(l.fin.caixaPeriodo);
            r.estoqueCustoTotal = r.estoqueCustoTotal.add(l.estoqueCusto);
            r.kgTotal = r.kgTotal.add(l.kgComprado);
            r.armazens.add(l);
        }

        r.armazens.sort((a, b) -> b.fin.lucroLiquido.compareTo(a.fin.lucroLiquido));
        // "Maior/Menor lucro" só entre armazéns que VENDERAM no período, com 2 ou mais e lucros diferentes
        List<Linha> comVendas = new ArrayList<>();
        for (Linha l : r.armazens) if (l.fin.receita.signum() > 0) comVendas.add(l);
        if (comVendas.size() >= 2) {
            Linha melhor = comVendas.get(0), pior = comVendas.get(comVendas.size() - 1);
            if (melhor.fin.lucroLiquido.compareTo(pior.fin.lucroLiquido) != 0) { melhor.maiorLucro = true; pior.menorLucro = true; }
        }

        for (MaterialLinha ml : porMaterial.values()) {
            BigDecimal max = null, min = null;
            for (Map.Entry<Long, BigDecimal> en : ml.kgPorArmazem.entrySet()) {
                if (max == null || en.getValue().compareTo(max) > 0) { max = en.getValue(); ml.armazemQueMaisComprou = en.getKey(); }
                if (min == null || en.getValue().compareTo(min) < 0) { min = en.getValue(); ml.armazemQueMenosComprou = en.getKey(); }
                BigDecimal valor = ml.valorPorArmazem.get(en.getKey());
                if (valor != null && en.getValue().signum() > 0) ml.precoMedioCompra.put(en.getKey(), valor.divide(en.getValue(), 2, RoundingMode.HALF_UP));
            }
            if (ml.kgPorArmazem.size() < 2 || (max != null && max.compareTo(min) == 0)) {
                ml.armazemQueMenosComprou = null; // sem comparação possível
            }
            r.materiais.add(ml);
        }
        r.materiais.sort((a, b) -> {
            int c = b.kgTotal.compareTo(a.kgTotal);
            return c != 0 ? c : b.lucroTotal.compareTo(a.lucroTotal);
        });

        // largura das barras: cada valor em relação ao maior (em módulo) entre os armazéns
        BigDecimal maxLucro = BigDecimal.ZERO, maxVendas = BigDecimal.ZERO, maxCompras = BigDecimal.ZERO;
        for (Linha l : r.armazens) {
            maxLucro = maxLucro.max(l.fin.lucroLiquido.abs());
            maxVendas = maxVendas.max(l.fin.receita);
            maxCompras = maxCompras.max(l.fin.compras);
        }
        for (Linha l : r.armazens) {
            l.pctLucro = pct(l.fin.lucroLiquido.abs(), maxLucro);
            l.pctVendas = pct(l.fin.receita, maxVendas);
            l.pctCompras = pct(l.fin.compras, maxCompras);
        }

        r.destaques = destaques(r);
        return r;
    }

    private static int pct(BigDecimal valor, BigDecimal maximo) {
        if (maximo.signum() <= 0) return 0;
        int p = valor.multiply(BigDecimal.valueOf(100)).divide(maximo, 0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, p));
    }

    private static String chave(String material) {
        return material.trim().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ destaques (frases prontas)
    private static String brl(BigDecimal v) {
        java.text.NumberFormat f = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "R$ " + f.format(v);
    }

    private static String kg(BigDecimal v) {
        java.text.NumberFormat f = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));
        f.setMinimumFractionDigits(0);
        f.setMaximumFractionDigits(1);
        return f.format(v) + " kg";
    }

    static List<Destaque> destaques(Resultado r) {
        List<Destaque> d = new ArrayList<>();

        // 1) quem sustenta o lucro da conta (parte de cada um na soma dos lucros positivos)
        BigDecimal positivos = BigDecimal.ZERO;
        for (Linha l : r.armazens) if (l.fin.lucroLiquido.signum() > 0) positivos = positivos.add(l.fin.lucroLiquido);
        if (r.armazens.size() >= 2 && positivos.signum() > 0) {
            Linha topo = r.armazens.get(0);
            if (topo.fin.lucroLiquido.signum() > 0) {
                BigDecimal parte = topo.fin.lucroLiquido.multiply(BigDecimal.valueOf(100)).divide(positivos, 0, RoundingMode.HALF_UP);
                if (parte.intValue() >= 60) {
                    d.add(new Destaque("info", topo.nome + " gerou " + parte.intValue() + "% do lucro dos armazéns que tiveram lucro ("
                            + brl(topo.fin.lucroLiquido) + " de " + brl(positivos) + ")."));
                }
            }
        }
        for (Linha l : r.armazens) {
            // 2) prejuízo realizado
            if ("PREJUIZO".equals(l.situacao)) {
                d.add(new Destaque("perigo", l.nome + " teve prejuízo de " + brl(l.fin.lucroLiquido.abs())
                        + " no período. Confira despesas, perdas de estoque e materiais vendidos abaixo do custo."));
            }
            // 3) comprou e não vendeu: o dinheiro está no estoque, o lucro ainda não apareceu
            if ("SEM_VENDAS".equals(l.situacao)) {
                d.add(new Destaque("aviso", l.nome + " comprou " + brl(l.fin.compras) + " (" + kg(l.kgComprado)
                        + ") e não vendeu nada no período: " + brl(l.estoqueCusto)
                        + " estão em estoque. O lucro só aparece quando o material é vendido."));
            }
            // 4) despesas pesadas
            if (l.fin.receita.signum() > 0 && l.fin.despesas.signum() > 0
                    && l.fin.despesas.compareTo(l.fin.receita.multiply(DESPESA_PESADA)) >= 0) {
                BigDecimal p = l.fin.despesas.multiply(BigDecimal.valueOf(100)).divide(l.fin.receita, 0, RoundingMode.HALF_UP);
                d.add(new Destaque("aviso", "As despesas de " + l.nome + " consomem " + p.intValue() + "% das vendas (" + brl(l.fin.despesas) + ")."));
            }
            // 5) perdas de estoque
            if (l.fin.perdas.signum() > 0) {
                d.add(new Destaque("aviso", l.nome + " perdeu " + brl(l.fin.perdas) + " em quebras/ajustes negativos de estoque."));
            }
            // 6) materiais vendidos abaixo do custo (no máximo 2 por armazém)
            int n = 0;
            for (AnaliseFinanceira.LinhaMaterial lm : l.fin.porMaterial) {
                if (lm.lucro.signum() < 0 && n < 2) {
                    d.add(new Destaque("perigo", l.nome + " vendeu " + lm.material + " abaixo do custo médio: prejuízo de " + brl(lm.lucro.abs()) + "."));
                    n++;
                }
            }
            // 7) lucro variou forte contra o período anterior
            BigDecimal v = l.fin.variacaoLucroPercentual;
            if (v != null && v.compareTo(QUEDA_FORTE) <= 0) {
                d.add(new Destaque("aviso", "O lucro de " + l.nome + " caiu " + v.abs().setScale(0, RoundingMode.HALF_UP).intValue() + "% em relação ao período anterior."));
            } else if (v != null && v.compareTo(QUEDA_FORTE.negate()) >= 0) {
                d.add(new Destaque("bom", "O lucro de " + l.nome + " subiu " + v.setScale(0, RoundingMode.HALF_UP).intValue() + "% em relação ao período anterior."));
            }
            // 8) parado
            if ("SEM_MOVIMENTO".equals(l.situacao)) {
                d.add(new Destaque("info", l.nome + " não teve nenhuma movimentação no período."));
            }
        }

        // 9) mesmo material pago a preços diferentes (top 3 por volume)
        int mostrados = 0;
        for (MaterialLinha ml : r.materiais) {
            if (mostrados >= 3) break;
            if (ml.precoMedioCompra.size() < 2) continue;
            Long idMax = null, idMin = null;
            for (Map.Entry<Long, BigDecimal> en : ml.precoMedioCompra.entrySet()) {
                if (idMax == null || en.getValue().compareTo(ml.precoMedioCompra.get(idMax)) > 0) idMax = en.getKey();
                if (idMin == null || en.getValue().compareTo(ml.precoMedioCompra.get(idMin)) < 0) idMin = en.getKey();
            }
            BigDecimal pMax = ml.precoMedioCompra.get(idMax), pMin = ml.precoMedioCompra.get(idMin);
            if (pMax.signum() <= 0 || pMax.subtract(pMin).compareTo(pMax.multiply(DIFERENCA_PRECO)) < 0) continue;
            String nMax = nomeDe(r, idMax), nMin = nomeDe(r, idMin);
            BigDecimal dif = pMax.subtract(pMin).multiply(BigDecimal.valueOf(100)).divide(pMax, 0, RoundingMode.HALF_UP);
            d.add(new Destaque("info", ml.material + ": " + nMax + " paga " + brl(pMax) + "/kg e " + nMin + " paga " + brl(pMin)
                    + "/kg (" + dif.intValue() + "% a menos)."));
            mostrados++;
        }
        return d;
    }

    private static String nomeDe(Resultado r, Long id) {
        for (Linha l : r.armazens) if (l.id.equals(id)) return l.nome;
        return "?";
    }

    private static String extremo(Map<String, BigDecimal> mapa, boolean maior) {
        String achado = null;
        BigDecimal valor = null;
        for (Map.Entry<String, BigDecimal> e : mapa.entrySet()) {
            if (valor == null || (maior ? e.getValue().compareTo(valor) > 0 : e.getValue().compareTo(valor) < 0)) {
                valor = e.getValue();
                achado = e.getKey();
            }
        }
        return achado;
    }
}
