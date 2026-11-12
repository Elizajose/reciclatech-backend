package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.*;
import com.reciclatech.backend.repository.*;
import com.reciclatech.backend.service.AnaliseFinanceira;
import com.reciclatech.backend.service.DatabaseService;
import com.reciclatech.backend.service.LimitadorPublico;
import com.reciclatech.backend.service.LoginThrottle;
import com.reciclatech.backend.service.ParceiroService;
import com.reciclatech.backend.util.ExportacaoCsv;
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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TelaController.class);


    @Autowired(required = false) private OfertaRepository ofertaRepository;
    @Autowired(required = false) private UsuarioRepository usuarioRepository;
    @Autowired(required = false) private MaterialRepository materialRepository;

    @Autowired private DatabaseService googleSheetsService;
    @Autowired private ParceiroService parceiroService;
    @Autowired private LoginThrottle throttle;
    @Autowired private LimitadorPublico limitadorPublico;

    @Autowired(required = false)
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    // Senha Master configurada no application.properties (variável SENHA_MASTER)
    @Value("${coletae.senha-master}")
    private String senhaMasterConfigurada;

    private boolean senhaMasterValida(String senha) {
        return !senhaMasterConfigurada.isBlank() && senha != null
                && java.security.MessageDigest.isEqual(
                        senhaMasterConfigurada.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        senha.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // E-mail que recebe os chamados de suporte (variável EMAIL_SUPORTE)
    @Value("${coletae.email-suporte}")
    private String emailSuporte;


    // =========================================================================
    // 1. ÁREA PÚBLICA E AUTENTICAÇÃO
    // =========================================================================

    @GetMapping("/")
    public String home(Model model) {
        try {
            model.addAttribute("listaArmazens", googleSheetsService.obterCotacoesPublicas());
        } catch (Exception e) {
            model.addAttribute("listaArmazens", new ArrayList<>());
        }
        return "index";
    }

    @GetMapping("/cadastro")
    public String telaCadastro() {
        return "redirect:/conta/cadastro"; // cadastro antigo desativado na tela; o fluxo novo é /conta/cadastro
    }

    @PostMapping("/salvar-cadastro")
    public String salvarNovoCadastro() {
        return "redirect:/conta/cadastro"; // cadastro antigo desativado: contas novas só em /conta/cadastro
    }

    @GetMapping("/login")
    public String telaLogin(HttpSession session) {
        if (session.getAttribute("adminLogado") != null) {
            if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
            return "redirect:/admin/coletas";
        }
        return "login-admin";
    }

    @PostMapping("/login-admin")
    public String login(@RequestParam String login, @RequestParam String senha, jakarta.servlet.http.HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String chaveLogin = "op:" + login;
        if (throttle.bloqueado(chaveLogin)) return "redirect:/login?erro=tentativas";
        if ("master".equals(login) && senhaMasterValida(senha)) {
            request.changeSessionId(); // evita fixação de sessão
            session.setAttribute("masterLogado", true);
            return "redirect:/master/painel";
        }

        // Conta do dono (e-mail + senha): o mesmo login serve para operador (usuário) e para dono (e-mail)
        String emailDono = login.trim();
        String chaveDono = "login:" + emailDono;
        boolean pareceEmail = emailDono.contains("@");
        if (pareceEmail) {
            if (throttle.bloqueado(chaveDono)) return "redirect:/login?erro=tentativas";
            Optional<Parceiro> dono = parceiroService.autenticar(emailDono, senha);
            if (dono.isPresent()) {
                throttle.sucesso(chaveDono);
                throttle.sucesso(chaveLogin);
                request.changeSessionId(); // evita fixação de sessão
                session.setAttribute("parceiroId", dono.get().getId());
                session.setAttribute("parceiroNome", dono.get().getNome());
                return "redirect:/conta/armazens";
            }
        }

        try {
            Map<String, String> dadosUser = googleSheetsService.autenticarOperador(login, senha);
            if (dadosUser != null) {
                throttle.sucesso(chaveLogin);
                request.changeSessionId(); // evita fixação de sessão
                session.setAttribute("adminLogado", true);
                session.setAttribute("perfilUser", dadosUser.get("perfil"));
                session.setAttribute("idPlanilhaAtiva", dadosUser.get("idPlanilha"));
                session.setAttribute("nomeArmazem", dadosUser.getOrDefault("nomeArmazem", "Armazém Parceiro"));
                session.setAttribute("cnpjArmazem", dadosUser.getOrDefault("cnpj", ""));
                session.setAttribute("telefone", dadosUser.getOrDefault("telefone", ""));
                session.setAttribute("senhaLogin", dadosUser.getOrDefault("senhaLogin", ""));
                session.setAttribute("enderecoArmazem", dadosUser.getOrDefault("endereco", "Endereço não informado"));

                // DEFINIÇÃO INTELIGENTE DO RESPONSÁVEL:
                String perfil = dadosUser.get("perfil");
                if ("GESTOR".equals(perfil)) {
                    session.setAttribute("nomeFuncionarioLogado", "Gestor / Administrador");
                } else {
                    session.setAttribute("nomeFuncionarioLogado", dadosUser.getOrDefault("nomeFuncionarioLogado", "Operador de Balcão"));
                }

                String status = dadosUser.getOrDefault("status", "ATIVO");
                String plano = dadosUser.getOrDefault("plano", "START");
                String trialVencido = dadosUser.getOrDefault("trialVencido", "false");

                session.setAttribute("planoAssinatura", plano);
                session.setAttribute("statusAssinatura", status);

                if ("BLOQUEADO".equals(status) || "true".equals(trialVencido)) {
                    session.setAttribute("bloqueadoPagamento", true);
                    return "redirect:/admin/assinatura";
                }

                session.removeAttribute("bloqueadoPagamento");
                return "redirect:/admin/coletas";
            }
        } catch (RuntimeException e) {
            return "redirect:/login?erro=suspenso";
        } catch (Exception e) {
            log.error("Erro inesperado", e);
        }
        throttle.falhou(chaveLogin);
        if (pareceEmail) throttle.falhou(chaveDono);
        return "redirect:/login?erro=true";
    }

    @PostMapping("/sair")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    /** Links antigos de "sair" (GET) não encerram mais a sessão: apenas levam à tela de login. */
    @GetMapping("/sair")
    public String logoutLinkAntigo() {
        return "redirect:/login";
    }

    @PostMapping("/publicar")
    public String solicitarColeta(@RequestParam String endereco, @RequestParam String nomeVendedor,
                                  @RequestParam String telefoneVendedor, @RequestParam String idPlanilhaDestino,
                                  jakarta.servlet.http.HttpServletRequest request) {
        String zapLimpo = telefoneVendedor.replaceAll("\\D", "");
        if (nomeVendedor.isBlank() || nomeVendedor.length() > 120 || endereco.isBlank() || endereco.length() > 255
                || zapLimpo.length() < 10 || zapLimpo.length() > 13) {
            return "redirect:/?erro=dados";
        }
        if (!googleSheetsService.armazemAceitaSolicitacoes(idPlanilhaDestino)) {
            return "redirect:/?erro=armazem";
        }
        // Freio contra abuso: 6 pedidos por 10 min por origem e 3 por hora por telefone/armazém
        if (!limitadorPublico.permitir("ip:" + request.getRemoteAddr(), 6, 10 * 60 * 1000L)
                || !limitadorPublico.permitir("tel:" + idPlanilhaDestino + ":" + zapLimpo, 3, 60 * 60 * 1000L)) {
            return "redirect:/?erro=limite";
        }
        Usuario novo = new Usuario();
        novo.setNome(nomeVendedor); novo.setTelefone(zapLimpo); novo.setEndereco(endereco); novo.setTipo(Usuario.TipoUsuario.CATADOR);
        try { googleSheetsService.salvarSolicitacaoInicial(novo, endereco, idPlanilhaDestino); }
        catch (IOException e) { log.error("Erro ao publicar", e); }
        return "redirect:/?sucesso=true";
    }


    // =========================================================================
    // 2. ATENDIMENTO DE BALCÃO (OPERAÇÃO)
    // =========================================================================

    @GetMapping("/admin/coletas")
    public String telaListaColetas(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

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

    @PostMapping("/admin/pesagem-rapida")
    public String pesagemRapida(@RequestParam String nome, @RequestParam(required = false) String cpf, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        String cpfPadronizado = "";
        if (cpf != null && !cpf.trim().isEmpty()) {
            String apenasNumeros = cpf.replaceAll("\\D", "");
            if (apenasNumeros.length() == 11) {
                cpfPadronizado = apenasNumeros.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
            } else {
                cpfPadronizado = cpf.trim();
            }
        }

        String chaveTemporaria = String.valueOf(System.currentTimeMillis());

        try {
            Usuario usuarioBalcao = new Usuario();
            usuarioBalcao.setNome(nome);
            usuarioBalcao.setCpf(cpfPadronizado);
            usuarioBalcao.setTelefone(chaveTemporaria);
            usuarioBalcao.setEndereco("Atendimento Avulso");
            usuarioBalcao.setTipo(Usuario.TipoUsuario.CATADOR);

            googleSheetsService.salvarSolicitacaoInicial(usuarioBalcao, "Atendimento Avulso");
            return "redirect:/admin/atender/" + chaveTemporaria;
        } catch (Exception e) {
            log.error("Erro pesagem rápida", e);
            return "redirect:/admin/coletas?erro=pesagem_rapida";
        }
    }

    @GetMapping("/admin/atender/{idUsuario}")
    public String telaChecklist(@PathVariable String idUsuario, Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache"); response.setDateHeader("Expires", 0);

        try {
            googleSheetsService.marcarComoEmAtendimento(idUsuario);
            Usuario vendedor = googleSheetsService.listarUsuarios().stream()
                    .filter(u -> idUsuario.equals(u.getTelefone()) || idUsuario.equals(u.getId().toString()))
                    .findFirst().orElseThrow();

            model.addAttribute("vendedor", vendedor);
            model.addAttribute("todosMateriais", googleSheetsService.listarMateriais());
            model.addAttribute("precosVip", googleSheetsService.buscarPrecosEspeciais(vendedor.getCpf()));

        } catch (Exception e) {
            log.error("Erro ao carregar checklist", e);
            return "redirect:/admin/coletas?erro=usuario";
        }
        return "admin-checklist";
    }

    @PostMapping("/admin/revisar-coleta")
    public String revisarColeta(@RequestParam String idVendedor, @RequestParam Map<String, String> params, Model model, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try {
            Usuario vendedor = googleSheetsService.listarUsuarios().stream()
                    .filter(u -> u.getTelefone().equals(idVendedor) || u.getId().toString().equals(idVendedor))
                    .findFirst().orElseThrow();

            List<PreVendaDTO> itensRevisao = new ArrayList<>();
            BigDecimal totalEstimado = BigDecimal.ZERO;
            List<Material> todosMateriais = googleSheetsService.listarMateriais();
            Map<String, BigDecimal> precosVip = googleSheetsService.buscarPrecosEspeciais(vendedor.getCpf());

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
                    } catch (Exception e) { log.debug("Quantidade inválida ignorada", e); }
                }
            }
            model.addAttribute("vendedor", vendedor);
            model.addAttribute("itens", itensRevisao);
            model.addAttribute("totalEstimado", totalEstimado);
            return "admin-revisao";
        } catch (Exception e) {
            return "redirect:/admin/coletas?erro=planilha";
        }
    }

    @PostMapping("/admin/confirmar-finalizacao")
    public String confirmarFinalizacao(@RequestParam String idVendedor, @RequestParam(required = false) String cpfFinal,
                                       @RequestParam(required = false) List<Long> idsMateriais, @RequestParam(required = false) List<Double> pesosFinais,
                                       @RequestParam(required = false) List<Double> precosFinais, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
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
                        idVendaUnico, mat.getNome(), pesosFinais.get(i), "ENTREGA NO LOCAL",
                        precoUn, total, LocalDate.now(ZoneId.of("America/Recife")).toString(),
                        idVendedor, "VENDIDO", cpfFinal != null && !cpfFinal.trim().isEmpty() ? cpfFinal : "NÃO INFORMADO"
                );
                loteDeVendas.add(row);
            }
            boolean gravou = googleSheetsService.finalizarAtendimento(idVendedor, cpfFinal, loteDeVendas);
            if (!gravou) return "redirect:/admin/coletas?aviso=ja_finalizado";
            return "redirect:/extrato/" + idVendedor;
        } catch (Exception e) {
            log.error("Erro ao finalizar coleta", e);
            return "redirect:/admin/coletas?erro=venda";
        }
    }

    @PostMapping("/admin/cancelar")
    public String cancelarColeta(@RequestParam String id, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        try { googleSheetsService.cancelarSolicitacao(id); }
        catch (Exception e) { log.error("Erro ao cancelar coleta", e); return "redirect:/admin/coletas?erro=cancelar"; }
        return "redirect:/admin/coletas";
    }


    // =========================================================================
