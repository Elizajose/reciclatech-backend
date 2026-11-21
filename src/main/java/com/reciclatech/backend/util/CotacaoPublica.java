package com.reciclatech.backend.util;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fase 27: regras da cotação pública da página inicial (sem banco, sem Spring: fácil de testar).
 *
 * O catador escolhe primeiro a região (Estado e cidade). Só então aparecem os armazéns daquela cidade,
 * em ordem alfabética (neutra). A visão "por material" mostra quem paga mais por um material na cidade.
 * Tudo é devolvido em mapas e listas simples, prontos para virar JSON.
 */
public final class CotacaoPublica {

    /** Valor de "Estado" para armazéns antigos que não informaram a região. */
    public static final String SEM_REGIAO = "__SEM__";
    public static final int POR_PAGINA = 12;
    /** Quantos materiais o cartão do armazém mostra antes do "Ver todos". */
    public static final int PRINCIPAIS = 5;

    private CotacaoPublica() {}

    /** Um preço de material de um armazém. {@code atualizadoEm} = última vez que o preço foi criado ou alterado (pode ser nulo). */
    public record Preco(String nome, String unidade, BigDecimal preco, LocalDate atualizadoEm) {
        public String chaveMaterial() { return CotacaoPublica.chaveMaterial(nome, unidade); }
        public String unidadeNormalizada() { return CotacaoPublica.unidade(unidade); }
    }

