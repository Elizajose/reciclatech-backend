package com.reciclatech.backend.service;

import java.math.BigDecimal;
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
    }

    public static final class MaterialLinha {
        public String material;
        public BigDecimal kgTotal = BigDecimal.ZERO;
        public Map<Long, BigDecimal> kgPorArmazem = new LinkedHashMap<>();
        public Long armazemQueMaisComprou;
        public Long armazemQueMenosComprou;   // só entre os que compraram algo
    }

    public static final class Resultado {
        public LocalDate de, ate;
        public List<Linha> armazens = new ArrayList<>();       // ordenados por lucro líquido, do maior ao menor
        public List<MaterialLinha> materiais = new ArrayList<>(); // do mais ao menos comprado (kg, todos os armazéns)
        public BigDecimal lucroTotal = BigDecimal.ZERO;
        public BigDecimal receitaTotal = BigDecimal.ZERO;
        public BigDecimal comprasTotal = BigDecimal.ZERO;
        public BigDecimal kgTotal = BigDecimal.ZERO;
    }

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
                MaterialLinha ml = porMaterial.computeIfAbsent(mat, k -> { MaterialLinha x = new MaterialLinha(); x.material = k; return x; });
                ml.kgTotal = ml.kgTotal.add(m.peso);
                ml.kgPorArmazem.merge(e.id, m.peso, BigDecimal::add);
            }
            if (!l.kgPorMaterial.isEmpty()) {
                l.maisComprado = extremo(l.kgPorMaterial, true);
                if (l.kgPorMaterial.size() > 1) l.menosComprado = extremo(l.kgPorMaterial, false);
            }
            r.lucroTotal = r.lucroTotal.add(l.fin.lucroLiquido);
            r.receitaTotal = r.receitaTotal.add(l.fin.receita);
            r.comprasTotal = r.comprasTotal.add(l.fin.compras);
            r.kgTotal = r.kgTotal.add(l.kgComprado);
            r.armazens.add(l);
        }

        r.armazens.sort((a, b) -> b.fin.lucroLiquido.compareTo(a.fin.lucroLiquido));
        // destaque de maior/menor lucro só faz sentido com 2+ armazéns que tiveram movimento e lucros diferentes
        List<Linha> comMov = new ArrayList<>();
        for (Linha l : r.armazens) if (l.temMovimento) comMov.add(l);
        if (comMov.size() >= 2) {
            Linha melhor = comMov.get(0), pior = comMov.get(comMov.size() - 1);
            if (melhor.fin.lucroLiquido.compareTo(pior.fin.lucroLiquido) != 0) { melhor.maiorLucro = true; pior.menorLucro = true; }
        }

        for (MaterialLinha ml : porMaterial.values()) {
            BigDecimal max = null, min = null;
            for (Map.Entry<Long, BigDecimal> en : ml.kgPorArmazem.entrySet()) {
                if (max == null || en.getValue().compareTo(max) > 0) { max = en.getValue(); ml.armazemQueMaisComprou = en.getKey(); }
                if (min == null || en.getValue().compareTo(min) < 0) { min = en.getValue(); ml.armazemQueMenosComprou = en.getKey(); }
            }
            if (ml.kgPorArmazem.size() < 2 || (max != null && max.compareTo(min) == 0)) {
                ml.armazemQueMenosComprou = null; // sem comparação possível
            }
            r.materiais.add(ml);
        }
        r.materiais.sort((a, b) -> b.kgTotal.compareTo(a.kgTotal));
        return r;
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
