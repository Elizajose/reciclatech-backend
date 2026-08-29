package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.*;
import com.reciclatech.backend.repository.*;
import com.reciclatech.backend.service.DatabaseService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class TelaController {

    @Autowired(required = false) private OfertaRepository ofertaRepository;
    @Autowired(required = false) private UsuarioRepository usuarioRepository;
    @Autowired(required = false) private MaterialRepository materialRepository;

    // A MÁGICA AQUI: Conectando com o novo serviço de banco de dados
    @Autowired private DatabaseService googleSheetsService;

    // VARIÁVEL DE AMBIENTE: Puxa a senha Master do sistema operacional
    @Value("${SENHA_MASTER:Sertao2026}")
    private String senhaMasterConfigurada;

    @GetMapping("/")
    public String home(Model model) {
        try {
            model.addAttribute("listaArmazens", googleSheetsService.obterCotacoesPublicas());
        } catch (Exception e) {
            model.addAttribute("listaArmazens", new ArrayList<>());
        }
        return "index";
    }

    // ==========================================
    // SISTEMA DE CADASTRO E LOGIN
    // ==========================================
    @GetMapping("/cadastro")
    public String telaCadastro() {
        return "cadastro";
    }

    @PostMapping("/salvar-cadastro")
    public String salvarNovoCadastro(@RequestParam String nomeArmazem, @RequestParam String nomeProprietario,
                                     @RequestParam(required = false) String cnpj, @RequestParam String telefone,
                                     @RequestParam String endereco, @RequestParam String login, @RequestParam String senha,
                                     @RequestParam(required = false, defaultValue = "START") String planoEscolhido) {
        try {
            boolean sucesso = googleSheetsService.cadastrarParceiroSaaS(nomeArmazem, nomeProprietario, cnpj, telefone, endereco, login, senha, planoEscolhido);

            if (sucesso) {
                // Se deu certo, manda pra tela de login com aviso de sucesso
                return "redirect:/login?sucesso=cadastro";
            } else {
                // Se o login já existir, devolve pra tela de cadastro com aviso
                return "redirect:/cadastro?erro=usuario_existe";
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Se der erro no banco, devolve com erro genérico
            return "redirect:/cadastro?erro=true";
        }
    }

    @GetMapping("/login") public String telaLogin() { return "login-admin"; }

    @PostMapping("/login-admin")
    public String login(@RequestParam String login, @RequestParam String senha, HttpSession session) {

        // ==========================================
        // INTERCEPTADOR MASTER (Acesso Secreto Seguro)
        // Usa a variável de ambiente injetada no topo da classe
        // ==========================================
        if ("master".equals(login) && senhaMasterConfigurada.equals(senha)) {
            session.setAttribute("masterLogado", true);
            return "redirect:/master/painel";
        }
        // ==========================================

        try {
            Map<String, String> dadosUser = googleSheetsService.autenticarSaaS(login, senha);
            if (dadosUser != null) {
                session.setAttribute("adminLogado", true);
                session.setAttribute("perfilUser", dadosUser.get("perfil"));
                session.setAttribute("idPlanilhaAtiva", dadosUser.get("idPlanilha"));
                session.setAttribute("nomeArmazem", dadosUser.getOrDefault("nomeArmazem", "Armazém Parceiro"));
                session.setAttribute("cnpjArmazem", dadosUser.getOrDefault("cnpj", ""));
                session.setAttribute("senhaFinanceira", dadosUser.getOrDefault("senhaFinanceira", "admin123"));

                // DADOS DA ASSINATURA E BLOQUEIO (TRIAL 7 DIAS)
                String status = dadosUser.getOrDefault("status", "ATIVO");
                String plano = dadosUser.getOrDefault("plano", "START");
                String dataCadastroStr = dadosUser.get("data_cadastro");

                session.setAttribute("planoAssinatura", plano);
                session.setAttribute("statusAssinatura", status);

                if ("BLOQUEADO".equals(status)) {
                    session.setAttribute("bloqueadoPagamento", true);
                    return "redirect:/admin/assinatura";
                }

                if ("TRIAL".equals(status) && dataCadastroStr != null) {
                    try {
                        LocalDate dataCadastro = LocalDate.parse(dataCadastroStr);
                        LocalDate hoje = LocalDate.now(ZoneId.of("America/Recife"));
                        long diasUso = java.time.temporal.ChronoUnit.DAYS.between(dataCadastro, hoje);

                        if (diasUso > 7) {
                            session.setAttribute("bloqueadoPagamento", true);
                            return "redirect:/admin/assinatura";
                        }
                    } catch (Exception e) {} // Ignora se a data estiver mal formatada e deixa entrar
                }

                session.removeAttribute("bloqueadoPagamento"); // Libera acesso se tudo estiver OK
                return "redirect:/admin/coletas";
            }
        } catch (RuntimeException e) { return "redirect:/login?erro=suspenso"; }
        catch (Exception e) { e.printStackTrace(); }
        return "redirect:/login?erro=true";
    }

    @GetMapping("/sair") public String logout(HttpSession session) {
        session.invalidate(); return "redirect:/login";
    }

    @PostMapping("/publicar")
    public String solicitarColeta(@RequestParam String endereco, @RequestParam String nomeVendedor,
                                  @RequestParam String telefoneVendedor, @RequestParam String idPlanilhaDestino) {
        String zapLimpo = telefoneVendedor.replaceAll("\\D", "");
        Usuario novo = new Usuario(); novo.setNome(nomeVendedor); novo.setTelefone(zapLimpo); novo.setEndereco(endereco); novo.setTipo(Usuario.TipoUsuario.CATADOR);
        try { googleSheetsService.salvarSolicitacaoInicial(novo, endereco, idPlanilhaDestino); }
        catch (IOException e) { System.err.println("Erro ao publicar: " + e.getMessage()); }
        return "redirect:/?sucesso=true";
    }

    // ==========================================
    // ROTAS DO SISTEMA (BLOQUEADAS SE VENCIDO)
    // ==========================================

    @GetMapping("/admin/coletas")
    public String telaListaColetas(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        session.removeAttribute("gestorAutorizado");

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        try {
            model.addAttribute("usuarios", googleSheetsService.buscarUsuariosComColetasPendentes());
        } catch (IOException e) {
            model.addAttribute("usuarios", new ArrayList<>());
        }
        return "admin-lista-coletas";
    }

    @GetMapping("/admin/atender/{idUsuario}")
    public String telaChecklist(@PathVariable String idUsuario, Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache"); response.setDateHeader("Expires", 0);
        try {
            googleSheetsService.marcarComoEmAtendimento(idUsuario);
            Usuario vendedor = googleSheetsService.listarUsuarios().stream().filter(u -> u.getTelefone().equals(idUsuario) || u.getId().toString().equals(idUsuario)).findFirst().orElseThrow();
            model.addAttribute("vendedor", vendedor);
            model.addAttribute("todosMateriais", googleSheetsService.listarMateriais());
            model.addAttribute("precosVip", googleSheetsService.buscarPrecosEspeciais(vendedor.getTelefone())); // PARÊNTESE CORRIGIDO
        } catch (Exception e) { return "redirect:/admin/coletas?erro=usuario"; }
        return "admin-checklist";
    }

    @GetMapping("/admin/cancelar/{id}")
    public String cancelarColeta(@PathVariable String id, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        try { googleSheetsService.marcarSolicitacaoComoConcluida(id); } catch (Exception e) { }
        return "redirect:/admin/coletas";
    }

    @PostMapping("/admin/revisar-coleta")
    public String revisarColeta(@RequestParam String idVendedor, @RequestParam Map<String, String> params, Model model, HttpSession session) {
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try {
            Usuario vendedor = googleSheetsService.listarUsuarios().stream().filter(u -> u.getTelefone().equals(idVendedor) || u.getId().toString().equals(idVendedor)).findFirst().orElseThrow();
            List<PreVendaDTO> itensRevisao = new ArrayList<>();
            BigDecimal totalEstimado = BigDecimal.ZERO;
            List<Material> todosMateriais = googleSheetsService.listarMateriais();
            Map<String, BigDecimal> precosVip = googleSheetsService.buscarPrecosEspeciais(vendedor.getTelefone());

            for (String key : params.keySet()) {
                if (key.startsWith("qtd_") && !params.get(key).isEmpty()) {
                    try {
                        Long idMaterial = Long.parseLong(key.replace("qtd_", ""));
                        Double quantidade = Double.parseDouble(params.get(key).replace(",", "."));
                        if (quantidade > 0) {
                            Material mat = todosMateriais.stream().filter(m -> m.getId().equals(idMaterial)).findFirst().orElse(null);
                            if (mat != null) {
                                BigDecimal precoBase = precosVip.containsKey(mat.getNome()) ? precosVip.get(mat.getNome()) : mat.getPrecoPorKg();
                                BigDecimal totalItem = precoBase.multiply(BigDecimal.valueOf(quantidade));
                                mat.setPrecoPorKg(precoBase);
                                itensRevisao.add(new PreVendaDTO(mat, quantidade, totalItem));
                                totalEstimado = totalEstimado.add(totalItem);
                            }
                        }
                    } catch (Exception e) {}
                }
            }
            model.addAttribute("vendedor", vendedor); model.addAttribute("itens", itensRevisao); model.addAttribute("totalEstimado", totalEstimado);
            return "admin-revisao";
        } catch (Exception e) { return "redirect:/admin/coletas?erro=planilha"; }
    }

    @PostMapping("/admin/confirmar-finalizacao")
    public String confirmarFinalizacao(@RequestParam String idVendedor, @RequestParam(required = false) String cpfFinal,
                                       @RequestParam(required = false) List<Long> idsMateriais, @RequestParam(required = false) List<Double> pesosFinais,
                                       @RequestParam(required = false) List<Double> precosFinais, HttpSession session) {
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try {
            if (idsMateriais == null || idsMateriais.isEmpty()) return "redirect:/admin/coletas?erro=sem_materiais";
            List<Material> todosMateriais = googleSheetsService.listarMateriais();
            List<List<Object>> loteDeVendas = new ArrayList<>();

            for (int i = 0; i < idsMateriais.size(); i++) {
                Long idMat = idsMateriais.get(i);
                Material mat = todosMateriais.stream().filter(m -> m.getId().equals(idMat)).findFirst().orElseThrow(() -> new RuntimeException("Erro mat"));
                BigDecimal precoUn = BigDecimal.valueOf(precosFinais.get(i));
                BigDecimal total = precoUn.multiply(BigDecimal.valueOf(pesosFinais.get(i)));

                String idVendaUnico = UUID.randomUUID().toString().substring(0, 13).toUpperCase();

                List<Object> row = Arrays.asList(
                        idVendaUnico, mat.getNome(), pesosFinais.get(i).toString().replace(".", ","), "ENTREGA NO LOCAL",
                        precoUn.toString().replace(".", ","), total.toString().replace(".", ","), LocalDate.now(ZoneId.of("America/Recife")).toString(),
                        idVendedor, "VENDIDO", cpfFinal != null && !cpfFinal.trim().isEmpty() ? cpfFinal : "NÃO INFORMADO"
                );
                loteDeVendas.add(row);
            }
            googleSheetsService.registrarVendasEmLote(loteDeVendas);
            googleSheetsService.atualizarCpfUsuario(idVendedor, cpfFinal);
            googleSheetsService.marcarSolicitacaoComoConcluida(idVendedor);
            return "redirect:/extrato/" + idVendedor;
        } catch (Exception e) { return "redirect:/admin/coletas?erro=venda"; }
    }

    @GetMapping("/extrato/{id}")
    public String gerarExtratoIndividual(@PathVariable String id, Model model) {
        try {
            Usuario usuario = googleSheetsService.listarUsuarios().stream().filter(u -> u.getTelefone().equals(id) || u.getId().toString().equals(id)).findFirst().orElse(null);
            if (usuario == null) return "redirect:/";

            List<Oferta> vendasReais = googleSheetsService.buscarVendasPorUsuario(id);
            BigDecimal totalGeral = vendasReais.stream().map(Oferta::getPrecoEstimado).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Material> mats = googleSheetsService.listarMateriais();
            model.addAttribute("mapaUnidades", mats.stream().collect(Collectors.toMap(Material::getNome, Material::getUnidade)));
            model.addAttribute("mapaPrecos", mats.stream().collect(Collectors.toMap(Material::getNome, Material::getPrecoPorKg)));
            model.addAttribute("vendedor", usuario); model.addAttribute("vendas", vendasReais); model.addAttribute("total", totalGeral); model.addAttribute("dataHoje", LocalDate.now(ZoneId.of("America/Recife")));
        } catch (Exception e) { return "redirect:/?erro=extrato"; }
        return "extrato";
    }

    public static class PreVendaDTO {
        public Material material; public Double peso; public BigDecimal total;
        public PreVendaDTO(Material m, Double p, BigDecimal t) { this.material = m; this.peso = p; this.total = t; }
    }

    public static class RankingDTO {
        public String nome; public Double peso; public String unidade;
        public RankingDTO(String n, Double p, String u) { this.nome = n; this.peso = p; this.unidade = u; }
    }

    @GetMapping("/meus-extratos")
    public String listaExtratosDoDia(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        try { model.addAttribute("usuarios", googleSheetsService.buscarUsuariosComVendasHoje()); }
        catch (IOException e) { model.addAttribute("usuarios", new ArrayList<>()); }
        return "lista-extratos";
    }

    @PostMapping("/admin/pesagem-rapida")
    public String pesagemRapida(@RequestParam String nome, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        Long idGerado = System.currentTimeMillis();
        Usuario novoAvulso = new Usuario(); novoAvulso.setId(idGerado); novoAvulso.setNome(nome); novoAvulso.setTelefone(idGerado.toString()); novoAvulso.setEndereco("Atendimento Avulso"); novoAvulso.setTipo(Usuario.TipoUsuario.CATADOR);
        try { googleSheetsService.salvarSolicitacaoInicial(novoAvulso, novoAvulso.getEndereco()); return "redirect:/admin/atender/" + idGerado; }
        catch (IOException e) { return "redirect:/admin/coletas?erro=pesagem_rapida"; }
    }

    @GetMapping("/admin/historico")
    public String historicoMovimentacoes(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        if (session.getAttribute("gestorAutorizado") == null) return "redirect:/admin/analises/autenticar";

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache"); response.setDateHeader("Expires", 0);

        try { model.addAttribute("historico", googleSheetsService.getHistoricoCompleto()); }
        catch (Exception e) { model.addAttribute("historico", new ArrayList<>()); }
        return "admin-historico";
    }

    @GetMapping("/admin/despesas")
    public String telaDespesas(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        if (session.getAttribute("gestorAutorizado") == null) return "redirect:/admin/analises/autenticar";

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache"); response.setDateHeader("Expires", 0);

        try { model.addAttribute("despesas", googleSheetsService.listarDespesas()); }
        catch (Exception e) { model.addAttribute("despesas", new ArrayList<>()); }
        return "admin-despesas";
    }

    @PostMapping("/admin/despesas/nova")
    public String registrarDespesa(@RequestParam String descricao, @RequestParam Double valor, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser")) || session.getAttribute("gestorAutorizado") == null) {
            return "redirect:/admin/analises/autenticar";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        try { googleSheetsService.salvarDespesa(descricao, BigDecimal.valueOf(valor)); }
        catch (Exception e) { System.err.println("Erro despesa: " + e.getMessage()); }
        return "redirect:/admin/despesas";
    }

    @GetMapping("/admin/precos")
    public String painelPrecos(Model m, HttpSession s, HttpServletResponse response) {
        if(s.getAttribute("adminLogado") == null) return "redirect:/login";
        if (s.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        String perfil = (String) s.getAttribute("perfilUser");
        if (!"GESTOR".equalsIgnoreCase(perfil)) return "redirect:/admin/coletas";

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        try { m.addAttribute("materiais", googleSheetsService.listarMateriais()); }
        catch (Exception e) { m.addAttribute("materiais", new ArrayList<>()); }
        return "admin-precos";
    }

    @PostMapping("/admin/material/novo")
    public String novoMaterial(@RequestParam String nome, @RequestParam String unidade, @RequestParam Double preco, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";

        Material m = new Material(); m.setNome(nome); m.setUnidade(unidade); m.setPrecoPorKg(BigDecimal.valueOf(preco));
        try { googleSheetsService.salvarMaterial(m); } catch (Exception e) { }
        return "redirect:/admin/precos";
    }

    @PostMapping("/admin/atualizar")
    public String upd(@RequestParam Long id, @RequestParam Double novoPreco, HttpSession session) {
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        try { googleSheetsService.atualizarPrecoMaterial(id, BigDecimal.valueOf(novoPreco)); } catch (Exception e) { }
        return "redirect:/admin/precos";
    }

    @GetMapping("/admin/material/deletar/{id}")
    public String deletarMaterial(@PathVariable Long id, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        try { googleSheetsService.deletarMaterial(id); } catch (Exception e) { }
        return "redirect:/admin/precos";
    }

    @GetMapping("/admin/analises")
    public String mostrarAnalises(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        if (session.getAttribute("gestorAutorizado") == null) return "redirect:/admin/analises/autenticar";

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache"); response.setHeader("Expires", "0");

        try {
            List<Oferta> historico = googleSheetsService.getHistoricoCompleto();
            String dataHoje = LocalDate.now(ZoneId.of("America/Recife")).toString();

            List<Material> todosMateriais = googleSheetsService.listarMateriais();
            Map<String, String> mapaUnidades = todosMateriais.stream().collect(Collectors.toMap(Material::getNome, Material::getUnidade));
            model.addAttribute("mapaUnidades", mapaUnidades);

            Double caixaEntradaHoje = 0.0;
            Double caixaSaidaHoje = 0.0;
            Map<String, Double> estoqueRealKg = new HashMap<>();

            Map<String, Double> totalCompradoKg = new HashMap<>();
            Double totalGeralKg = 0.0;
            Double totalPagoCatadores = 0.0;
            Double pesoCompradoHoje = 0.0;

            for (Oferta o : historico) {
                if (o.getMaterial() == null || o.getPeso() == null) continue;
                String material = o.getMaterial(); Double peso = o.getPeso();
                Double valor = (o.getPrecoEstimado() != null) ? o.getPrecoEstimado().doubleValue() : 0.0;
                boolean isHoje = dataHoje.equals(o.getData());
                String status = (o.getStatus() != null) ? o.getStatus().toString().toUpperCase() : "VENDIDO";

                if (status.equals("SAIDA_INDUSTRIA") || status.equals("AJUSTE_NEGATIVO")) {
                    estoqueRealKg.put(material, estoqueRealKg.getOrDefault(material, 0.0) - peso);
                    if (isHoje && status.equals("SAIDA_INDUSTRIA")) caixaEntradaHoje += valor;
                } else if (status.equals("VENDIDO") || status.equals("AJUSTE_POSITIVO")) {
                    estoqueRealKg.put(material, estoqueRealKg.getOrDefault(material, 0.0) + peso);

                    if (isHoje && status.equals("VENDIDO")) {
                        caixaSaidaHoje += valor;
                        if ("KG".equalsIgnoreCase(mapaUnidades.getOrDefault(material, "KG"))) {
                            pesoCompradoHoje += peso;
                        }
                    }

                    if (status.equals("VENDIDO")) {
                        String unidadeMat = mapaUnidades.getOrDefault(material, "KG");
                        if ("KG".equalsIgnoreCase(unidadeMat)) {
                            totalCompradoKg.put(material, totalCompradoKg.getOrDefault(material, 0.0) + peso);
                            totalGeralKg += peso;
                        }
                        totalPagoCatadores += valor;
                    }
                }
            }

            estoqueRealKg.entrySet().removeIf(entry -> entry.getValue() <= 0);

            Double totalDespesasHoje = googleSheetsService.calcularDespesasDoDia(dataHoje);
            Double lucroDoDia = caixaEntradaHoje - caixaSaidaHoje - totalDespesasHoje;

            Map<String, Double> rankingMaisColetados = totalCompradoKg.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(3)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

            Map<String, Double> baixaColeta = totalCompradoKg.entrySet().stream()
                    .filter(e -> e.getValue() < 50.0)
                    .sorted(Map.Entry.comparingByValue())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

            Map<String, Double> top5Reciclometro = totalCompradoKg.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(5)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

            model.addAttribute("caixaEntradaHoje", caixaEntradaHoje);
            model.addAttribute("caixaSaidaHoje", caixaSaidaHoje);
            model.addAttribute("totalDespesasHoje", totalDespesasHoje);
            model.addAttribute("lucroDoDia", lucroDoDia);
            model.addAttribute("estoqueReal", estoqueRealKg);
            model.addAttribute("pesoCompradoHoje", pesoCompradoHoje);
            model.addAttribute("rankingMaisColetados", rankingMaisColetados);
            model.addAttribute("baixaColeta", baixaColeta);
            model.addAttribute("totalGeralKg", totalGeralKg);
            model.addAttribute("totalPagoCatadores", totalPagoCatadores);
            model.addAttribute("top5Reciclometro", top5Reciclometro);

            List<Usuario> todosUsuarios = googleSheetsService.listarUsuarios();
            long clientesReais = todosUsuarios.stream().filter(u -> u.getNome() == null || !u.getNome().contains("(SAÍDA)")).count();
            long totalExpedicoes = todosUsuarios.stream().filter(u -> u.getNome() != null && u.getNome().contains("(SAÍDA)")).count();

            model.addAttribute("totalAtendimentos", clientesReais);
            model.addAttribute("totalExpedicoes", totalExpedicoes);
            model.addAttribute("dadosRoscaKg", estoqueRealKg);

            List<Map<String, String>> historicoCaixa = googleSheetsService.listarFechamentosCaixa();
            model.addAttribute("historicoCaixa", historicoCaixa);
            model.addAttribute("dataHojeString", dataHoje);

            return "analises";

        } catch (Exception e) {
            return "redirect:/admin/coletas?erro=analises";
        }
    }

    @GetMapping("/admin/analises/autenticar")
    public String telaSenhaGestor(HttpSession session, HttpServletResponse response) {
        String perfil = (String) session.getAttribute("perfilUser");
        if (!"GESTOR".equalsIgnoreCase(perfil)) return "redirect:/admin/coletas";

        session.removeAttribute("gestorAutorizado");

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        return "autenticar-analises";
    }

    @PostMapping("/admin/analises/autenticar")
    public String processarSenhaGestor(@RequestParam String senhaGestor, HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        String senhaCorreta = (String) session.getAttribute("senhaFinanceira");
        if (senhaCorreta != null && senhaCorreta.equals(senhaGestor)) {
            session.setAttribute("gestorAutorizado", true); return "redirect:/admin/analises";
        }
        ra.addFlashAttribute("erro", "Senha incorreta!"); return "redirect:/admin/analises/autenticar";
    }

    @GetMapping("/admin/analises/sair")
    public String sairDaAnalise(HttpSession session) {
        session.removeAttribute("gestorAutorizado"); return "redirect:/admin/coletas";
    }

    @GetMapping("/admin/saida")
    public String telaSaida(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        if (session.getAttribute("gestorAutorizado") == null) return "redirect:/admin/analises/autenticar";

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache"); response.setDateHeader("Expires", 0);

        try { model.addAttribute("materiais", googleSheetsService.listarMateriais()); }
        catch (Exception e) { model.addAttribute("materiais", new ArrayList<>()); }
        return "admin-saida";
    }

    @PostMapping("/admin/registrar-saida-lote")
    public String registrarSaidaLote(@RequestParam String nomeIndustria, @RequestParam(required = false) String cnpjIndustria, @RequestParam Map<String, String> params, HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser")) || session.getAttribute("gestorAutorizado") == null) {
            return "redirect:/admin/analises/autenticar";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        try {
            List<Oferta> historico = googleSheetsService.getHistoricoCompleto();
            Map<String, Double> estoqueRealKg = new HashMap<>();

            for (Oferta o : historico) {
                if (o.getMaterial() == null || o.getPeso() == null) continue;
                String status = (o.getStatus() != null) ? o.getStatus().toString().toUpperCase() : "VENDIDO";

                if (status.equals("SAIDA_INDUSTRIA") || status.equals("AJUSTE_NEGATIVO")) {
                    estoqueRealKg.put(o.getMaterial(), estoqueRealKg.getOrDefault(o.getMaterial(), 0.0) - o.getPeso());
                } else if (status.equals("VENDIDO") || status.equals("AJUSTE_POSITIVO")) {
                    estoqueRealKg.put(o.getMaterial(), estoqueRealKg.getOrDefault(o.getMaterial(), 0.0) + o.getPeso());
                }
            }

            List<Material> todosMateriais = googleSheetsService.listarMateriais();
            List<List<Object>> loteDeVendas = new ArrayList<>();

            for (Material mat : todosMateriais) {
                String pesoStr = params.get("peso_" + mat.getId());
                if (pesoStr != null && !pesoStr.isEmpty()) {
                    Double pesoSaida = Double.parseDouble(pesoStr.replace(",", "."));
                    if (pesoSaida > 0) {
                        Double disponivelNoPatio = estoqueRealKg.getOrDefault(mat.getNome(), 0.0);

                        if (pesoSaida > disponivelNoPatio) {
                            ra.addFlashAttribute("erroEstoque", "Quantidade incompatível! Você tentou vender " + pesoSaida + "kg de " + mat.getNome() + ", mas só possui " + String.format("%.2f", disponivelNoPatio) + "kg no pátio.");
                            return "redirect:/admin/saida";
                        }
                    }
                }
            }

            Long idGerado = System.currentTimeMillis();
            String cnpjFinal = (cnpjIndustria != null && !cnpjIndustria.trim().isEmpty()) ? cnpjIndustria : "NÃO INFORMADO";

            Usuario novaIndustria = new Usuario();
            novaIndustria.setId(idGerado);
            novaIndustria.setNome(nomeIndustria.toUpperCase() + " (SAÍDA)");
            novaIndustria.setTelefone(idGerado.toString());
            novaIndustria.setEndereco(cnpjFinal);
            novaIndustria.setTipo(Usuario.TipoUsuario.CATADOR);

            googleSheetsService.salvarUsuario(novaIndustria);

            for (Material mat : todosMateriais) {
                String pesoStr = params.get("peso_" + mat.getId());
                String precoStr = params.get("preco_" + mat.getId());

                if (pesoStr != null && !pesoStr.isEmpty() && precoStr != null && !precoStr.isEmpty()) {
                    Double pesoSaida = Double.parseDouble(pesoStr.replace(",", "."));
                    Double precoVenda = Double.parseDouble(precoStr.replace(",", "."));

                    if (pesoSaida > 0 && precoVenda > 0) {
                        BigDecimal precoUn = BigDecimal.valueOf(precoVenda);
                        BigDecimal total = precoUn.multiply(BigDecimal.valueOf(pesoSaida));

                        String idSaidaUnico = UUID.randomUUID().toString().substring(0, 13).toUpperCase();

                        List<Object> row = Arrays.asList(idSaidaUnico, mat.getNome(), pesoSaida.toString().replace(".", ","), "VENDA INDÚSTRIA", precoUn.toString().replace(".", ","), total.toString().replace(".", ","), LocalDate.now(ZoneId.of("America/Recife")).toString(), idGerado.toString(), "SAIDA_INDUSTRIA", cnpjFinal);
                        loteDeVendas.add(row);
                    }
                }
            }

            if (loteDeVendas.isEmpty()) return "redirect:/admin/saida?erro=vazio";
            googleSheetsService.registrarVendasEmLote(loteDeVendas);

            return "redirect:/extrato/" + idGerado;

        } catch (Exception e) {
            ra.addFlashAttribute("erroEstoque", "Ocorreu um erro interno ao processar a saída.");
            return "redirect:/admin/saida";
        }
    }

    @GetMapping("/admin/fornecedores-vip")
    public String telaFornecedoresVip(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        try {
            model.addAttribute("vips", googleSheetsService.listarFornecedoresVip());
            model.addAttribute("materiais", googleSheetsService.listarMateriais());
        } catch (Exception e) {}
        return "admin-fornecedores-vip";
    }

    @PostMapping("/admin/fornecedores-vip/novo")
    public String salvarFornecedorVip(@RequestParam String telefone, @RequestParam String material, @RequestParam Double preco, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try { googleSheetsService.salvarFornecedorVip(telefone, material, BigDecimal.valueOf(preco)); } catch (Exception e) {}
        return "redirect:/admin/fornecedores-vip";
    }

    @GetMapping("/admin/fornecedores-vip/deletar")
    public String deletarFornecedorVip(@RequestParam String telefone, @RequestParam String material, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try { googleSheetsService.deletarFornecedorVip(telefone, material); } catch (Exception e) {}
        return "redirect:/admin/fornecedores-vip";
    }

    @GetMapping("/admin/ajuste-estoque")
    public String telaAjusteEstoque(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        if (session.getAttribute("gestorAutorizado") == null) return "redirect:/admin/analises/autenticar";

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        try { model.addAttribute("materiais", googleSheetsService.listarMateriais()); } catch (Exception e) {}
        return "admin-ajuste-estoque";
    }

    @PostMapping("/admin/ajuste-estoque/novo")
    public String salvarAjusteEstoque(@RequestParam String material, @RequestParam Double peso, @RequestParam String tipoAjuste, @RequestParam String motivo, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser")) || session.getAttribute("gestorAutorizado") == null) return "redirect:/admin/analises/autenticar";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try { googleSheetsService.salvarAjusteEstoque(material, peso, tipoAjuste, motivo); } catch (Exception e) {}
        return "redirect:/admin/analises";
    }

    @GetMapping("/admin/despesas/deletar/{id}")
    public String deletarDespesa(@PathVariable String id, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser")) || session.getAttribute("gestorAutorizado") == null) {
            return "redirect:/admin/analises/autenticar";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try { googleSheetsService.deletarDespesa(id); } catch (Exception e) {}
        return "redirect:/admin/despesas";
    }

    @PostMapping("/admin/fechar-caixa")
    public String fecharCaixaDiario(
            @RequestParam String dataHoje, @RequestParam Double entradas,
            @RequestParam Double saidas, @RequestParam Double despesas,
            @RequestParam Double lucro, @RequestParam Double pesoTotal,
            HttpSession session) {

        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser")) || session.getAttribute("gestorAutorizado") == null) {
            return "redirect:/admin/analises/autenticar";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        try {
            googleSheetsService.fecharCaixaDoDia(dataHoje, BigDecimal.valueOf(entradas), BigDecimal.valueOf(saidas), BigDecimal.valueOf(despesas), BigDecimal.valueOf(lucro), pesoTotal);
        } catch (Exception e) {
            System.err.println("Erro ao fechar caixa: " + e.getMessage());
        }

        return "redirect:/admin/analises";
    }

    // ==========================================
    // ROTA LIVRE (A TELA DE PAGAMENTO)
    // ==========================================
    @GetMapping("/admin/assinatura")
    public String telaAssinaturaSaaS(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        // Puxa o plano que o usuário escolheu na hora do cadastro
        String plano = (String) session.getAttribute("planoAssinatura");
        String status = (String) session.getAttribute("statusAssinatura");

        double valor = "PRO".equals(plano) ? 199.90 : 149.90;
        String planoNome = "PRO".equals(plano) ? "Coletaê SaaS Pro" : "Coletaê SaaS Start";

        model.addAttribute("planoNome", planoNome);
        model.addAttribute("valorMensalidade", valor);
        model.addAttribute("statusAssinatura", status != null ? status : "TRIAL");

        // Define o vencimento para 7 dias após hoje (apenas visual)
        model.addAttribute("proximoVencimento", LocalDate.now(ZoneId.of("America/Recife")).plusDays(7).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        model.addAttribute("faturas", new ArrayList<>()); // Limpo por enquanto
        return "admin-assinatura";
    }

    // ==========================================
    // ÁREA RESTRITA DO MASTER (O SEU PAINEL)
    // ==========================================

    @GetMapping("/master/painel")
    public String telaPainelMaster(Model model, HttpSession session) {
        // Trava de segurança: só você entra aqui
        if (session.getAttribute("masterLogado") == null) {
            return "redirect:/login";
        }

        // Busca todos os clientes no Supabase e manda pra tela
        model.addAttribute("listaClientes", googleSheetsService.listarTodosArmazensSaaS());
        return "master-painel";
    }

    // Rota secreta para você logar como Master
    @PostMapping("/master/login-secreto")
    public String loginMaster(@RequestParam String senhaMaster, HttpSession session) {
        // Usa a variável de ambiente injetada no topo da classe
        if (senhaMasterConfigurada.equals(senhaMaster)) {
            session.setAttribute("masterLogado", true);
            return "redirect:/master/painel";
        }
        return "redirect:/login?erro=true";
    }

    @PostMapping("/master/mudar-status")
    public String mudarStatusCliente(@RequestParam Long id, @RequestParam String status, HttpSession session) {
        if (session.getAttribute("masterLogado") == null) return "redirect:/login";

        googleSheetsService.atualizarStatusArmazem(id, status);
        return "redirect:/master/painel";
    }

    @PostMapping("/admin/perfil/atualizar-senhas")
    public String atualizarSenhasPerfil(@RequestParam(required = false) String novaSenha,
                                        @RequestParam(required = false) String novaSenhaFinanceira,
                                        HttpSession session,
                                        org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";

        try {
            Long idArmazem = Long.parseLong(session.getAttribute("idPlanilhaAtiva").toString());
            googleSheetsService.atualizarSenhasArmazem(idArmazem, novaSenha, novaSenhaFinanceira);

            // Atualiza a senha na sessão caso tenha sido alterada
            if (novaSenhaFinanceira != null && !novaSenhaFinanceira.trim().isEmpty()) {
                session.setAttribute("senhaFinanceira", novaSenhaFinanceira);
            }

            ra.addFlashAttribute("sucessoPerfil", "Credenciais atualizadas com sucesso!");
        } catch (Exception e) {
            ra.addFlashAttribute("erroPerfil", "Não foi possível atualizar as senhas.");
        }

        return "redirect:/admin/coletas";
    }
}