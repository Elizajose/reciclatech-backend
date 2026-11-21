package com.reciclatech.backend.util;

import com.reciclatech.backend.model.Oferta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Fase 33: atendimentos por funcionário num período (compras do balcão, status VENDIDO).
 * Um atendimento = um recibo (mesmo código de lote; vendas antigas sem código contam 1 por cliente por dia).
 * Quem atendeu: o id do funcionário gravado na venda (Fase 33); se não houver, o nome gravado (Fases 31–32);
 * sem nenhum dos dois, entra em "Sem registro (vendas antigas)".
 */
public final class DesempenhoEquipe {

    private DesempenhoEquipe() {}

    public static final String SEM_REGISTRO = "Sem registro (vendas antigas)";

    public static final class Linha {
        public final String chave;
        public String nome;
        public boolean funcionario;          // é um operador cadastrado (pelo id)
        public boolean ativo = true;         // operador ainda ativo
        public int atendimentos;
        public BigDecimal kg = BigDecimal.ZERO;
        public BigDecimal un = BigDecimal.ZERO;
        public BigDecimal total = BigDecimal.ZERO;
        public LocalDateTime ultimo;
        final Set<String> recibos = new HashSet<>();
        final Map<String, BigDecimal> porMaterial = new HashMap<>();
        public List<String> principais = List.of();
        public BigDecimal participacao = BigDecimal.ZERO;   // % do valor do período

        Linha(String chave, String nome) { this.chave = chave; this.nome = nome; }
        public String getChave() { return chave; }
        public String getNome() { return nome; }
        public boolean isFuncionario() { return funcionario; }
        public boolean isAtivo() { return ativo; }
        public int getAtendimentos() { return atendimentos; }
        public BigDecimal getKg() { return kg; }
        public BigDecimal getUn() { return un; }
        public BigDecimal getTotal() { return total; }
        public LocalDateTime getUltimo() { return ultimo; }
        public List<String> getPrincipais() { return principais; }
        public BigDecimal getParticipacao() { return participacao; }
        /** Valor médio pago por atendimento. */
        public BigDecimal getMedia() {
            return atendimentos == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(atendimentos), 2, RoundingMode.HALF_UP);
        }
    }

    public static final class Resultado {
        public final List<Linha> linhas;
        public final int atendimentos;
        public final BigDecimal kg, un, total;
        Resultado(List<Linha> linhas, int atendimentos, BigDecimal kg, BigDecimal un, BigDecimal total) {
            this.linhas = linhas; this.atendimentos = atendimentos; this.kg = kg; this.un = un; this.total = total;
        }
        public List<Linha> getLinhas() { return linhas; }
        public int getAtendimentos() { return atendimentos; }
        public BigDecimal getKg() { return kg; }
        public BigDecimal getUn() { return un; }
        public BigDecimal getTotal() { return total; }
    }

    /** Funcionário cadastrado: id, nome e se está ativo. */
    public record Funcionario(Long id, String nome, boolean ativo) {}

    /**
     * @param vendas      ofertas do armazém (qualquer status: só VENDIDO conta)
     * @param equipe      funcionários cadastrados (os sem atendimento aparecem com zero)
     * @param unidades    material -> "KG"/"UN" (nome exato do cadastro)
     */
    public static Resultado calcular(List<Oferta> vendas, List<Funcionario> equipe, Map<String, String> unidades,
                                     LocalDate de, LocalDate ate) {
        Map<String, Linha> porChave = new LinkedHashMap<>();
        Map<Long, Funcionario> porId = new HashMap<>();
        Map<String, Funcionario> porNome = new HashMap<>();
        for (Funcionario f : equipe) {
            porId.put(f.id(), f);
            if (f.nome() != null) porNome.putIfAbsent(chaveNome(f.nome()), f);
            Linha l = porChave.computeIfAbsent("F" + f.id(), k -> new Linha(k, f.nome()));
            l.funcionario = true;
            l.ativo = f.ativo();
        }
        for (Oferta o : vendas) {
            if (o == null || o.getStatus() != Oferta.StatusOferta.VENDIDO) continue;
            LocalDate d = Recibos.dataDa(o);
            if (d == null || d.isBefore(de) || d.isAfter(ate)) continue;

            Linha l;
            if (o.getFuncionarioId() != null) {
                Funcionario f = porId.get(o.getFuncionarioId());
                l = porChave.computeIfAbsent("F" + o.getFuncionarioId(), k -> new Linha(k, f != null ? f.nome() : nomeOu(o.getAtendente(), "Operador removido")));
                l.funcionario = true;
                if (f == null) l.ativo = false;
            } else if (o.getAtendente() != null && !o.getAtendente().isBlank()) {
                // Fases 31–32: só o nome. Se bater com um operador cadastrado, soma nele.
                Funcionario f = porNome.get(chaveNome(o.getAtendente()));
                l = f != null ? porChave.get("F" + f.id())
                        : porChave.computeIfAbsent("N" + chaveNome(o.getAtendente()), k -> new Linha(k, o.getAtendente().trim()));
            } else {
                l = porChave.computeIfAbsent("SEM", k -> new Linha(k, SEM_REGISTRO));
            }

            String recibo = Recibos.chaveDa(o) + "|" + (o.getUsuario() == null || o.getUsuario().getId() == null ? "?" : o.getUsuario().getId());
            l.recibos.add(recibo);
            BigDecimal q = o.getPeso() == null ? BigDecimal.ZERO : BigDecimal.valueOf(o.getPeso());
            if ("UN".equalsIgnoreCase(unidades.getOrDefault(o.getMaterial(), "KG"))) l.un = l.un.add(q); else l.kg = l.kg.add(q);
            BigDecimal v = o.getPrecoEstimado() == null ? BigDecimal.ZERO : o.getPrecoEstimado();
            l.total = l.total.add(v);
            if (o.getMaterial() != null) l.porMaterial.merge(o.getMaterial().trim(), v, BigDecimal::add);
            LocalDateTime quando = o.getCriadoEm() != null ? o.getCriadoEm() : d.atStartOfDay();
            if (l.ultimo == null || quando.isAfter(l.ultimo)) l.ultimo = quando;
        }

        int atend = 0;
        BigDecimal kg = BigDecimal.ZERO, un = BigDecimal.ZERO, total = BigDecimal.ZERO;
        List<Linha> linhas = new ArrayList<>();
        for (Linha l : porChave.values()) {
            l.atendimentos = l.recibos.size();
            // operador desativado e sem movimento no período não precisa aparecer
            if (l.atendimentos == 0 && (!l.funcionario || !l.ativo)) continue;
            l.principais = l.porMaterial.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .limit(3).map(Map.Entry::getKey).toList();
            atend += l.atendimentos; kg = kg.add(l.kg); un = un.add(l.un); total = total.add(l.total);
            linhas.add(l);
        }
        for (Linha l : linhas) {
            l.participacao = total.signum() == 0 ? BigDecimal.ZERO
                    : l.total.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
        }
        linhas.sort(Comparator.comparing((Linha l) -> l.total).reversed()
                .thenComparing(l -> -l.atendimentos)
                .thenComparing(l -> l.nome == null ? "" : l.nome.toLowerCase(Locale.ROOT)));
        return new Resultado(linhas, atend, kg, un, total);
    }

    private static String nomeOu(String n, String padrao) { return n == null || n.isBlank() ? padrao : n.trim(); }

    static String chaveNome(String n) { return n == null ? "" : n.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
}
