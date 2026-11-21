package com.reciclatech.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;

/**
 * Fase 31: regras da importação de preços por planilha (sem banco: fácil de testar).
 *
 * Formato: UMA planilha, primeira aba, com 3 colunas: Material | Unidade | Preço.
 *  - Unidade: KG ou UN (aceita também "kg", "quilo", "un", "unidade", "und"). Em branco: material já cadastrado usa a unidade do cadastro; material novo vira erro (Fase 32: antes virava KG).
 *  - Preço: 4,50 / 4.50 / R$ 4,50 / número do Excel.
 *  - A linha de títulos é achada pelos nomes (Material/Nome, Unidade, Preço/Valor); sem títulos, usa as colunas A, B, C.
 * Material já cadastrado é achado pelo nome (sem acento, maiúscula ou espaço extra). A planilha NUNCA apaga material
 * e NUNCA troca a unidade de um material existente.
 */
public final class ImportacaoPrecos {

    public enum Tipo { NOVO, ALTERAR, IGUAL, ERRO, IGNORADA }

    /** Fase 32: uma linha como veio da planilha (ou corrigida na tela de conferência). */
    public record Entrada(int linha, String nome, String unidade, String preco) {}

    /** Material como está hoje no armazém. */
    public record Atual(Long id, String nome, String unidade, BigDecimal preco) {}

    public static final class Item {
        public final int linha;               // número da linha na planilha (como o Excel mostra)
        public final Tipo tipo;
        public final String material;
        public final String unidade;
        public final BigDecimal precoAnterior;
        public final BigDecimal precoNovo;
        public final Long idExistente;
        public final boolean variacaoGrande;  // mais que o dobro ou menos da metade: exige confirmação
        public final String mensagem;
        public Entrada entrada;               // Fase 32: o que estava escrito (para corrigir na tela)

        Item(int linha, Tipo tipo, String material, String unidade, BigDecimal anterior, BigDecimal novo,
             Long idExistente, boolean variacaoGrande, String mensagem) {
            this.linha = linha; this.tipo = tipo; this.material = material; this.unidade = unidade;
            this.precoAnterior = anterior; this.precoNovo = novo; this.idExistente = idExistente;
            this.variacaoGrande = variacaoGrande; this.mensagem = mensagem;
        }
        public Entrada getEntrada() { return entrada; }
        public boolean isExistente() { return idExistente != null; }
        /** Nome para o campo da tela: o do cadastro (existente) ou o escrito. */
        public String getNomeCampo() { return entrada != null && entrada.nome() != null ? entrada.nome().trim() : material; }
        /** Unidade para o campo da tela: KG/UN reconhecida, ou vazio. */
        public String getUnidadeCampo() {
            if (unidade != null) return unidade;
            String u = entrada == null ? null : ImportacaoPrecos.unidade(entrada.unidade());
            return u == null || (entrada.unidade() == null || entrada.unidade().isBlank()) ? "" : u;
        }
        /** Preço para o campo da tela: 4,50 quando entendido; senão o texto como veio. */
        public String getPrecoCampo() {
            BigDecimal p = precoNovo != null ? precoNovo : (entrada == null ? null : ImportacaoPrecos.preco(entrada.preco()));
            if (p != null) return p.toPlainString().replace('.', ',');
            return entrada == null || entrada.preco() == null ? "" : entrada.preco().trim();
        }
        public int getLinha() { return linha; }
        public Tipo getTipo() { return tipo; }
        public String getMaterial() { return material; }
        public String getUnidade() { return unidade; }
        public BigDecimal getPrecoAnterior() { return precoAnterior; }
        public BigDecimal getPrecoNovo() { return precoNovo; }
        public boolean isVariacaoGrande() { return variacaoGrande; }
        public String getMensagem() { return mensagem; }
        /** Variação em % (só para ALTERAR). */
        public BigDecimal getVariacao() {
            if (precoAnterior == null || precoNovo == null || precoAnterior.signum() == 0) return null;
            return precoNovo.subtract(precoAnterior).multiply(BigDecimal.valueOf(100)).divide(precoAnterior, 1, RoundingMode.HALF_UP);
        }
    }

    public static final class Previa {
        public final List<Item> itens;
        public final int novos, alterados, iguais, erros, ignoradas, variacoesGrandes, foraDaPlanilha;
        Previa(List<Item> itens, int foraDaPlanilha) {
            this.itens = List.copyOf(itens);
            int n = 0, a = 0, i = 0, e = 0, g = 0, v = 0;
            for (Item it : itens) {
                switch (it.tipo) { case NOVO -> n++; case ALTERAR -> a++; case IGUAL -> i++; case ERRO -> e++; case IGNORADA -> g++; }
                if (it.variacaoGrande) v++;
            }
            novos = n; alterados = a; iguais = i; erros = e; ignoradas = g; variacoesGrandes = v; this.foraDaPlanilha = foraDaPlanilha;
        }
        public List<Item> getItens() { return itens; }
        public int getNovos() { return novos; }
        public int getAlterados() { return alterados; }
        public int getIguais() { return iguais; }
        public int getErros() { return erros; }
        public int getIgnoradas() { return ignoradas; }
        public int getVariacoesGrandes() { return variacoesGrandes; }
        public int getForaDaPlanilha() { return foraDaPlanilha; }
        public boolean temMudanca() { return novos + alterados > 0; }
        public boolean isTemMudanca() { return temMudanca(); }
    }

