package com.reciclatech.backend.service;

import com.reciclatech.backend.model.*;
import com.reciclatech.backend.repository.*;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regras da fase 17 testadas sem Spring e sem banco: repositórios falsos feitos com java.lang.reflect.Proxy.
 * Cada teste monta seu próprio cenário (nada é compartilhado entre testes).
 */
class DatabaseServiceRegrasTest {

    // ------------------------------------------------------------------ infraestrutura dos testes

    private Armazem arm;
    private final List<Oferta> ofertas = new ArrayList<>();
    private final List<Oferta> salvas = new ArrayList<>();
    private final List<Material> materiais = new ArrayList<>();
    private final List<Despesa> despesas = new ArrayList<>();
    private final List<CaixaDiario> caixas = new ArrayList<>();
    private final List<Usuario> usuarios = new ArrayList<>();
    private final List<HistoricoAlteracao> historico = new ArrayList<>();
    private final Map<String, Object> sessao = new HashMap<>();
    private long seq = 100;

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> c, Map<String, Function<Object[], Object>> h) {
        return (T) Proxy.newProxyInstance(c.getClassLoader(), new Class<?>[]{c}, (p, m, a) -> {
            Function<Object[], Object> f = h.get(m.getName());
            if (f != null) return f.apply(a == null ? new Object[0] : a);
            Class<?> r = m.getReturnType();
            if (r == List.class) return new ArrayList<>();
            if (r == Optional.class) return Optional.empty();
            if (r == boolean.class) return false;
            if (r == long.class) return 0L;
            if (r == int.class) return 0;
            return null;
        });
    }

    private static void injetar(Object alvo, String campo, Object valor) throws Exception {
        Field f = alvo.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(alvo, valor);
    }

    private DatabaseService preparar() throws Exception {
        arm = new Armazem();
        arm.setId(1L);
        sessao.put("idPlanilhaAtiva", "1");
        DatabaseService s = new DatabaseService();
        injetar(s, "session", fake(HttpSession.class, Map.of(
                "getAttribute", a -> sessao.get(a[0]),
                "setAttribute", a -> { sessao.put((String) a[0], a[1]); return null; })));
        injetar(s, "armazemRepository", fake(ArmazemRepository.class, Map.of(
                "findById", a -> Optional.of(arm), "travarPorId", a -> Optional.of(arm))));
        injetar(s, "usuarioRepository", fake(UsuarioRepository.class, Map.of(
                "findById", a -> usuarios.stream().filter(u -> u.getId().equals(a[0])).findFirst(),
                "findAllByTelefoneAndArmazemIdOrderByIdAsc", a -> new ArrayList<>(usuarios.stream().filter(u -> Objects.equals(u.getTelefone(), a[0])).toList()),
                "save", a -> { Usuario u = (Usuario) a[0]; if (u.getId() == null) { u.setId(++seq); usuarios.add(u); } return u; })));
        injetar(s, "materialRepository", fake(MaterialRepository.class, Map.of(
                "findAllByArmazemIdOrderByNomeAsc", a -> new ArrayList<>(materiais),
                "save", a -> { Material m = (Material) a[0]; if (m.getId() == null) { m.setId(++seq); materiais.add(m); } return m; },
                "delete", a -> { materiais.remove(a[0]); return null; },
                "findById", a -> materiais.stream().filter(m -> m.getId().equals(a[0])).findFirst())));
        injetar(s, "ofertaRepository", fake(OfertaRepository.class, Map.of(
                "findAllByArmazemIdOrderByDataCriacaoDesc", a -> new ArrayList<>(ofertas),
                "travarPorUsuariosEStatus", a -> new ArrayList<>(ofertas.stream().filter(o -> o.getUsuario() != null
                        && ((Collection<?>) a[0]).contains(o.getUsuario().getId()) && ((Collection<?>) a[1]).contains(o.getStatus())).toList()),
                "findAllByUsuarioIdAndStatusIn", a -> new ArrayList<>(ofertas.stream().filter(o -> o.getUsuario() != null
                        && o.getUsuario().getId().equals(a[0]) && ((Collection<?>) a[1]).contains(o.getStatus())).toList()),
                "save", a -> { Oferta o = (Oferta) a[0]; salvas.add(o); if (!ofertas.contains(o)) ofertas.add(o); return o; },
                "somarEntradasPorMaterial", a -> { double t = ofertas.stream().filter(o -> a[1].equals(o.getMaterial()) && (o.getStatus() == Oferta.StatusOferta.VENDIDO || o.getStatus() == Oferta.StatusOferta.AJUSTE_POSITIVO)).mapToDouble(Oferta::getPeso).sum(); return t == 0 ? null : t; },
                "somarSaidasPorMaterial", a -> { double t = ofertas.stream().filter(o -> a[1].equals(o.getMaterial()) && (o.getStatus() == Oferta.StatusOferta.SAIDA_INDUSTRIA || o.getStatus() == Oferta.StatusOferta.AJUSTE_NEGATIVO)).mapToDouble(Oferta::getPeso).sum(); return t == 0 ? null : t; })));
        injetar(s, "despesaRepository", fake(DespesaRepository.class, Map.of(
                "findAllByArmazemIdAndDataDespesa", a -> new ArrayList<>(despesas.stream().filter(d -> d.getDataDespesa().equals(a[1])).toList()),
                "save", a -> { despesas.add((Despesa) a[0]); return a[0]; })));
        injetar(s, "caixaDiarioRepository", fake(CaixaDiarioRepository.class, Map.of(
                "save", a -> { caixas.add((CaixaDiario) a[0]); return a[0]; })));
        injetar(s, "precoVipRepository", fake(PrecoVipRepository.class, Map.of()));
        injetar(s, "historicoRepository", fake(HistoricoAlteracaoRepository.class, Map.of(
                "save", a -> { historico.add((HistoricoAlteracao) a[0]); return a[0]; })));
        injetar(s, "parceiroRepository", fake(ParceiroRepository.class, Map.of()));
        injetar(s, "funcionarioRepository", fake(FuncionarioRepository.class, Map.of()));
        injetar(s, "passwordEncoder", new org.springframework.security.crypto.password.PasswordEncoder() {
            public String encode(CharSequence r) { return "$2x$" + r; }
            public boolean matches(CharSequence r, String e) { return e.equals("$2x$" + r); }
        });
        return s;
    }

    private interface Acao { void rodar() throws Exception; }

    /** A ação tem de ser recusada com IllegalArgumentException (mensagem pronta para a tela). */
    private static void recusa(String porque, Acao a) {
        try {
            a.rodar();
        } catch (IllegalArgumentException esperado) {
            return;
        } catch (Exception outra) {
            fail("Esperava IllegalArgumentException (" + porque + ") mas veio " + outra);
        }
        fail("Deveria ter sido recusado: " + porque);
    }

    private static String hoje() { return LocalDate.now(ZoneId.of("America/Recife")).toString(); }

    private Material material(String nome, String preco) {
        Material m = new Material();
        m.setId(++seq); m.setNome(nome); m.setUnidade("KG"); m.setPrecoPorKg(new BigDecimal(preco)); m.setArmazem(arm);
        materiais.add(m);
        return m;
    }

    private Usuario cliente(String nome) {
        Usuario u = new Usuario();
        u.setId(++seq); u.setNome(nome); u.setArmazem(arm);
        usuarios.add(u);
        return u;
    }

    private Oferta pedido(Usuario u, Oferta.StatusOferta st) {
        Oferta o = new Oferta();
        o.setUsuario(u); o.setStatus(st); o.setArmazem(arm);
        ofertas.add(o);
        return o;
    }

    private Oferta movimento(String mat, double peso, String preco, Oferta.StatusOferta st, String data) {
        Oferta o = new Oferta();
        o.setMaterial(mat); o.setPeso(peso); o.setPrecoEstimado(new BigDecimal(preco)); o.setStatus(st); o.setData(data); o.setArmazem(arm);
        ofertas.add(o);
        return o;
    }

    private List<List<Object>> lote(Usuario u, String mat, double peso, String preco, String totalMentiroso) {
        return new ArrayList<>(List.of(new ArrayList<>(Arrays.asList("ID1", mat, peso, "ENTREGA NO LOCAL", new BigDecimal(preco),
                new BigDecimal(totalMentiroso), hoje(), u.getId().toString(), "VENDIDO", "NÃO INFORMADO"))));
    }

    // ------------------------------------------------------------------ finalização: o servidor não confia no navegador

    @Test
    void precoMuitoAcimaDaTabelaERecusadoENadaEGravado() throws Exception {
        DatabaseService s = preparar();
        material("Latinha", "8.00");
        Usuario c = cliente("Ana");
        pedido(c, Oferta.StatusOferta.EM_ATENDIMENTO);

        recusa("preço 2,5x a tabela", () -> s.finalizarAtendimento(c.getId().toString(), null, lote(c, "Latinha", 10, "20.00", "200")));
        assertTrue(salvas.stream().noneMatch(o -> o.getStatus() == Oferta.StatusOferta.VENDIDO));
    }

    @Test
    void pesoPrecoNegativoOuZeroEMaterialInexistenteSaoRecusados() throws Exception {
        DatabaseService s = preparar();
        material("Latinha", "8.00");
        Usuario c = cliente("Ana");
        pedido(c, Oferta.StatusOferta.EM_ATENDIMENTO);

        recusa("preço negativo", () -> s.finalizarAtendimento(c.getId().toString(), null, lote(c, "Latinha", 10, "-1.00", "200")));
        recusa("peso zero", () -> s.finalizarAtendimento(c.getId().toString(), null, lote(c, "Latinha", 0, "8.00", "200")));
        recusa("material de outro armazém", () -> s.finalizarAtendimento(c.getId().toString(), null, lote(c, "Fantasma", 5, "8.00", "40")));
    }

    @Test
    void precoNegociadoDentroDoTetoGravaETotalEORecalculadoNoServidor() throws Exception {
        DatabaseService s = preparar();
        material("Latinha", "8.00");
        Usuario c = cliente("Ana");
        pedido(c, Oferta.StatusOferta.EM_ATENDIMENTO);

        boolean gravou = s.finalizarAtendimento(c.getId().toString(), null, lote(c, "Latinha", 10, "9.50", "999999"));

        assertTrue(gravou);
        Oferta venda = salvas.stream().filter(o -> o.getStatus() == Oferta.StatusOferta.VENDIDO).findFirst().orElse(null);
        assertNotNull(venda);
        assertEquals(0, new BigDecimal("95.00").compareTo(venda.getPrecoEstimado()), "o total tem de ser preço x peso, não o valor enviado");
    }

    // ------------------------------------------------------------------ Fase 31: um recibo por atendimento

    @Test
    void cadaAtendimentoGanhaSeuProprioReciboComHoraEAtendente() throws Exception {
        DatabaseService s = preparar();
        sessao.put("atendenteNome", "Luiz");
        material("Latinha", "8.00");
        Usuario c = cliente("Ana");

        pedido(c, Oferta.StatusOferta.EM_ATENDIMENTO);
        String r1 = s.finalizarAtendimentoComRecibo(c.getId().toString(), null, lote(c, "Latinha", 10, "8.00", "80"));
        pedido(c, Oferta.StatusOferta.EM_ATENDIMENTO);   // o mesmo cliente volta no mesmo dia
        String r2 = s.finalizarAtendimentoComRecibo(c.getId().toString(), null, lote(c, "Latinha", 5, "8.00", "40"));

        assertNotNull(r1);
        assertNotNull(r2);
        assertFalse(r1.equals(r2), "cada atendimento tem o seu código");
        List<DatabaseService.ReciboDoDia> dia = s.recibosDoDia(LocalDate.now(ZoneId.of("America/Recife")));
        assertEquals(2, dia.size(), "dois recibos separados, não um somando tudo");
        for (DatabaseService.ReciboDoDia r : dia) {
            assertEquals("Luiz", r.recibo.atendente);
            assertNotNull(r.recibo.quando);
        }
        BigDecimal soma = dia.get(0).recibo.total.add(dia.get(1).recibo.total);
        assertEquals(0, new BigDecimal("120.00").compareTo(soma));
        // atendimento já finalizado: nada é gravado de novo
        assertNull(s.finalizarAtendimentoComRecibo(c.getId().toString(), null, lote(c, "Latinha", 5, "8.00", "40")));
    }

    // ------------------------------------------------------------------ Fase 31: importação de preços (tudo ou nada, com histórico)

    @Test
    void importacaoCriaAlteraERegistraNoHistorico() throws Exception {
        DatabaseService s = preparar();
        Material latinha = material("Latinha", "8.00");

        int[] r = s.aplicarImportacaoPrecos(
                List.<String[]>of(new String[]{"Cobre", "KG", "38.00"}),
                List.<Object[]>of(new Object[]{latinha.getId(), new BigDecimal("9.00")}));

        assertEquals(1, r[0]);
        assertEquals(1, r[1]);
        assertEquals(0, new BigDecimal("9.00").compareTo(latinha.getPrecoPorKg()));
        assertTrue(materiais.stream().anyMatch(m -> "Cobre".equals(m.getNome()) && "KG".equals(m.getUnidade())));
        assertTrue(historico.stream().anyMatch(h -> HistoricoAlteracao.CRIADO.equals(h.getAcao())));
        assertTrue(historico.stream().anyMatch(h -> HistoricoAlteracao.PRECO_ALTERADO.equals(h.getAcao())));
    }

    @Test
    void importacaoRecusaMaterialDeOutroArmazemENomeRepetido() throws Exception {
        DatabaseService s = preparar();
        material("Latinha", "8.00");
        recusa("material que não é deste armazém",
                () -> s.aplicarImportacaoPrecos(List.of(), List.<Object[]>of(new Object[]{999999L, new BigDecimal("1.00")})));
        recusa("nome já existe", () -> s.aplicarImportacaoPrecos(List.<String[]>of(new String[]{"latinha", "KG", "1.00"}), List.of()));
    }

    // ------------------------------------------------------------------ ajuste de estoque

    @Test
    void ajusteSoAceitaPositivoENegativoComPesoMotivoESaldo() throws Exception {
        DatabaseService s = preparar();
        material("Latinha", "8.00");
        movimento("Latinha", 10, "80", Oferta.StatusOferta.VENDIDO, hoje());

        recusa("status VENDIDO", () -> s.salvarAjusteEstoque("Latinha", 5.0, "VENDIDO", "x"));
        recusa("status SAIDA_INDUSTRIA", () -> s.salvarAjusteEstoque("Latinha", 5.0, "SAIDA_INDUSTRIA", "x"));
        recusa("peso negativo", () -> s.salvarAjusteEstoque("Latinha", -5.0, "AJUSTE_POSITIVO", "x"));
        recusa("baixa maior que o saldo", () -> s.salvarAjusteEstoque("Latinha", 20.0, "AJUSTE_NEGATIVO", "quebra"));
        recusa("sem motivo", () -> s.salvarAjusteEstoque("Latinha", 2.0, "AJUSTE_NEGATIVO", "  "));
        recusa("material inexistente", () -> s.salvarAjusteEstoque("Outro", 2.0, "AJUSTE_POSITIVO", "achei"));
        assertTrue(salvas.isEmpty(), "nenhuma recusa pode gravar");

        s.salvarAjusteEstoque("latinha ", 4.0, "AJUSTE_NEGATIVO", "quebra");
        assertTrue(salvas.stream().anyMatch(o -> o.getStatus() == Oferta.StatusOferta.AJUSTE_NEGATIVO && "Latinha".equals(o.getMaterial())));
        assertEquals(1, historico.size());
        assertEquals("ESTOQUE", historico.get(0).getEntidade());
    }

    // ------------------------------------------------------------------ materiais

    @Test
    void materialRepetidoPrecoInvalidoEExclusaoComSaldoSaoRecusados() throws Exception {
        DatabaseService s = preparar();
        Material lat = material("Latinha", "8.00");
        movimento("Latinha", 10, "80", Oferta.StatusOferta.VENDIDO, hoje());

        Material dup = new Material();
        dup.setNome("  LATINHA "); dup.setUnidade("KG"); dup.setPrecoPorKg(new BigDecimal("5"));
        recusa("nome repetido", () -> s.salvarMaterial(dup));
        Material neg = new Material();
        neg.setNome("Papel"); neg.setUnidade("KG"); neg.setPrecoPorKg(new BigDecimal("-1"));
        recusa("preço negativo", () -> s.salvarMaterial(neg));
        recusa("preço zero na atualização", () -> s.atualizarPrecoMaterial(lat.getId(), BigDecimal.ZERO));
        recusa("excluir com saldo", () -> s.deletarMaterial(lat.getId()));

        ofertas.clear(); // sem saldo, pode excluir
        s.deletarMaterial(lat.getId());
        assertTrue(materiais.isEmpty());
    }

    // ------------------------------------------------------------------ despesas e caixa

    @Test
    void despesaInvalidaERecusada() throws Exception {
        DatabaseService s = preparar();
        recusa("valor negativo", () -> s.salvarDespesa("Luz", new BigDecimal("-10")));
        recusa("valor zero", () -> s.salvarDespesa("Luz", BigDecimal.ZERO));
        recusa("sem descrição", () -> s.salvarDespesa("  ", new BigDecimal("10")));
        s.salvarDespesa("Luz", new BigDecimal("20"));
        assertEquals(1, despesas.size());
    }

    @Test
    void fechamentoDeCaixaECalculadoNoServidor() throws Exception {
        DatabaseService s = preparar();
        material("Latinha", "8.00");
        Material un = material("Garrafa", "1.00");
        un.setUnidade("UN");
        movimento("Latinha", 10, "80", Oferta.StatusOferta.VENDIDO, hoje());
        movimento("Garrafa", 7, "7", Oferta.StatusOferta.VENDIDO, hoje());
        movimento("Latinha", 5, "150", Oferta.StatusOferta.SAIDA_INDUSTRIA, hoje());
        movimento("Latinha", 99, "999", Oferta.StatusOferta.VENDIDO, "2020-01-01");
        Despesa d = new Despesa();
        d.setValor(new BigDecimal("20")); d.setDataDespesa(LocalDate.parse(hoje()));
        despesas.add(d);

        s.fecharCaixaDoDia(hoje());

        CaixaDiario cx = caixas.get(0);
        assertEquals(0, new BigDecimal("150").compareTo(cx.getEntradas()), "entradas = vendas à indústria do dia");
        assertEquals(0, new BigDecimal("87").compareTo(cx.getSaidas()), "saídas = compras do dia (dia antigo fica de fora)");
        assertEquals(0, new BigDecimal("20").compareTo(cx.getDespesas()));
        assertEquals(0, new BigDecimal("43").compareTo(cx.getLucroLiquido()), "150 - 87 - 20");
        assertTrue(Math.abs(cx.getPesoComprado() - 10.0) < 1e-9, "só itens em KG entram no peso");
    }

    @Test
    void fechamentoComDataFuturaAntigaOuInvalidaERecusado() throws Exception {
        DatabaseService s = preparar();
        recusa("futura", () -> s.fecharCaixaDoDia("2999-01-01"));
        recusa("muito antiga", () -> s.fecharCaixaDoDia("2001-01-01"));
        recusa("inválida", () -> s.fecharCaixaDoDia("lixo"));
        assertTrue(caixas.isEmpty());
    }

    // ------------------------------------------------------------------ atendimento e formulário público

    @Test
    void doisOperadoresNaoAtendemOMesmoClienteSemAviso() throws Exception {
        DatabaseService s = preparar();
        Usuario cl = cliente("Beto");
        Oferta of = pedido(cl, Oferta.StatusOferta.DISPONIVEL);

        sessao.put("atendenteNome", "Maria");
        DatabaseService.InicioAtendimento r1 = s.iniciarAtendimento(cl.getId().toString(), false);
        assertTrue(r1.ok);
        assertEquals(Oferta.StatusOferta.EM_ATENDIMENTO, of.getStatus());
        assertEquals("Maria", of.getAtendente());

        sessao.put("atendenteNome", "João");
        DatabaseService.InicioAtendimento r2 = s.iniciarAtendimento(cl.getId().toString(), false);
        assertFalse(r2.ok);
        assertEquals("Maria", r2.ocupadoPor);
        assertEquals("Maria", of.getAtendente(), "o segundo operador não toma o atendimento sem confirmar");

        assertTrue(s.iniciarAtendimento(cl.getId().toString(), true).ok);
        assertEquals("João", of.getAtendente());
        assertTrue(s.iniciarAtendimento(cl.getId().toString(), false).ok, "quem já atende pode continuar");

        of.setStatus(Oferta.StatusOferta.FINALIZADO);
        DatabaseService.InicioAtendimento r4 = s.iniciarAtendimento(cl.getId().toString(), false);
        assertFalse(r4.ok);
        assertNull(r4.ocupadoPor);
        assertFalse(s.temPedidoAberto(cl.getId().toString()));
    }

    @Test
    void pedidoPublicoNaoSobrescreveNomeEEnderecoDeClienteExistente() throws Exception {
        DatabaseService s = preparar();
        Usuario ex = cliente("Nome Verdadeiro");
        ex.setTelefone("81999990000");
        ex.setEndereco("Rua Certa");
        Usuario intruso = new Usuario();
        intruso.setNome("OUTRO NOME");
        intruso.setTelefone("81999990000");

        s.salvarSolicitacaoInicial(intruso, "Rua Nova", "1");

        assertEquals("Nome Verdadeiro", ex.getNome());
        assertEquals("Rua Certa", ex.getEndereco());
        assertEquals(1, salvas.size());
        assertEquals("Rua Nova", salvas.get(0).getEndereco(), "o pedido novo guarda o endereço informado");
        assertSame(ex, salvas.get(0).getUsuario());
    }

    // ------------------------------------------------------------------ saída para a indústria

    @Test
    void saidaValidaEstoqueDocumentoETipoDoDestinatario() throws Exception {
        DatabaseService s = preparar();
        Material lt = material("Latinha", "8.00");
        movimento("Latinha", 10, "80", Oferta.StatusOferta.VENDIDO, hoje());

        recusa("maior que o estoque", () -> s.registrarSaidaIndustria("ACME", null, Map.of(lt.getId(), new BigDecimal("50")), Map.of(lt.getId(), new BigDecimal("10"))));
        recusa("sem preço", () -> s.registrarSaidaIndustria("ACME", null, Map.of(lt.getId(), new BigDecimal("5")), Map.of()));
        recusa("CNPJ inválido", () -> s.registrarSaidaIndustria("ACME", "123", Map.of(lt.getId(), new BigDecimal("5")), Map.of(lt.getId(), new BigDecimal("10"))));
        recusa("peso negativo", () -> s.registrarSaidaIndustria("ACME", null, Map.of(lt.getId(), new BigDecimal("-5")), Map.of(lt.getId(), new BigDecimal("10"))));
        assertTrue(usuarios.isEmpty(), "saída recusada não cria destinatário");

        Long id = s.registrarSaidaIndustria("acme ltda", null, Map.of(lt.getId(), new BigDecimal("5")), Map.of(lt.getId(), new BigDecimal("10")));

        Usuario dest = usuarios.get(0);
        assertEquals(Usuario.TipoUsuario.INDUSTRIA, dest.getTipo());
        assertEquals("ACME LTDA (SAÍDA)", dest.getNome());
        assertNull(dest.getTelefone(), "sem telefone inventado a partir da hora");
        assertEquals(id, dest.getId());
        Oferta saida = salvas.stream().filter(o -> o.getStatus() == Oferta.StatusOferta.SAIDA_INDUSTRIA).findFirst().orElse(null);
        assertNotNull(saida);
        assertEquals(0, new BigDecimal("50.00").compareTo(saida.getPrecoEstimado()));
        assertSame(dest, saida.getUsuario());
    }

    // ------------------------------------------------------------------ equipe e master

    @Test
    void operadorExigeNomeUsuarioSemEspacoESenhaMinima() throws Exception {
        DatabaseService s = preparar();
        recusa("senha curta", () -> s.salvarFuncionario("Zé", "ze", "123"));
        recusa("usuário com espaço", () -> s.salvarFuncionario("Zé", "z e", "123456"));
        recusa("sem nome", () -> s.salvarFuncionario("  ", "ze", "123456"));
        s.salvarFuncionario("Zé", "ze", "123456");
    }

    @Test
    void statusDeArmazemSoAceitaOsQuatroConhecidos() throws Exception {
        DatabaseService s = preparar();
        recusa("texto livre", () -> s.atualizarStatusArmazem(1L, "QUALQUER COISA"));
        recusa("vazio", () -> s.atualizarStatusArmazem(1L, ""));
    }

    // ------------------------------------------------------------------ fase 18: trava de variação de preço

    @Test void precoComVariacaoGrandeExigeConfirmacaoENaoGravaAntes() throws Exception {
        DatabaseService s = preparar();
        Material m = new Material();
        m.setId(5L); m.setNome("Latinha"); m.setUnidade("KG"); m.setPrecoPorKg(new BigDecimal("8.00")); m.setArmazem(arm);
        materiais.add(m);

        // 8 -> 800: recusado até o dono confirmar; o preço continua 8
        assertThrows(VariacaoPrecoException.class, () -> s.atualizarPrecoMaterial(5L, new BigDecimal("800"), false));
        assertEquals(0, new BigDecimal("8.00").compareTo(m.getPrecoPorKg()));

        // mudança normal (8 -> 9) passa direto
        s.atualizarPrecoMaterial(5L, new BigDecimal("9.00"), false);
        assertEquals(0, new BigDecimal("9.00").compareTo(m.getPrecoPorKg()));

        // com a confirmação do dono, o valor grande é gravado
        s.atualizarPrecoMaterial(5L, new BigDecimal("30.00"), true);
        assertEquals(0, new BigDecimal("30.00").compareTo(m.getPrecoPorKg()));
    }
}