    /** Um armazém com acesso ativo e os seus preços. */
    public record ArmazemCot(Long id, String nome, String uf, String cidade, List<Preco> precos) {
        public ArmazemCot {
            precos = precos == null ? List.of() : precos.stream()
                    .filter(p -> p != null && p.nome() != null && !p.nome().isBlank()
                            && p.preco() != null && p.preco().signum() > 0)
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    // ------------------------------------------------------------------ normalização

    /** Texto para comparar: sem acento, minúsculo, espaços simples. "  São   José " -> "sao jose". */
    public static String chave(String texto) {
        if (texto == null) return "";
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static String unidade(String u) {
        String s = u == null ? "" : u.trim().toUpperCase(Locale.ROOT);
        return s.isEmpty() ? "KG" : s;
    }

    /** O mesmo material só é comparado com a mesma unidade: "cobre|KG" é diferente de "cobre|UN". */
    public static String chaveMaterial(String nome, String unidade) {
        return chave(nome) + "|" + unidade(unidade);
    }

    public static String uf(String uf) {
        return uf == null ? "" : uf.trim().toUpperCase(Locale.ROOT);
    }

    static boolean temRegiao(ArmazemCot a) {
        return !uf(a.uf()).isEmpty() && !chave(a.cidade()).isEmpty();
    }

    /** O armazém está na região pedida? {@code uf = SEM_REGIAO} pega os que não têm Estado ou cidade. */
    public static boolean naRegiao(ArmazemCot a, String uf, String cidadeChave) {
        if (SEM_REGIAO.equals(uf)) return !temRegiao(a);
        if (!temRegiao(a)) return false;
        return uf(a.uf()).equals(uf(uf)) && chave(a.cidade()).equals(chave(cidadeChave));
    }

    /** Dias desde a última atualização (0 = hoje). Nulo quando não se sabe. */
    public static Integer dias(LocalDate data, LocalDate hoje) {
        if (data == null || hoje == null) return null;
        long d = ChronoUnit.DAYS.between(data, hoje);
        return (int) Math.max(0, d);
    }

    private static final Comparator<ArmazemCot> POR_NOME =
            Comparator.comparing((ArmazemCot a) -> chave(a.nome())).thenComparing(a -> a.id() == null ? Long.MAX_VALUE : a.id());

    // ------------------------------------------------------------------ regiões

    /** Estados e cidades que têm pelo menos um armazém ativo, com a contagem de armazéns. */
    public static Map<String, Object> regioes(List<ArmazemCot> armazens) {
        // uf -> (chave da cidade -> nomes originais)
        Map<String, Map<String, List<String>>> mapa = new TreeMap<>();
        int semRegiao = 0;
        for (ArmazemCot a : armazens) {
            if (!temRegiao(a)) { semRegiao++; continue; }
            mapa.computeIfAbsent(uf(a.uf()), k -> new TreeMap<>())
                .computeIfAbsent(chave(a.cidade()), k -> new ArrayList<>())
                .add(a.cidade().trim().replaceAll("\\s+", " "));
        }
        List<Map<String, Object>> estados = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<String>>> e : mapa.entrySet()) {
            List<Map<String, Object>> cidades = new ArrayList<>();
            for (Map.Entry<String, List<String>> c : e.getValue().entrySet()) {
                Map<String, Object> cid = new LinkedHashMap<>();
                cid.put("chave", c.getKey());
                cid.put("nome", nomeMaisUsado(c.getValue()));
                cid.put("armazens", c.getValue().size());
                cidades.add(cid);
            }
            Map<String, Object> est = new LinkedHashMap<>();
            est.put("uf", e.getKey());
            est.put("cidades", cidades);
            estados.add(est);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("estados", estados);
        r.put("semRegiao", semRegiao);
        r.put("total", armazens.size());
        return r;
    }

    /**
     * Entre grafias do mesmo nome ("Salgueiro", "SALGUEIRO", "salgueiro"), fica a mais usada.
     * Empate: a que mistura maiúsculas e minúsculas ("Salgueiro"); depois a primeira em ordem alfabética.
     */
    static String nomeMaisUsado(Collection<String> nomes) {
        Map<String, Integer> cont = new TreeMap<>();
        for (String n : nomes) cont.merge(n, 1, Integer::sum);
        String melhor = null;
        int max = -1;
        boolean melhorMisto = false;
        for (Map.Entry<String, Integer> e : cont.entrySet()) {
            boolean misto = !e.getKey().equals(e.getKey().toUpperCase(Locale.ROOT))
                    && !e.getKey().equals(e.getKey().toLowerCase(Locale.ROOT));
            if (e.getValue() > max || (e.getValue() == max && misto && !melhorMisto)) {
                melhor = e.getKey(); max = e.getValue(); melhorMisto = misto;
            }
        }
        return melhor;
    }

    // ------------------------------------------------------------------ materiais da cidade

    private record ContMaterial(String chave, String unidade, List<String> nomes) {}

    /** Materiais com preço na região, do mais comum (mais armazéns) para o menos; empate em ordem alfabética. */
    static List<ContMaterial> contarMateriais(List<ArmazemCot> daRegiao) {
        Map<String, ContMaterial> mapa = new HashMap<>();
        for (ArmazemCot a : daRegiao) {
            Set<String> vistos = new HashSet<>(); // o mesmo armazém conta uma vez por material
            for (Preco p : a.precos()) {
                String k = p.chaveMaterial();
                if (!vistos.add(k)) continue;
                mapa.computeIfAbsent(k, x -> new ContMaterial(k, p.unidadeNormalizada(), new ArrayList<>()))
                    .nomes().add(p.nome().trim().replaceAll("\\s+", " "));
            }
        }
        List<ContMaterial> lista = new ArrayList<>(mapa.values());
        lista.sort(Comparator.comparingInt((ContMaterial c) -> -c.nomes().size()).thenComparing(ContMaterial::chave));
        return lista;
    }

    public static Map<String, Object> materiais(List<ArmazemCot> armazens, String uf, String cidade) {
        List<ArmazemCot> daRegiao = armazens.stream().filter(a -> naRegiao(a, uf, cidade)).collect(Collectors.toList());
        List<Map<String, Object>> lista = new ArrayList<>();
        for (ContMaterial c : contarMateriais(daRegiao)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chave", c.chave());
            m.put("nome", nomeMaisUsado(c.nomes()));
            m.put("unidade", c.unidade());
            m.put("armazens", c.nomes().size());
            lista.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("materiais", lista);
        r.put("armazens", daRegiao.size());
        return r;
    }

    // ------------------------------------------------------------------ armazéns da cidade

    /**
     * Armazéns da região, em ordem alfabética, de {@link #POR_PAGINA} em {@link #POR_PAGINA}.
     * {@code busca} filtra pelo nome do armazém (sem acento, em qualquer parte do nome).
     * Os materiais de cada armazém vêm com os mais comuns da cidade primeiro: o cartão mostra os 5 primeiros.
     */
    public static Map<String, Object> armazens(List<ArmazemCot> armazens, String uf, String cidade,
                                               String busca, int pagina, LocalDate hoje) {
        List<ArmazemCot> daRegiao = armazens.stream().filter(a -> naRegiao(a, uf, cidade)).sorted(POR_NOME)
                .collect(Collectors.toList());
        String termo = chave(busca);
        List<ArmazemCot> filtrados = termo.isEmpty() ? daRegiao
                : daRegiao.stream().filter(a -> chave(a.nome()).contains(termo)).collect(Collectors.toList());

        // posição de cada material na cidade (mais comum = 0)
        Map<String, Integer> posicao = new HashMap<>();
        List<ContMaterial> comuns = contarMateriais(daRegiao);
        for (int i = 0; i < comuns.size(); i++) posicao.put(comuns.get(i).chave(), i);

        // Fase 33: maior preço de cada material na região (o cartão marca quem paga mais, igual ao "Quem paga mais")
        Map<String, BigDecimal> maiorPreco = new HashMap<>();
        Map<String, Integer> quantos = new HashMap<>();
        for (ArmazemCot a : daRegiao) {
            Map<String, BigDecimal> doArmazem = new HashMap<>();
            for (Preco p : a.precos()) doArmazem.merge(p.chaveMaterial(), p.preco(), BigDecimal::max);
            doArmazem.forEach((k, v) -> { maiorPreco.merge(k, v, BigDecimal::max); quantos.merge(k, 1, Integer::sum); });
        }

        int pag = Math.max(0, pagina);
        int inicio = (int) Math.min((long) pag * POR_PAGINA, filtrados.size());
        int fim = Math.min(inicio + POR_PAGINA, filtrados.size());

        List<Map<String, Object>> itens = new ArrayList<>();
        for (ArmazemCot a : filtrados.subList(inicio, fim)) {
            List<Preco> ordem = new ArrayList<>(a.precos());
            ordem.sort(Comparator.comparingInt((Preco p) -> posicao.getOrDefault(p.chaveMaterial(), Integer.MAX_VALUE))
                    .thenComparing(p -> chave(p.nome())));
            List<Map<String, Object>> mats = new ArrayList<>();
            LocalDate ultima = null;
            for (Preco p : ordem) {
                Map<String, Object> pj = precoJson(p, hoje);
                // só marca quando há disputa (2+ armazéns com o material) e este é o maior preço
                pj.put("melhor", quantos.getOrDefault(p.chaveMaterial(), 0) >= 2
                        && p.preco().compareTo(maiorPreco.get(p.chaveMaterial())) == 0);
                mats.add(pj);
                if (p.atualizadoEm() != null && (ultima == null || p.atualizadoEm().isAfter(ultima))) ultima = p.atualizadoEm();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(a.id()));
            item.put("nome", a.nome());
            item.put("dias", dias(ultima, hoje));
            item.put("materiais", mats);
            itens.add(item);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalRegiao", daRegiao.size());
        r.put("total", filtrados.size());
        r.put("pagina", pag);
        r.put("porPagina", POR_PAGINA);
        r.put("temMais", fim < filtrados.size());
        r.put("principais", PRINCIPAIS);
        r.put("itens", itens);
        return r;
    }

    private static Map<String, Object> precoJson(Preco p, LocalDate hoje) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chave", p.chaveMaterial());
        m.put("nome", p.nome().trim());
        m.put("unidade", p.unidadeNormalizada());
        m.put("preco", p.preco());
        m.put("dias", dias(p.atualizadoEm(), hoje));
        return m;
    }

    // ------------------------------------------------------------------ ranking por material

    /**
     * Quem paga mais por um material na região (maior preço primeiro; empate em ordem alfabética).
     * {@code melhor = true} em todos que empatam no maior preço.
     */
    public static Map<String, Object> ranking(List<ArmazemCot> armazens, String uf, String cidade,
                                              String materialChave, LocalDate hoje) {
        String alvo = materialChave == null ? "" : materialChave.trim();
        List<ArmazemCot> daRegiao = armazens.stream().filter(a -> naRegiao(a, uf, cidade)).collect(Collectors.toList());

        record Linha(ArmazemCot armazem, Preco preco) {}
        List<Linha> linhas = new ArrayList<>();
        List<String> nomes = new ArrayList<>();
        for (ArmazemCot a : daRegiao) {
            // se o mesmo armazém tiver o material duas vezes (grafias parecidas), vale o maior preço
            a.precos().stream().filter(p -> p.chaveMaterial().equals(alvo))
                    .max(Comparator.comparing(Preco::preco))
                    .ifPresent(p -> { linhas.add(new Linha(a, p)); nomes.add(p.nome().trim().replaceAll("\\s+", " ")); });
        }
        linhas.sort(Comparator.comparing((Linha l) -> l.preco().preco()).reversed()
                .thenComparing(l -> chave(l.armazem().nome()))
                .thenComparing(l -> l.armazem().id() == null ? Long.MAX_VALUE : l.armazem().id()));

        BigDecimal maior = linhas.isEmpty() ? null : linhas.get(0).preco().preco();
        List<Map<String, Object>> itens = new ArrayList<>();
        int posicao = 0;
        for (Linha l : linhas) {
            posicao++;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("posicao", posicao);
            m.put("id", String.valueOf(l.armazem().id()));
            m.put("nome", l.armazem().nome());
            m.put("preco", l.preco().preco());
            m.put("unidade", l.preco().unidadeNormalizada());
            m.put("dias", dias(l.preco().atualizadoEm(), hoje));
            m.put("melhor", maior != null && l.preco().preco().compareTo(maior) == 0);
            itens.add(m);
        }
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("chave", alvo);
        material.put("nome", nomes.isEmpty() ? null : nomeMaisUsado(nomes));
        material.put("unidade", linhas.isEmpty() ? null : linhas.get(0).preco().unidadeNormalizada());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("material", material);
        r.put("itens", itens);
        return r;
    }
}