    private ImportacaoPrecos() {}

    // ------------------------------------------------------------------ campos

    public static String chave(String nome) {
        if (nome == null) return "";
        String s = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** "kg", "Quilo", "" -> "KG"; "un", "Unidade", "und" -> "UN"; outro -> nulo. */
    public static String unidade(String texto) {
        String u = chave(texto).replace(".", "");
        if (u.isEmpty() || u.equals("kg") || u.equals("kgs") || u.equals("quilo") || u.equals("quilos") || u.equals("kilo") || u.equals("quilograma")) return "KG";
        if (u.equals("un") || u.equals("und") || u.equals("unid") || u.equals("unidade") || u.equals("unidades") || u.equals("pc") || u.equals("peca")) return "UN";
        return null;
    }

    /** "R$ 1.234,50" -> 1234.50; "4.5" -> 4.50; "4,5" -> 4.50; inválido -> nulo. */
    public static BigDecimal preco(String texto) {
        if (texto == null) return null;
        String s = texto.trim().replace("R$", "").replace("r$", "").replace(" ", "").replace(" ", "");
        if (s.isEmpty()) return null;
        if (s.indexOf(',') >= 0) s = s.replace(".", "").replace(',', '.');      // padrão brasileiro
        else if (s.chars().filter(c -> c == '.').count() > 1) return null;         // "1.234.567" sem vírgula: ambíguo
        try {
            BigDecimal v = new BigDecimal(s);
            if (v.scale() > 4) v = v.setScale(4, RoundingMode.HALF_UP);            // números do Excel vêm com muitas casas
            v = v.setScale(2, RoundingMode.HALF_UP);
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ prévia

    /**
     * Compara a planilha com os materiais atuais. Nada é gravado aqui.
     * @param tabela linhas lidas da planilha (primeira aba)
     * @param atuais materiais do armazém hoje
     * @param precoMax maior preço aceito (Valores.PRECO_MAX)
     */
    public static Previa montar(List<List<String>> tabela, List<Atual> atuais, BigDecimal precoMax) {
        return montar(extrair(tabela), Set.of(), atuais, precoMax);
    }

    /** Fase 32: as linhas da planilha (sem as vazias), achando a linha de títulos. */
    public static List<Entrada> extrair(List<List<String>> tabela) {
        int inicio = 0, colNome = 0, colUn = 1, colPreco = 2;
        for (int i = 0; i < Math.min(5, tabela.size()); i++) {
            int[] cols = colunasDoTitulo(tabela.get(i));
            if (cols != null) { inicio = i + 1; colNome = cols[0]; colUn = cols[1]; colPreco = cols[2]; break; }
        }
        List<Entrada> lista = new ArrayList<>();
        for (int i = inicio; i < tabela.size(); i++) {
            List<String> l = tabela.get(i);
            String nomeBruto = celula(l, colNome), unBruta = colUn >= 0 ? celula(l, colUn) : "", precoBruto = celula(l, colPreco);
            if (nomeBruto.isBlank() && precoBruto.isBlank() && unBruta.isBlank()) continue; // linha vazia
            lista.add(new Entrada(i + 1, nomeBruto, unBruta, precoBruto));
        }
        return lista;
    }

    /**
     * Fase 32: monta a conferência a partir das linhas (da planilha ou corrigidas na tela).
     * {@code ignoradas} = números de linha que o dono desmarcou: aparecem, mas não são gravadas.
     */
    public static Previa montar(List<Entrada> entradas, Set<Integer> ignoradas, List<Atual> atuais, BigDecimal precoMax) {
        Map<String, Atual> porNome = new HashMap<>();
        for (Atual a : atuais) porNome.putIfAbsent(chave(a.nome()), a);

        List<Item> itens = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (Entrada en : entradas) {
            Item it = avaliar(en, ignoradas.contains(en.linha()), porNome, vistos, precoMax);
            it.entrada = en;
            itens.add(it);
        }
        if (itens.isEmpty()) throw new IllegalArgumentException("Não encontrei nenhum material na planilha. Use as colunas Material, Unidade e Preço.");
        if (itens.size() > PlanilhaLeitura.LINHAS_MAX - 1) throw new IllegalArgumentException("Materiais demais na planilha.");

        Set<Long> naPlanilha = new HashSet<>();
        for (Item it : itens) if (it.idExistente != null) naPlanilha.add(it.idExistente);
        int fora = 0;
        for (Atual a : atuais) if (!naPlanilha.contains(a.id())) fora++;
        return new Previa(itens, fora);
    }

    private static Item avaliar(Entrada en, boolean ignorada, Map<String, Atual> porNome, Set<String> vistos, BigDecimal precoMax) {
            int num = en.linha();
            String nomeBruto = en.nome() == null ? "" : en.nome(), unBruta = en.unidade() == null ? "" : en.unidade(),
                    precoBruto = en.preco() == null ? "" : en.preco();
            String nome = Valores.texto(nomeBruto, 100);
            if (ignorada) {
                Atual a = nome == null ? null : porNome.get(chave(nome));
                return new Item(num, Tipo.IGNORADA, nome == null ? nomeBruto.trim() : (a != null ? a.nome() : nome), unidade(unBruta) != null && !unBruta.isBlank() ? unidade(unBruta) : null,
                        a == null ? null : a.preco(), preco(precoBruto), a == null ? null : a.id(), false, "Desmarcada: não será gravada");
            }
            if (nome == null) return erro(num, nomeBruto, "Nome do material vazio ou com mais de 100 letras.");
            if (nome.startsWith("=") || nome.startsWith("+") || nome.startsWith("@")) return erro(num, nome, "Nome não pode começar com = + ou @.");
            String k = chave(nome);
            Atual atual = porNome.get(k);
            String unAtual = atual == null ? null : (atual.unidade() == null ? "KG" : atual.unidade().trim().toUpperCase(Locale.ROOT));

            // Fase 32: unidade em branco só vale para material que já existe (usa a do cadastro).
            // Material NOVO sem unidade virava KG sozinho: um "litro" (UN) ia para quilo e mudava o valor pago.
            String un;
            if (unBruta.isBlank()) {
                if (atual == null) return erro(num, nome, "Informe a unidade: KG (quilo) ou UN (unidade).");
                un = unAtual;
            } else {
                un = unidade(unBruta);
                if (un == null) return erro(num, nome, "Unidade \"" + unBruta.trim() + "\" não reconhecida. Use KG ou UN.");
            }
            BigDecimal preco = preco(precoBruto);
            if (precoBruto.isBlank()) return erro(num, nome, "Preço em branco. (Se for fórmula, abra e salve no Excel antes de enviar.)");
            if (preco == null || preco.signum() <= 0) return erro(num, nome, "Preço inválido: \"" + precoBruto.trim() + "\". Use um valor maior que zero, ex.: 4,50.");
            if (precoMax != null && preco.compareTo(precoMax) > 0) return erro(num, nome, "Preço acima do limite do sistema.");
            if (!vistos.add(k)) return erro(num, nome, "Material repetido na planilha (vale a primeira linha).");

            if (atual == null) return new Item(num, Tipo.NOVO, nome, un, null, preco, null, false, "Material novo");
            if (!unAtual.equals(un)) {
                return new Item(num, Tipo.ERRO, atual.nome(), un, atual.preco(), preco, atual.id(), false,
                        "Unidade diferente da cadastrada (" + unAtual + "). A planilha não troca a unidade.");
            }
            if (atual.preco() != null && atual.preco().compareTo(preco) == 0) {
                return new Item(num, Tipo.IGUAL, atual.nome(), un, atual.preco(), preco, atual.id(), false, "Sem mudança");
            }
            boolean grande = RegrasVenda.variacaoSuspeita(atual.preco(), preco);
            return new Item(num, Tipo.ALTERAR, atual.nome(), un, atual.preco(), preco, atual.id(), grande,
                    grande ? "Variação grande: confira" : "Preço alterado");
    }

    private static Item erro(int linha, String material, String msg) {
        return new Item(linha, Tipo.ERRO, material == null ? "" : material.trim(), null, null, null, null, false, msg);
    }

    private static String celula(List<String> l, int i) {
        return i >= 0 && i < l.size() && l.get(i) != null ? l.get(i) : "";
    }

    /** Linha de títulos? Devolve [nome, unidade, preço] (unidade pode ser -1). */
    static int[] colunasDoTitulo(List<String> linha) {
        int nome = -1, un = -1, preco = -1;
        for (int i = 0; i < linha.size(); i++) {
            String t = chave(linha.get(i));
            if (nome < 0 && (t.startsWith("material") || t.equals("nome") || t.startsWith("produto") || t.startsWith("item"))) nome = i;
            else if (un < 0 && (t.startsWith("unid") || t.equals("un") || t.equals("medida"))) un = i;
            else if (preco < 0 && (t.startsWith("preco") || t.startsWith("valor") || t.startsWith("r$"))) preco = i;
        }
        return nome >= 0 && preco >= 0 ? new int[]{nome, un, preco} : null;
    }
}
