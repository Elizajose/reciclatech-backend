package com.reciclatech.backend.util;

import com.reciclatech.backend.model.Oferta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Fase 31: separa as vendas de um cliente em recibos (um por atendimento).
 * Antes, o recibo somava TODAS as compras do cliente (quem voltava pelo mesmo telefone via as compras antigas no recibo novo).
 * Vendas novas têm "lote"; as antigas (sem lote) são agrupadas pelo dia.
 */
public final class Recibos {

    private Recibos() {}

    public static final class Grupo {
        public final String chave;          // lote (8 letras) ou "D2026-11-21" para vendas antigas
        public final String lote;           // nulo nas antigas
        public final LocalDate data;
        public final LocalDateTime quando;  // nulo nas antigas
        public final String atendente;      // nulo nas antigas
        public final List<Oferta> itens;
        public final BigDecimal total;

        Grupo(String chave, String lote, LocalDate data, LocalDateTime quando, String atendente, List<Oferta> itens, BigDecimal total) {
            this.chave = chave; this.lote = lote; this.data = data; this.quando = quando;
            this.atendente = atendente; this.itens = itens; this.total = total;
        }
        public String getChave() { return chave; }
        public String getLote() { return lote; }
        public String getCodigo() { return lote == null ? null : CodigoRecibo.formatar(lote); }
        public LocalDate getData() { return data; }
        public LocalDateTime getQuando() { return quando; }
        public String getAtendente() { return atendente; }
        public List<Oferta> getItens() { return itens; }
        public BigDecimal getTotal() { return total; }
        public int getQuantidadeItens() { return itens.size(); }
    }

    public static LocalDate dataDa(Oferta o) {
        if (o.getCriadoEm() != null) return o.getCriadoEm().toLocalDate();
        if (o.getData() != null) {
            try { return LocalDate.parse(o.getData().trim()); } catch (RuntimeException ignorado) { /* formato antigo */ }
        }
        return o.getDataCriacao();
    }

    public static String chaveDa(Oferta o) {
        if (o.getLote() != null && !o.getLote().isBlank()) return o.getLote();
        LocalDate d = dataDa(o);
        return "D" + (d == null ? "?" : d.toString());
    }

    /** Recibos do mais novo para o mais antigo. */
    public static List<Grupo> agrupar(List<Oferta> vendas) {
        Map<String, List<Oferta>> porChave = new LinkedHashMap<>();
        for (Oferta o : vendas) {
            if (o == null) continue;
            porChave.computeIfAbsent(chaveDa(o), k -> new ArrayList<>()).add(o);
        }
        List<Grupo> grupos = new ArrayList<>();
        for (Map.Entry<String, List<Oferta>> e : porChave.entrySet()) {
            List<Oferta> itens = e.getValue();
            itens.sort(Comparator.comparing(o -> o.getId() == null ? Long.MAX_VALUE : o.getId()));
            Oferta primeira = itens.get(0);
            BigDecimal total = BigDecimal.ZERO;
            LocalDateTime quando = null;
            String atendente = null;
            for (Oferta o : itens) {
                if (o.getPrecoEstimado() != null) total = total.add(o.getPrecoEstimado());
                if (o.getCriadoEm() != null && (quando == null || o.getCriadoEm().isBefore(quando))) quando = o.getCriadoEm();
                if (atendente == null && o.getAtendente() != null && !o.getAtendente().isBlank()) atendente = o.getAtendente();
            }
            String lote = primeira.getLote() != null && !primeira.getLote().isBlank() ? primeira.getLote() : null;
            grupos.add(new Grupo(e.getKey(), lote, dataDa(primeira), quando, atendente, itens, total));
        }
        grupos.sort(Comparator.comparing((Grupo g) -> g.data == null ? LocalDate.MIN : g.data).reversed()
                .thenComparing((Grupo g) -> g.quando == null ? LocalDateTime.MIN : g.quando, Comparator.reverseOrder())
                .thenComparing((Grupo g) -> maiorId(g), Comparator.reverseOrder()));
        return grupos;
    }

    private static long maiorId(Grupo g) {
        long m = Long.MIN_VALUE;
        for (Oferta o : g.itens) if (o.getId() != null && o.getId() > m) m = o.getId();
        return m;
    }

    /**
     * Fase 33: o operador só vê os recibos que ele mesmo atendeu.
     * Vale quem tem o id do funcionário gravado na venda (a partir da Fase 33). Para os recibos das Fases 31–32
     * (com código, mas sem o id), vale o nome do atendente gravado. Vendas antigas sem código não aparecem para operador.
     */
    public static boolean doAtendente(Grupo g, Long funcionarioId, String nome) {
        if (g == null) return false;
        boolean algumId = false;
        for (Oferta o : g.itens) {
            if (o.getFuncionarioId() != null) {
                algumId = true;
                if (o.getFuncionarioId().equals(funcionarioId)) return true;
            }
        }
        if (algumId || g.lote == null || nome == null || nome.isBlank() || g.atendente == null) return false;
        return g.atendente.trim().equalsIgnoreCase(nome.trim());
    }

    /** "Maria da Silva (SAÍDA)" -> "Maria S." (verificação pública: sem o nome completo). */
    public static String nomeResumido(String nome) {
        if (nome == null) return "";
        String limpo = nome.replace("(SAÍDA)", "").trim().replaceAll("\\s+", " ");
        if (limpo.isEmpty()) return "";
        String[] p = limpo.split(" ");
        String primeiro = p[0].substring(0, 1).toUpperCase(Locale.ROOT) + p[0].substring(1).toLowerCase(Locale.ROOT);
        if (p.length == 1) return primeiro;
        String ultimo = p[p.length - 1];
        return primeiro + " " + ultimo.substring(0, 1).toUpperCase(Locale.ROOT) + ".";
    }

    /** O recibo pedido (pela chave) ou, sem chave, o mais recente. Vazio se não existir. */
    public static Optional<Grupo> escolher(List<Grupo> grupos, String chave) {
        if (grupos.isEmpty()) return Optional.empty();
        if (chave == null || chave.isBlank()) return Optional.of(grupos.get(0));
        return grupos.stream().filter(g -> g.chave.equalsIgnoreCase(chave.trim())).findFirst();
    }
}