// 3. EXTRATOS E RECIBOS
// =========================================================================

    @GetMapping("/extrato/{id}")
    public String gerarExtratoIndividual(@PathVariable String id, Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        try {
            Usuario usuario = googleSheetsService.listarUsuarios().stream().filter(u -> u.getTelefone().equals(id) || u.getId().toString().equals(id)).findFirst().orElse(null);
            if (usuario == null) return "redirect:/";

            List<Oferta> vendasReais = googleSheetsService.buscarVendasPorUsuario(id);
            BigDecimal totalGeral = vendasReais.stream().map(Oferta::getPrecoEstimado).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Material> mats = googleSheetsService.listarMateriais();

            // =========================================================================
            // ADICIONADO: BUSCA O RESPONSÁVEL DA SESSÃO PARA O RECIBO/EXTRATO
            // =========================================================================
            String responsavel = (String) session.getAttribute("nomeFuncionarioLogado");
            if (responsavel == null || responsavel.trim().isEmpty()) {
                responsavel = "Gestor / Administrador";
            }
            model.addAttribute("responsavelAtendimento", responsavel);

            model.addAttribute("mapaUnidades", mats.stream().collect(Collectors.toMap(Material::getNome, Material::getUnidade)));
            model.addAttribute("mapaPrecos", mats.stream().collect(Collectors.toMap(Material::getNome, Material::getPrecoPorKg)));
            model.addAttribute("vendedor", usuario);
            model.addAttribute("vendas", vendasReais);
            model.addAttribute("total", totalGeral);
            model.addAttribute("dataHoje", LocalDate.now(ZoneId.of("America/Recife")));

        } catch (Exception e) {
            return "redirect:/?erro=extrato";
        }
        return "extrato";
    }

    @GetMapping("/meus-extratos")
    public String listaExtratosDoDia(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        try {
            List<Usuario> usuarios = googleSheetsService.buscarUsuariosComVendasHoje();
            model.addAttribute("usuarios", usuarios);
        } catch (Exception e) {
            log.error("Erro na Central de Extratos", e);
            model.addAttribute("usuarios", new ArrayList<>());
        }
        return "lista-extratos";
    }


    // =========================================================================
    // 4. GESTÃO DE ESTOQUE (SAÍDAS E AJUSTES)
    // =========================================================================

    @GetMapping("/admin/saida")
    public String telaSaida(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache"); response.setDateHeader("Expires", 0);

        try { model.addAttribute("materiais", googleSheetsService.listarMateriais()); }
        catch (Exception e) { model.addAttribute("materiais", new ArrayList<>()); }
        return "admin-saida";
    }

    @PostMapping("/admin/registrar-saida-lote")
    public String registrarSaidaLote(@RequestParam String nomeIndustria, @RequestParam(required = false) String cnpjIndustria, @RequestParam Map<String, String> params, HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/admin/coletas";
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

    @GetMapping("/admin/ajuste-estoque")
    public String telaAjusteEstoque(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        try { model.addAttribute("materiais", googleSheetsService.listarMateriais()); } catch (Exception e) { log.warn("Falha ignorada", e); }
        return "admin-ajuste-estoque";
    }

    @PostMapping("/admin/ajuste-estoque/novo")
    public String salvarAjusteEstoque(@RequestParam String material, @RequestParam Double peso, @RequestParam String tipoAjuste, @RequestParam String motivo, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try { googleSheetsService.salvarAjusteEstoque(material, peso, tipoAjuste, motivo); } catch (Exception e) { log.warn("Falha ignorada", e); }
        return "redirect:/admin/analises";
    }


    // =========================================================================
    // 5. TABELAS DE PREÇO E VIPs (CONFIGURAÇÕES DE COMPRA)
    // =========================================================================

    @GetMapping("/admin/precos")
    public String painelPrecos(Model m, HttpSession s, HttpServletResponse response) {
        if(s.getAttribute("adminLogado") == null) return "redirect:/login";
        if (s.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) s.getAttribute("perfilUser"))) return "redirect:/admin/coletas";

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
        try { googleSheetsService.salvarMaterial(m); } catch (Exception e) { log.error("Erro ao salvar material", e); }
        return "redirect:/admin/precos";
    }

    @PostMapping("/admin/atualizar")
    public String upd(@RequestParam Long id, @RequestParam Double novoPreco, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        try { googleSheetsService.atualizarPrecoMaterial(id, BigDecimal.valueOf(novoPreco)); } catch (Exception e) { log.error("Erro ao atualizar preço", e); }
        return "redirect:/admin/precos";
    }

    @PostMapping("/admin/material/deletar")
    public String deletarMaterial(@RequestParam Long id, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        try { googleSheetsService.deletarMaterial(id); } catch (Exception e) { log.error("Erro ao excluir material", e); }
        return "redirect:/admin/precos";
    }

    /** Histórico de alterações de preços e despesas: somente leitura, somente para o dono (GESTOR). */
    @GetMapping("/admin/historico-alteracoes")
    public String historicoAlteracoes(@RequestParam(required = false) String tipo, Model model, HttpSession s, HttpServletResponse response) {
        if (s.getAttribute("adminLogado") == null) return "redirect:/login";
        if (s.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) s.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String entidade = null;
        if ("MATERIAL".equals(tipo) || "DESPESA".equals(tipo) || "PRECO_VIP".equals(tipo)) entidade = tipo;
        model.addAttribute("tipo", entidade == null ? "TODOS" : entidade);
        model.addAttribute("registros", googleSheetsService.listarHistorico(entidade, 500));
        return "admin-historico-alteracoes";
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
        } catch (Exception e) { log.warn("Falha ignorada", e); }
        return "admin-fornecedores-vip";
    }

    @PostMapping("/admin/fornecedores-vip/novo")
    public String salvarFornecedorVip(@RequestParam String cpf, @RequestParam String material, @RequestParam Double preco, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try { googleSheetsService.salvarFornecedorVip(cpf, material, BigDecimal.valueOf(preco)); } catch (Exception e) { log.warn("Falha ignorada", e); }
        return "redirect:/admin/fornecedores-vip";
    }

    @PostMapping("/admin/fornecedores-vip/deletar")
    public String deletarFornecedorVip(@RequestParam String cpf, @RequestParam String material, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try { googleSheetsService.deletarFornecedorVip(cpf, material); } catch (Exception e) { log.warn("Falha ignorada", e); }
        return "redirect:/admin/fornecedores-vip";
    }


    // =========================================================================
    // 6. FINANCEIRO E ANÁLISES (RESTRITO GESTOR)
    // =========================================================================

    @GetMapping("/admin/analises")
    public String mostrarAnalises(@RequestParam(required = false) String periodo,
                                  @RequestParam(required = false) String de,
                                  @RequestParam(required = false) String ate,
                                  Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";

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

            Set<String> clientesReaisSet = new HashSet<>();
            Set<String> expedicoesSet = new HashSet<>();

            for (Oferta o : historico) {
                if (o.getMaterial() == null || o.getPeso() == null) continue;

                String status = (o.getStatus() != null) ? o.getStatus().toString().toUpperCase() : "VENDIDO";

                if (status.equals("DISPONIVEL") || status.equals("EM_ATENDIMENTO") || status.equals("FINALIZADO") || status.equals("CANCELADO")) {
                    continue;
                }

                String material = o.getMaterial();
                Double peso = o.getPeso();
                Double valor = (o.getPrecoEstimado() != null) ? o.getPrecoEstimado().doubleValue() : 0.0;
                boolean isHoje = dataHoje.equals(o.getData());

                if (o.getUsuario() != null) {
                    if (status.equals("VENDIDO") || status.equals("AJUSTE_POSITIVO")) {
                        clientesReaisSet.add(o.getUsuario().getId() + "|" + o.getData());
                    } else if (status.equals("SAIDA_INDUSTRIA")) {
                        expedicoesSet.add(o.getUsuario().getId() + "|" + o.getData());
                    }
                }

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

            model.addAttribute("totalAtendimentos", clientesReaisSet.size());
            model.addAttribute("totalExpedicoes", expedicoesSet.size());
            model.addAttribute("dadosRoscaKg", estoqueRealKg);

            List<Map<String, String>> historicoCaixa = googleSheetsService.listarFechamentosCaixa();
            model.addAttribute("historicoCaixa", historicoCaixa);
            model.addAttribute("dataHojeString", dataHoje);

            Map<String, BigDecimal> mapaPrecos = todosMateriais.stream()
                    .collect(Collectors.toMap(Material::getNome, Material::getPrecoPorKg));

            Double patrimonioTotal = 0.0;
            for (Map.Entry<String, Double> entry : estoqueRealKg.entrySet()) {
                BigDecimal precoVenda = mapaPrecos.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                patrimonioTotal += entry.getValue() * precoVenda.doubleValue();
            }

            model.addAttribute("mapaPrecos", mapaPrecos);
            model.addAttribute("patrimonioTotal", patrimonioTotal);

            // ---- Resultado do período (lucro real com custo médio) ----
            PeriodoFinanceiro pf = calcularPeriodoFinanceiro(historico, dataHoje, periodo, de, ate);
            model.addAttribute("fin", pf.resultado);
            model.addAttribute("periodoSel", pf.selecionado);
            model.addAttribute("periodoDe", pf.de.toString());
            model.addAttribute("periodoAte", pf.ate.toString());

            return "analises";

        } catch (Exception e) {
            return "redirect:/admin/coletas?erro=analises";
        }
    }

    /** Resultado financeiro de um período + o período efetivamente usado (já ajustado aos limites). */
    private static final class PeriodoFinanceiro {
        AnaliseFinanceira.Resultado resultado;
        String selecionado;
        LocalDate de, ate;
    }

    private PeriodoFinanceiro calcularPeriodoFinanceiro(List<Oferta> historico, String dataHoje,
                                                        String periodo, String de, String ate) throws IOException {
        LocalDate hojeData = LocalDate.parse(dataHoje);
        LocalDate periodoDe = hojeData, periodoAte = hojeData;
        String periodoSel = periodo == null ? "hoje" : periodo;
        switch (periodoSel) {
            case "7d": periodoDe = hojeData.minusDays(6); break;
            case "30d": periodoDe = hojeData.minusDays(29); break;
            case "mes": periodoDe = hojeData.withDayOfMonth(1); break;
            case "custom":
                try {
                    periodoDe = LocalDate.parse(de);
                    periodoAte = LocalDate.parse(ate);
                } catch (Exception e) {
                    periodoDe = hojeData; periodoAte = hojeData; periodoSel = "hoje";
                }
                break;
            default: periodoSel = "hoje";
        }
        if (periodoAte.isAfter(hojeData)) periodoAte = hojeData;
        if (periodoDe.isAfter(periodoAte)) periodoDe = periodoAte;
        if (java.time.temporal.ChronoUnit.DAYS.between(periodoDe, periodoAte) > 366) periodoDe = periodoAte.minusDays(366);

        List<AnaliseFinanceira.Movimento> movimentos = new ArrayList<>();
        for (Oferta o : historico) {
            if (o.getMaterial() == null || o.getPeso() == null || o.getStatus() == null || o.getData() == null) continue;
            try {
                movimentos.add(new AnaliseFinanceira.Movimento(o.getId(), o.getMaterial(), o.getStatus().name(),
                        BigDecimal.valueOf(o.getPeso()), o.getPrecoEstimado(), LocalDate.parse(o.getData()),
                        o.getUsuario() != null ? o.getUsuario().getNome() : null));
            } catch (Exception e) {
                log.debug("Movimento com data inválida ignorado na análise: id={}", o.getId());
            }
        }
        List<AnaliseFinanceira.DespesaMov> despesasMov = new ArrayList<>();
        for (Despesa d : googleSheetsService.listarDespesasBrutas()) {
            despesasMov.add(new AnaliseFinanceira.DespesaMov(d.getDataDespesa(), d.getValor()));
        }

        PeriodoFinanceiro pf = new PeriodoFinanceiro();
        pf.resultado = AnaliseFinanceira.calcular(movimentos, despesasMov, periodoDe, periodoAte);
        pf.selecionado = periodoSel;
        pf.de = periodoDe;
        pf.ate = periodoAte;
        return pf;
    }

    /** Baixa o resultado do período em CSV (planilha). Mesmas regras de acesso da tela de análises. */
    @GetMapping("/admin/analises/exportar")
    public org.springframework.http.ResponseEntity<byte[]> exportarAnalises(
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate,
            HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return redirecionar("/login");
        if (session.getAttribute("bloqueadoPagamento") != null) return redirecionar("/admin/assinatura");
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return redirecionar("/admin/coletas");

        try {
            String dataHoje = LocalDate.now(ZoneId.of("America/Recife")).toString();
            PeriodoFinanceiro pf = calcularPeriodoFinanceiro(googleSheetsService.getHistoricoCompleto(), dataHoje, periodo, de, ate);
            byte[] corpo = ExportacaoCsv.resultado(pf.resultado).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String nome = "coletae-resultado-" + pf.de + "_a_" + pf.ate + ".csv";
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Type", "text/csv; charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=\"" + nome + "\"")
                    .header("Cache-Control", "no-store")
                    .body(corpo);
        } catch (Exception e) {
            log.error("Falha ao exportar o resultado do período", e);
            return redirecionar("/admin/analises");
        }
    }

    private static org.springframework.http.ResponseEntity<byte[]> redirecionar(String destino) {
        return org.springframework.http.ResponseEntity.status(302).header("Location", destino).build();
    }

    @GetMapping("/admin/historico")
    public String historicoMovimentacoes(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";

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

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache"); response.setDateHeader("Expires", 0);

        try { model.addAttribute("despesas", googleSheetsService.listarDespesas()); }
        catch (Exception e) { model.addAttribute("despesas", new ArrayList<>()); }
        return "admin-despesas";
    }

    @PostMapping("/admin/despesas/nova")
    public String registrarDespesa(@RequestParam String descricao, @RequestParam Double valor, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/admin/coletas";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        try { googleSheetsService.salvarDespesa(descricao, BigDecimal.valueOf(valor)); }
        catch (Exception e) { log.error("Erro despesa", e); }
        return "redirect:/admin/despesas";
    }

    @PostMapping("/admin/despesas/deletar")
    public String deletarDespesa(@RequestParam String id, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/admin/coletas";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try { googleSheetsService.deletarDespesa(id); } catch (Exception e) { log.warn("Falha ignorada", e); }
        return "redirect:/admin/despesas";
    }

    @PostMapping("/admin/fechar-caixa")
    public String fecharCaixaDiario(
            @RequestParam String dataHoje, @RequestParam Double entradas,
            @RequestParam Double saidas, @RequestParam Double despesas,
            @RequestParam Double lucro, @RequestParam Double pesoTotal,
            HttpSession session) {

        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/admin/coletas";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        try {
            googleSheetsService.fecharCaixaDoDia(dataHoje, BigDecimal.valueOf(entradas), BigDecimal.valueOf(saidas), BigDecimal.valueOf(despesas), BigDecimal.valueOf(lucro), pesoTotal);
        } catch (Exception e) {
            log.error("Erro ao fechar caixa", e);
        }

        return "redirect:/admin/analises";
    }

    @GetMapping("/admin/relatorio-gerencial")
    public String gerarRelatorioBalanço(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        try {
            List<Oferta> historico = googleSheetsService.getHistoricoCompleto();
            String dataHoje = LocalDate.now(ZoneId.of("America/Recife")).toString();
            List<Material> todosMateriais = googleSheetsService.listarMateriais();
            Map<String, String> mapaUnidades = todosMateriais.stream().collect(Collectors.toMap(Material::getNome, Material::getUnidade));
            Map<String, BigDecimal> mapaPrecos = todosMateriais.stream().collect(Collectors.toMap(Material::getNome, Material::getPrecoPorKg));

            Double caixaEntradaHoje = 0.0; Double caixaSaidaHoje = 0.0;
            Map<String, Double> estoqueRealKg = new HashMap<>();

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
                    if (isHoje && status.equals("VENDIDO")) caixaSaidaHoje += valor;
                }
            }

            estoqueRealKg.entrySet().removeIf(entry -> entry.getValue() <= 0);

            Double totalDespesasHoje = googleSheetsService.calcularDespesasDoDia(dataHoje);
            Double lucroDoDia = caixaEntradaHoje - caixaSaidaHoje - totalDespesasHoje;

            Double patrimonioTotal = 0.0;
            for (Map.Entry<String, Double> entry : estoqueRealKg.entrySet()) {
                BigDecimal precoVenda = mapaPrecos.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                patrimonioTotal += entry.getValue() * precoVenda.doubleValue();
            }

            model.addAttribute("dataRelatorio", LocalDate.now(ZoneId.of("America/Recife")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            model.addAttribute("nomeArmazem", session.getAttribute("nomeArmazem"));
            model.addAttribute("caixaEntradaHoje", caixaEntradaHoje);
            model.addAttribute("caixaSaidaHoje", caixaSaidaHoje);
            model.addAttribute("totalDespesasHoje", totalDespesasHoje);
            model.addAttribute("lucroDoDia", lucroDoDia);
            model.addAttribute("estoqueReal", estoqueRealKg);
            model.addAttribute("mapaUnidades", mapaUnidades);
            model.addAttribute("mapaPrecos", mapaPrecos);
            model.addAttribute("patrimonioTotal", patrimonioTotal);
            model.addAttribute("historicoCaixa", googleSheetsService.listarFechamentosCaixa());

            return "admin-relatorio";
        } catch (Exception e) {
            return "redirect:/admin/analises?erro=relatorio";
        }
    }


    // =========================================================================
    // 7. GESTÃO DE EQUIPE E PERFIL (RESTRITO GESTOR)
    // =========================================================================

    @GetMapping("/admin/operadores")
    public String telaOperadores(Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/admin/coletas";
        }

        model.addAttribute("operadores", googleSheetsService.listarFuncionarios());
        return "admin-operadores";
    }

    @PostMapping("/admin/operadores/novo")
    public String novoOperador(@RequestParam String nome, @RequestParam String login, @RequestParam String senha, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/login";
        }

        try {
            googleSheetsService.salvarFuncionario(nome, login, senha);
        } catch (Exception e) {
            log.error("Erro ao criar funcionário. Login talvez já exista", e);
            return "redirect:/admin/operadores?erro=login_duplicado";
        }
        return "redirect:/admin/operadores";
    }

    @PostMapping("/admin/operadores/status")
    public String alterarStatusOperador(@RequestParam Long id, @RequestParam boolean ativo, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/login";
        }

        try {
            googleSheetsService.alterarStatusFuncionario(id, ativo);
        } catch (Exception e) {
            log.error("Erro inesperado", e);
        }
        return "redirect:/admin/operadores";
    }

    @PostMapping("/admin/perfil/atualizar-senhas")
    public String atualizarSenhasPerfil(@RequestParam String senhaAtual,
                                        @RequestParam(required = false) String novaSenha,
                                        HttpSession session,
                                        org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {

        if (session.getAttribute("adminLogado") == null) return "redirect:/login";

        if (!(session.getAttribute("parceiroId") instanceof Long parceiroId)) {
            ra.addFlashAttribute("erroPerfil", "Somente o dono da conta pode alterar a senha. Entre pela sua conta e tente de novo.");
            return "redirect:/admin/coletas";
        }
        if (novaSenha == null || novaSenha.isBlank()) {
            ra.addFlashAttribute("sucessoPerfil", "Nenhuma alteração: a nova senha estava em branco.");
            return "redirect:/admin/coletas";
        }

        String chave = "senha:" + parceiroId;
        if (throttle.bloqueado(chave)) {
            ra.addFlashAttribute("erroPerfil", "Muitas tentativas. Aguarde alguns minutos.");
            return "redirect:/admin/coletas";
        }
        try {
            if (parceiroService.alterarSenha(parceiroId, senhaAtual, novaSenha)) {
                throttle.sucesso(chave);
                ra.addFlashAttribute("sucessoPerfil", "Senha atualizada com sucesso!");
            } else {
                throttle.falhou(chave);
                ra.addFlashAttribute("erroPerfil", "Operação negada: a senha atual informada está incorreta.");
            }
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("erroPerfil", e.getMessage());
        }
        return "redirect:/admin/coletas";
    }


    // =========================================================================
    // 8. PLANO SAAS E SUPORTE
    // =========================================================================

    @GetMapping("/admin/assinatura")
    public String telaAssinaturaSaaS(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String plano = (String) session.getAttribute("planoAssinatura");
        String status = (String) session.getAttribute("statusAssinatura");

        double valor = "PRO".equals(plano) ? 199.90 : 149.90;
        String planoNome = "PRO".equals(plano) ? "Coletaê SaaS Pro" : "Coletaê SaaS Start";

        model.addAttribute("planoNome", planoNome);
        model.addAttribute("valorMensalidade", valor);
        model.addAttribute("statusAssinatura", status != null ? status : "TRIAL");
        model.addAttribute("proximoVencimento", LocalDate.now(ZoneId.of("America/Recife")).plusDays(7).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        model.addAttribute("faturas", new ArrayList<>());
        return "admin-assinatura";
    }

    private static final Set<String> EXTENSOES_ANEXO = Set.of("png", "jpg", "jpeg", "webp", "gif", "pdf");
    private static final Set<String> TIPOS_ANEXO = Set.of("image/png", "image/jpeg", "image/webp", "image/gif", "application/pdf");

    private static boolean anexoPermitido(org.springframework.web.multipart.MultipartFile f) {
        String nome = f.getOriginalFilename() == null ? "" : f.getOriginalFilename().toLowerCase();
        int ponto = nome.lastIndexOf('.');
        String ext = ponto >= 0 ? nome.substring(ponto + 1) : "";
        String tipo = f.getContentType() == null ? "" : f.getContentType().toLowerCase();
        return EXTENSOES_ANEXO.contains(ext) && TIPOS_ANEXO.contains(tipo);
    }

    private static String nomeAnexoSeguro(String original) {
        String n = original == null ? "anexo" : original.replaceAll("[^A-Za-z0-9._-]", "_");
        return n.length() > 80 ? n.substring(n.length() - 80) : n;
    }

    @PostMapping("/admin/suporte/enviar")
    public String enviarChamado(
            @RequestParam String assunto,
            @RequestParam String prioridade,
            @RequestParam String descricao,
            @RequestParam(required = false) String emailRetorno,
            @RequestParam(required = false) org.springframework.web.multipart.MultipartFile printTela,
            jakarta.servlet.http.HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (printTela != null && !printTela.isEmpty() && !anexoPermitido(printTela)) {
            redirectAttributes.addFlashAttribute("erroSuporte", "Anexo não permitido. Envie apenas imagem (PNG, JPG, WEBP, GIF) ou PDF de até 5 MB.");
            return "redirect:/admin/coletas";
        }
        // evita quebra de linha no assunto do e-mail (injeção de cabeçalho)
        assunto = assunto.replaceAll("[\\r\\n]+", " ").trim();
        prioridade = prioridade.replaceAll("[\\r\\n]+", " ").trim();
        try {
            String nomeArmazem = (String) session.getAttribute("nomeArmazem");
            String telefoneAdmin = (String) session.getAttribute("telefone");

            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(emailSuporte);
            helper.setSubject("[CHAMADO " + prioridade.toUpperCase() + "] " + assunto);

            if (emailRetorno != null && !emailRetorno.trim().isEmpty()) {
                helper.setReplyTo(emailRetorno.replaceAll("[\\r\\n]+", "").trim());
            }

            String linkZap = "<em>Telefone não encontrado na sessão.</em>";
            if (telefoneAdmin != null && !telefoneAdmin.isEmpty()) {
                String numeroLimpo = telefoneAdmin.replaceAll("[^0-9]", "");
                linkZap = "<a href='https://wa.me/55" + numeroLimpo + "' style='background-color:#25D366; color:white; padding:10px 15px; text-decoration:none; border-radius:5px; font-weight:bold; display:inline-block;'>🟢 Responder via WhatsApp</a>";
            }

            String corpoHTML = "<h2 style='color:#198754;'>Novo Chamado Técnico - Coletaê</h2>"
                    + "<p><b>Armazém:</b> " + (nomeArmazem != null ? org.springframework.web.util.HtmlUtils.htmlEscape(nomeArmazem) : "Desconhecido") + "</p>"
                    + "<p><b>E-mail de Retorno:</b> " + (emailRetorno != null && !emailRetorno.isEmpty() ? org.springframework.web.util.HtmlUtils.htmlEscape(emailRetorno) : "Não informado") + "</p>"
                    + "<p><b>Descrição do Problema:</b><br>" + org.springframework.web.util.HtmlUtils.htmlEscape(descricao).replace("\n", "<br>") + "</p>"
                    + "<hr style='border:1px solid #eee; margin:20px 0;'>"
                    + "<p>" + linkZap + "</p>";

            helper.setText(corpoHTML, true);

            if (printTela != null && !printTela.isEmpty()) {
                helper.addAttachment(nomeAnexoSeguro(printTela.getOriginalFilename()), printTela);
            }

            mailSender.send(message);
            redirectAttributes.addFlashAttribute("sucessoSuporte", "Chamado enviado com sucesso! Nossa equipe técnica analisará o caso.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erroSuporte", "Ocorreu um erro ao enviar seu chamado. Tente novamente ou chame no WhatsApp.");
            log.error("Erro inesperado", e);
        }

        return "redirect:/admin/coletas";
    }


    // =========================================================================
    // 9. ÁREA RESTRITA DO MASTER (O SEU PAINEL DE ADMIN DO SAAS)
    // =========================================================================

    @GetMapping("/master/painel")
    public String telaPainelMaster(Model model, HttpSession session) {
        if (session.getAttribute("masterLogado") == null) {
            return "redirect:/login";
        }

        try {
            List<Map<String, Object>> clientesBanco = (List<Map<String, Object>>) (List<?>) googleSheetsService.listarTodosArmazensSaaS();
            int totalClientes = clientesBanco.size();
            int ativos = 0; int trials = 0; int bloqueados = 0;
            int ativosStart = 0; int ativosPro = 0;
            int trialsStart = 0; int trialsPro = 0;
            double faturamentoMensal = 0.0;

            List<Armazem> clientesFormatados = new ArrayList<>();

            for (Map<String, Object> c : clientesBanco) {
                String status = c.get("status") != null ? c.get("status").toString().toUpperCase() : "ATIVO";
                String plano = c.get("plano") != null ? c.get("plano").toString().toUpperCase() : "START";

                if ("ATIVO".equals(status)) {
                    ativos++;
                    if ("PRO".equals(plano)) { ativosPro++; faturamentoMensal += 199.90; }
                    else { ativosStart++; faturamentoMensal += 149.90; }
                } else if ("TRIAL".equals(status)) {
                    trials++;
                    if ("PRO".equals(plano)) { trialsPro++; } else { trialsStart++; }
                } else if ("BLOQUEADO".equals(status) || "SUSPENSO".equals(status)) {
                    bloqueados++;
                }

                Armazem a = new Armazem();
                a.setId(c.get("id") != null ? Long.parseLong(c.get("id").toString()) : 0L);
                a.setNome(c.get("nome") != null ? c.get("nome").toString() : "Sem Nome");
                a.setPlano(plano);
                a.setTelefone(c.get("telefone") != null ? c.get("telefone").toString() : "");
                a.setCnpj(c.get("cnpj") != null ? c.get("cnpj").toString() : "");
                a.setLogin(c.get("login") != null ? c.get("login").toString() : "");
                a.setStatus(status);

                clientesFormatados.add(a);
            }

            double volumeGmv = 0.0;
            double volumeToneladas = 0.0;
            int churnMes = bloqueados;
            int clientesEmRisco = 0;

            try {
                List<Oferta> todasAsTransacoes = googleSheetsService.getHistoricoCompleto();
                String mesAtual = LocalDate.now(ZoneId.of("America/Recife")).toString().substring(0, 7);

                for (Oferta o : todasAsTransacoes) {
                    if (o.getData() != null && o.getData().startsWith(mesAtual)) {
                        if (o.getPrecoEstimado() != null) { volumeGmv += o.getPrecoEstimado().doubleValue(); }
                        if (o.getPeso() != null) { volumeToneladas += (o.getPeso() / 1000.0); }
                    }
                }
            } catch (Exception e) {
                log.info("BI: ainda sem transações suficientes ou erro de leitura.");
            }

            model.addAttribute("listaClientes", clientesFormatados);
            model.addAttribute("totalClientes", totalClientes);
            model.addAttribute("ativos", ativos);
            model.addAttribute("trials", trials);
            model.addAttribute("bloqueados", bloqueados);
            model.addAttribute("ativosStart", ativosStart);
            model.addAttribute("ativosPro", ativosPro);
            model.addAttribute("trialsStart", trialsStart);
            model.addAttribute("trialsPro", trialsPro);
            model.addAttribute("faturamentoMensal", faturamentoMensal);
            model.addAttribute("volumeGmv", volumeGmv);
            model.addAttribute("volumeToneladas", volumeToneladas);
            model.addAttribute("churnMes", churnMes);
            model.addAttribute("clientesEmRisco", clientesEmRisco);

        } catch (Exception e) {
            log.error("Erro ao carregar o painel master", e);
            model.addAttribute("listaClientes", new ArrayList<>());
        }

        return "master-painel";
    }

    @PostMapping("/master/login-secreto")
    public String loginMaster(@RequestParam String senhaMaster, HttpSession session) {
        if (senhaMasterValida(senhaMaster)) {
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


    // =========================================================================
    // 10. UTILITÁRIOS E CLASSES AUXILIARES (DTOs)
    // =========================================================================

    private boolean isCpfCnpjValido(String documento) {
        if (documento == null) return false;

        String docLimpo = documento.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();

        if (docLimpo.length() == 11) {
            if (docLimpo.matches("(\\d)\\1{10}")) return false;
            int soma = 0, peso = 10;
            for (int i = 0; i < 9; i++) soma += (docLimpo.charAt(i) - '0') * peso--;
            int digito1 = 11 - (soma % 11);
            if (digito1 > 9) digito1 = 0;
            soma = 0; peso = 11;
            for (int i = 0; i < 10; i++) soma += (docLimpo.charAt(i) - '0') * peso--;
            int digito2 = 11 - (soma % 11);
            if (digito2 > 9) digito2 = 0;
            return (docLimpo.charAt(9) - '0' == digito1) && (docLimpo.charAt(10) - '0' == digito2);

        } else if (docLimpo.length() == 14) {
            if (!docLimpo.substring(12, 14).matches("\\d\\d")) return false;

            int[] valores = new int[14];
            for (int i = 0; i < 14; i++) {
                char c = docLimpo.charAt(i);
                if (Character.isDigit(c)) { valores[i] = c - '0'; }
                else if (Character.isLetter(c)) { valores[i] = c - 'A' + 17; }
                else { return false; }
            }

            int soma = 0;
            int[] peso1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
            for (int i = 0; i < 12; i++) { soma += valores[i] * peso1[i]; }
            int resto = soma % 11;
            int digito1 = resto < 2 ? 0 : 11 - resto;

            soma = 0;
            int[] peso2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
            for (int i = 0; i < 12; i++) { soma += valores[i] * peso2[i]; }
            soma += digito1 * 2;
            resto = soma % 11;
            int digito2 = resto < 2 ? 0 : 11 - resto;

            return (valores[12] == digito1) && (valores[13] == digito2);
        }
        return false;
    }

    public static class PreVendaDTO {
        public Material material; public Double peso; public BigDecimal total;
        public PreVendaDTO(Material m, Double p, BigDecimal t) { this.material = m; this.peso = p; this.total = t; }
    }

    public static class RankingDTO {
        public String nome; public Double peso; public String unidade;
        public RankingDTO(String n, Double p, String u) { this.nome = n; this.peso = p; this.unidade = u; }
    }
}