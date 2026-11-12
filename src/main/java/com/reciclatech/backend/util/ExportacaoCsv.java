package com.reciclatech.backend.util;

import com.reciclatech.backend.service.AnaliseFinanceira;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * Gera o CSV do "Resultado do Período" para abrir direto no Excel/Planilhas (padrão brasileiro:
 * separador ";" e vírgula decimal). Textos que começam com = + - @ são neutralizados para
 * evitar que uma planilha execute o conteúdo como fórmula.
 */
public final class ExportacaoCsv {

    private ExportacaoCsv() {}

    /** Marca de ordem de bytes UTF-8: faz o Excel mostrar os acentos corretamente. */
    public static final String BOM = "﻿";
    private static final String QUEBRA = "\r\n";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static String resultado(AnaliseFinanceira.Resultado r) {
        StringBuilder sb = new StringBuilder(BOM);
        linha(sb, "Coletaê - Resultado do período");
        linha(sb, "Período", r.de.format(DATA), r.ate.format(DATA));
        linha(sb);

        linha(sb, "RESUMO");
        linha(sb, "Vendas", dinheiro(r.receita));
        linha(sb, "Custo do material vendido", dinheiro(r.custoVendido));
        linha(sb, "Lucro bruto", dinheiro(r.lucroBruto));
        linha(sb, "Perdas", dinheiro(r.perdas));
        linha(sb, "Despesas", dinheiro(r.despesas));
        linha(sb, "Lucro líquido", dinheiro(r.lucroLiquido));
        linha(sb, "Margem sobre as vendas (%)", r.margemPercentual == null ? "" : dinheiro(r.margemPercentual));
        linha(sb, "Pago aos catadores", dinheiro(r.compras));
        linha(sb, "Caixa do período", dinheiro(r.caixaPeriodo));
        linha(sb, "Atendimentos", String.valueOf(r.atendimentos));
        linha(sb, "Ticket médio", r.ticketMedio == null ? "" : dinheiro(r.ticketMedio));
        linha(sb);

        linha(sb, "LUCRO POR MATERIAL");
        linha(sb, "Material", "Quantidade vendida", "Custo médio", "Preço médio de venda", "Receita", "Custo", "Lucro", "Margem (%)");
        for (AnaliseFinanceira.LinhaMaterial l : r.porMaterial) {
            linha(sb, l.material, quantidade(l.qtdVendida), opcional(l.custoMedio), opcional(l.precoMedioVenda),
                    dinheiro(l.receita), dinheiro(l.custo), dinheiro(l.lucro), opcional(l.margemPercentual));
        }
        linha(sb);

        linha(sb, "ESTOQUE NO PÁTIO (até o fim do período)");
        linha(sb, "Material", "Quantidade", "Custo médio", "Valor a custo", "Revenda estimada");
        for (AnaliseFinanceira.LinhaEstoque e : r.estoque) {
            linha(sb, e.material, quantidade(e.qtd), dinheiro(e.custoMedio), dinheiro(e.valorCusto), opcional(e.valorRevenda));
        }
        linha(sb, "Total", "", "", dinheiro(r.estoqueValorCusto), dinheiro(r.estoqueValorRevenda));
        linha(sb);

        linha(sb, "MAIORES FORNECEDORES");
        linha(sb, "Fornecedor", "Valor pago", "Quantidade");
        for (AnaliseFinanceira.LinhaFornecedor f : r.topFornecedores) {
            linha(sb, f.nome, dinheiro(f.valorPago), quantidade(f.qtd));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ formatação

    static String dinheiro(BigDecimal v) {
        if (v == null) return "";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    static String quantidade(BigDecimal v) {
        if (v == null) return "";
        return v.setScale(3, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static String opcional(BigDecimal v) {
        return v == null ? "" : dinheiro(v);
    }

    /** Neutraliza fórmulas e aplica aspas quando o texto tem ; " ou quebra de linha. */
    static String celula(String texto) {
        if (texto == null) return "";
        String t = texto;
        if (!t.isEmpty() && "=+-@\t\r".indexOf(t.charAt(0)) >= 0) t = "'" + t;
        if (t.contains(";") || t.contains("\"") || t.contains("\n") || t.contains("\r")) {
            t = "\"" + t.replace("\"", "\"\"") + "\"";
        }
        return t;
    }

    private static void linha(StringBuilder sb, String... colunas) {
        for (int i = 0; i < colunas.length; i++) {
            if (i > 0) sb.append(';');
            String c = colunas[i];
            // valores numéricos negativos (ex.: "-10,00") não são fórmula: só texto livre é neutralizado
            sb.append(ehNumero(c) ? c : celula(c));
        }
        sb.append(QUEBRA);
    }

    private static boolean ehNumero(String c) {
        return c != null && c.matches("-?\\d+(,\\d+)?");
    }
}
