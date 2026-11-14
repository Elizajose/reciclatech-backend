package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.*;
import com.reciclatech.backend.repository.*;
import com.reciclatech.backend.service.AnaliseFinanceira;
import com.reciclatech.backend.service.DatabaseService;
import com.reciclatech.backend.service.LimitadorPublico;
import com.reciclatech.backend.service.LoginThrottle;
import com.reciclatech.backend.service.ParceiroService;
import com.reciclatech.backend.util.BuscaCliente;
import com.reciclatech.backend.util.CodigoArmazem;
import com.reciclatech.backend.util.Assinatura;
import com.reciclatech.backend.util.DocumentoValidator;
import com.reciclatech.backend.util.ExportacaoCsv;
import com.reciclatech.backend.util.Impressao;
import com.reciclatech.backend.util.Valores;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
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
        if (session.getAttribute("adminLogado") == null && session.getAttribute("parceiroId") instanceof Long) {
            return "redirect:/conta/armazens"; // dono já entrou: escolher o armazém
        }
        if (session.getAttribute("adminLogado") != null) {
            if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
            return "redirect:/admin/coletas";
        }
        return "login-admin";
    }

    /** Remove da sessão tudo que identifica um login anterior (dono ou operador) antes de gravar o novo. */
    private static void limparIdentidade(HttpSession session) {
        for (String k : new String[]{"adminLogado", "perfilUser", "idPlanilhaAtiva", "nomeArmazem", "cnpjArmazem", "telefone",
                "enderecoArmazem", "nomeFuncionarioLogado", "planoAssinatura", "statusAssinatura", "bloqueadoPagamento",
                "parceiroId", "parceiroNome", "origemLogin", "credId", "credFp", "atendenteNome", "validadoEm", "masterLogado"}) {
            session.removeAttribute(k);
        }
    }

    /**
     * Entrada única. Aba "dono": e-mail + senha. Aba "operador": código do armazém + usuário + senha.
     * Sem o campo "tipo" (formulário antigo), um texto com "@" é tratado como e-mail do dono.
     * Armazéns antigos (sem dono vinculado) entram aqui também, só com o usuário e a senha deles.
     */
    @PostMapping("/login-admin")
    public String login(@RequestParam String login, @RequestParam String senha,
                        @RequestParam(required = false) String tipo,
                        @RequestParam(required = false) String codigo,
                        jakarta.servlet.http.HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String loginLimpo = login.trim();
        boolean dono = "DONO".equalsIgnoreCase(tipo) || (tipo == null && loginLimpo.contains("@"));
        String aba = dono ? "dono" : "operador";
        String codigoNorm = CodigoArmazem.normalizar(codigo);
        // dono compartilha o limite de tentativas com o fluxo antigo de conta; operador conta por armazém + usuário
        String chaveLogin = dono ? "login:" + loginLimpo : "op:" + codigoNorm + ":" + loginLimpo;
        if (throttle.bloqueado(chaveLogin)) return "redirect:/login?erro=tentativas&aba=" + aba;
        if ("master".equals(login) && senhaMasterValida(senha)) {
            request.changeSessionId(); // evita fixação de sessão
            limparIdentidade(session);
            session.setAttribute("masterLogado", true);
            return "redirect:/master/painel";
        }

        if (dono) {
            Optional<Parceiro> achado = parceiroService.autenticar(loginLimpo, senha);
            if (achado.isPresent()) {
                throttle.sucesso(chaveLogin);
                request.changeSessionId(); // evita fixação de sessão
                limparIdentidade(session);
                session.setAttribute("parceiroId", achado.get().getId());
                session.setAttribute("parceiroNome", achado.get().getNome());
                session.setAttribute("credFp", Impressao.de(achado.get().getSenhaHash())); // troca de senha encerra as sessões antigas
                return "redirect:/conta/armazens";
            }
            throttle.falhou(chaveLogin);
            return "redirect:/login?erro=true&aba=dono";
        }

        try {
            Map<String, String> dadosUser = codigoNorm.isEmpty() ? null : googleSheetsService.autenticarOperador(codigoNorm, loginLimpo, senha);
            if (dadosUser == null) {
                // armazém antigo, sem dono vinculado: continua entrando com o usuário/senha dele (código é opcional)
                dadosUser = googleSheetsService.autenticarArmazemAntigo(codigoNorm, loginLimpo, senha);
            }
            if (dadosUser != null) {
                throttle.sucesso(chaveLogin);
                request.changeSessionId(); // evita fixação de sessão
                limparIdentidade(session);
                return abrirSessaoOperacao(session, dadosUser);
            }
            if (codigoNorm.isEmpty()) return "redirect:/login?erro=codigo&aba=operador";
        } catch (RuntimeException e) {
            return "redirect:/login?erro=suspenso&aba=operador";
        } catch (Exception e) {
            log.error("Erro inesperado", e);
        }
        throttle.falhou(chaveLogin);
        return "redirect:/login?erro=true&aba=operador";
    }

    /** Grava na sessão tudo que as telas /admin precisam (operador ou armazém antigo) e decide para onde ir. */
    private String abrirSessaoOperacao(HttpSession session, Map<String, String> dadosUser) {
        session.setAttribute("adminLogado", true);
        session.setAttribute("perfilUser", dadosUser.get("perfil"));
        session.setAttribute("idPlanilhaAtiva", dadosUser.get("idPlanilha"));
        session.setAttribute("nomeArmazem", dadosUser.getOrDefault("nomeArmazem", "Armazém Parceiro"));
        session.setAttribute("cnpjArmazem", dadosUser.getOrDefault("cnpj", ""));
        session.setAttribute("telefone", dadosUser.getOrDefault("telefone", ""));
        session.setAttribute("enderecoArmazem", dadosUser.getOrDefault("endereco", "Endereço não informado"));
        // credenciais para a revalidação periódica da sessão (ValidadorSessao): nunca o hash em si
        session.setAttribute("origemLogin", dadosUser.getOrDefault("origem", "FUNC"));
        session.setAttribute("credId", Long.valueOf(dadosUser.getOrDefault("credId", dadosUser.get("idPlanilha"))));
        session.setAttribute("credFp", dadosUser.getOrDefault("credFp", ""));

        String nomeReal = dadosUser.getOrDefault("nomeFuncionarioLogado", "Operador de Balcão");
        session.setAttribute("atendenteNome", nomeReal); // quem aparece como "em atendimento" na fila
        // DEFINIÇÃO INTELIGENTE DO RESPONSÁVEL:
        session.setAttribute("nomeFuncionarioLogado", "GESTOR".equals(dadosUser.get("perfil")) ? "Gestor / Administrador" : nomeReal);

        String status = dadosUser.getOrDefault("status", "ATIVO");
        session.setAttribute("planoAssinatura", dadosUser.getOrDefault("plano", "START"));
        session.setAttribute("statusAssinatura", status);

        boolean bloqueado = "BLOQUEADO".equals(status) || "SUSPENSO".equals(status) || "true".equals(dadosUser.getOrDefault("trialVencido", "false"));
        if (bloqueado) {
            session.setAttribute("bloqueadoPagamento", true);
            return "redirect:/admin/assinatura";
        }
        session.removeAttribute("bloqueadoPagamento");
        return "redirect:/admin/coletas";
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
    public String pesagemRapida(@RequestParam String nome, @RequestParam(required = false) String cpf, HttpSession session, RedirectAttributes ra) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        String nomeLimpo = Valores.texto(nome, 120);
        if (nomeLimpo == null) {
            ra.addFlashAttribute("erroTela", "Informe o nome do cliente (até 120 letras).");
            return "redirect:/admin/coletas";
        }
        String cpfPadronizado = "";
        if (cpf != null && !cpf.trim().isEmpty()) {
            if (!DocumentoValidator.valido(cpf)) {
                ra.addFlashAttribute("erroTela", "CPF inválido. Confira os números ou deixe em branco.");
                return "redirect:/admin/coletas";
            }
            cpfPadronizado = DocumentoValidator.formatar(cpf);
        }

        try {
            // Cliente de balcão não tem telefone: a chave é o número (id) gerado pelo banco, nunca mais a hora do relógio.
            Usuario usuarioBalcao = new Usuario();
            usuarioBalcao.setNome(nomeLimpo);
            usuarioBalcao.setCpf(cpfPadronizado);
            usuarioBalcao.setEndereco("Atendimento Avulso");
            usuarioBalcao.setTipo(Usuario.TipoUsuario.CATADOR);

            Usuario criado = googleSheetsService.salvarSolicitacaoInicial(usuarioBalcao, "Atendimento Avulso");
            googleSheetsService.iniciarAtendimento(criado.getId().toString(), true);
            return "redirect:/admin/atender/" + criado.getId();
        } catch (Exception e) {
            log.error("Erro pesagem rápida", e);
            return "redirect:/admin/coletas?erro=pesagem_rapida";
        }
    }

    /** Abre a tela de pesagem. Só LÊ: quem muda o status é o POST "iniciar" abaixo (um GET não deve alterar dados). */
    @GetMapping("/admin/atender/{idUsuario}")
    public String telaChecklist(@PathVariable String idUsuario, Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache"); response.setDateHeader("Expires", 0);

        try {
            Usuario vendedor = googleSheetsService.listarUsuarios().stream()
                    .filter(u -> BuscaCliente.corresponde(u, idUsuario))
                    .findFirst().orElseThrow();
            if (!googleSheetsService.temPedidoAberto(idUsuario)) return "redirect:/admin/coletas?aviso=ja_finalizado";

            model.addAttribute("vendedor", vendedor);
            model.addAttribute("todosMateriais", googleSheetsService.listarMateriais());
            model.addAttribute("precosVip", googleSheetsService.buscarPrecosEspeciais(vendedor.getCpf()));

        } catch (Exception e) {
            log.error("Erro ao carregar checklist", e);
            return "redirect:/admin/coletas?erro=usuario";
        }
        return "admin-checklist";
    }

    /** Começa/retoma o atendimento. Se outro operador já está com o cliente, avisa quem é (e só toma se confirmar "assumir"). */
    @PostMapping("/admin/atender/{idUsuario}/iniciar")
    public String iniciarAtendimento(@PathVariable String idUsuario, @RequestParam(defaultValue = "false") boolean assumir,
                                     HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try {
            DatabaseService.InicioAtendimento r = googleSheetsService.iniciarAtendimento(idUsuario, assumir);
            if (r.ok) return "redirect:/admin/atender/" + idUsuario;
            if (r.ocupadoPor != null) {
                ra.addFlashAttribute("avisoTela", "Este cliente já está sendo atendido por " + r.ocupadoPor
                        + ". Se tiver certeza, use \"ASSUMIR\" na lista.");
                return "redirect:/admin/coletas";
            }
            return "redirect:/admin/coletas?aviso=ja_finalizado";
        } catch (Exception e) {
            log.error("Erro ao iniciar atendimento", e);
            return "redirect:/admin/coletas?erro=usuario";
        }
    }

    @PostMapping("/admin/revisar-coleta")
    public String revisarColeta(@RequestParam String idVendedor, @RequestParam Map<String, String> params, Model model, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try {
            Usuario vendedor = googleSheetsService.listarUsuarios().stream()
                    .filter(u -> BuscaCliente.corresponde(u, idVendedor))
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
                                // CÓPIA só para exibir o preço (VIP ou base): nunca altera a entidade do banco
                                Material paraTela = new Material();
                                paraTela.setId(mat.getId());
                                paraTela.setNome(mat.getNome());
                                paraTela.setUnidade(mat.getUnidade());
                                paraTela.setPrecoPorKg(precoBase);

                                itensRevisao.add(new PreVendaDTO(paraTela, quantidade, totalItem));
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
                                       @RequestParam(required = false) List<Double> precosFinais, HttpSession session, RedirectAttributes ra) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try {
            if (idsMateriais == null || idsMateriais.isEmpty()) return "redirect:/admin/coletas?erro=sem_materiais";
            if (pesosFinais == null || precosFinais == null
                    || pesosFinais.size() != idsMateriais.size() || precosFinais.size() != idsMateriais.size()) {
                ra.addFlashAttribute("erroTela", "Os dados da pesagem vieram incompletos. Nada foi gravado; refaça a pesagem.");
                return "redirect:/admin/coletas";
            }
            String cpfGravar = null;
            if (cpfFinal != null && !cpfFinal.trim().isEmpty()) {
                if (!DocumentoValidator.valido(cpfFinal)) {
                    ra.addFlashAttribute("erroTela", "CPF inválido. Nada foi gravado; refaça a pesagem com o CPF correto (ou sem CPF).");
                    return "redirect:/admin/coletas";
                }
                cpfGravar = DocumentoValidator.formatar(cpfFinal);
            }

            List<Material> todosMateriais = googleSheetsService.listarMateriais();
            List<List<Object>> loteDeVendas = new ArrayList<>();

            for (int i = 0; i < idsMateriais.size(); i++) {
                Long idMat = idsMateriais.get(i);
                Material mat = todosMateriais.stream().filter(m -> m.getId().equals(idMat)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Um dos materiais não existe mais neste armazém."));
                BigDecimal peso = Valores.peso(pesosFinais.get(i));
                BigDecimal precoUn = Valores.preco(precosFinais.get(i));
                if (peso == null) throw new IllegalArgumentException("Peso inválido para \"" + mat.getNome() + "\": use um valor maior que zero.");
                if (precoUn == null) throw new IllegalArgumentException("Preço inválido para \"" + mat.getNome() + "\": use um valor maior que zero.");

                String idVendaUnico = UUID.randomUUID().toString().substring(0, 13).toUpperCase();
                List<Object> row = Arrays.asList(
                        idVendaUnico, mat.getNome(), peso.doubleValue(), "ENTREGA NO LOCAL",
                        precoUn, com.reciclatech.backend.util.RegrasVenda.total(precoUn, peso), LocalDate.now(ZoneId.of("America/Recife")).toString(),
                        idVendedor, "VENDIDO", cpfGravar != null ? cpfGravar : "NÃO INFORMADO"
                );
                loteDeVendas.add(row);
            }
            // o serviço confere de novo (material do armazém, teto de preço sobre a tabela/VIP) e recalcula os totais
            boolean gravou = googleSheetsService.finalizarAtendimento(idVendedor, cpfGravar, loteDeVendas);
            if (!gravou) return "redirect:/admin/coletas?aviso=ja_finalizado";
            return "redirect:/extrato/" + idVendedor;
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("erroTela", e.getMessage() + " Nada foi gravado.");
            return "redirect:/admin/coletas";
        } catch (Exception e) {
            log.error("Erro ao finalizar coleta", e);
            return "redirect:/admin/coletas?erro=venda";
        }
    }

    @PostMapping("/admin/cancelar")
    public String cancelarColeta(@RequestParam String id, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
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
            Usuario usuario = googleSheetsService.listarUsuarios().stream().filter(u -> BuscaCliente.corresponde(u, id)).findFirst().orElse(null);
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

            model.addAttribute("mapaUnidades", mapaUnidades(mats));
            model.addAttribute("mapaPrecos", mapaPrecos(mats));
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
    public String registrarSaidaLote(@RequestParam String nomeIndustria, @RequestParam(required = false) String cnpjIndustria, @RequestParam Map<String, String> params, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/admin/coletas";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        try {
            Map<Long, BigDecimal> pesos = new LinkedHashMap<>();
            Map<Long, BigDecimal> precos = new HashMap<>();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (!e.getKey().startsWith("peso_") || e.getValue() == null || e.getValue().isBlank()) continue;
                Long idMat = Long.valueOf(e.getKey().substring(5));
                BigDecimal peso = Valores.lerDecimal(e.getValue());
                if (peso == null || peso.signum() < 0) throw new IllegalArgumentException("Quantidade inválida informada.");
                if (peso.signum() == 0) continue; // zero = material não vai nesta saída
                pesos.put(idMat, peso);
                BigDecimal preco = Valores.lerDecimal(params.get("preco_" + idMat));
                if (preco != null) precos.put(idMat, preco);
            }
            Long idDestinatario = googleSheetsService.registrarSaidaIndustria(nomeIndustria, cnpjIndustria, pesos, precos);
            return "redirect:/extrato/" + idDestinatario;
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("erroEstoque", e.getMessage());
            return "redirect:/admin/saida";
        } catch (Exception e) {
            log.error("Erro ao registrar saída", e);
            ra.addFlashAttribute("erroEstoque", "Ocorreu um erro interno ao processar a saída. Nada foi gravado.");
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
    public String salvarAjusteEstoque(@RequestParam String material, @RequestParam Double peso, @RequestParam String tipoAjuste, @RequestParam String motivo, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try {
            googleSheetsService.salvarAjusteEstoque(material, peso, tipoAjuste, motivo);
            ra.addFlashAttribute("sucessoTela", "Ajuste de estoque registrado.");
            return "redirect:/admin/analises";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("erroTela", e.getMessage());
        } catch (Exception e) {
            log.error("Erro no ajuste de estoque", e);
            ra.addFlashAttribute("erroTela", "Não foi possível registrar o ajuste. Nada foi gravado.");
        }
        return "redirect:/admin/ajuste-estoque";
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

    /** Resposta padrão das telas de cadastro: grava mensagem para a próxima tela e volta. */
    private String voltarComAviso(RedirectAttributes ra, String destino, String sucesso, Exception erro) {
        if (erro == null) {
            if (sucesso != null) ra.addFlashAttribute("sucessoTela", sucesso);
        } else if (erro instanceof IllegalArgumentException) {
            ra.addFlashAttribute("erroTela", erro.getMessage());
        } else {
            log.error("Erro ao salvar", erro);
            ra.addFlashAttribute("erroTela", "Não foi possível salvar. Nada foi alterado; tente de novo.");
        }
        return "redirect:" + destino;
    }

    @PostMapping("/admin/material/novo")
    public String novoMaterial(@RequestParam String nome, @RequestParam String unidade, @RequestParam Double preco, HttpSession session, RedirectAttributes ra) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";

        try {
            BigDecimal precoValido = Valores.preco(preco);
            if (precoValido == null) throw new IllegalArgumentException("Informe um preço maior que zero.");
            Material m = new Material(); m.setNome(nome); m.setUnidade(unidade); m.setPrecoPorKg(precoValido);
            googleSheetsService.salvarMaterial(m);
            return voltarComAviso(ra, "/admin/precos", "Material cadastrado.", null);
        } catch (Exception e) {
            return voltarComAviso(ra, "/admin/precos", null, e);
        }
    }

    @PostMapping("/admin/atualizar")
    public String upd(@RequestParam Long id, @RequestParam Double novoPreco, HttpSession session, RedirectAttributes ra) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        try {
            BigDecimal precoValido = Valores.preco(novoPreco);
            if (precoValido == null) throw new IllegalArgumentException("Informe um preço maior que zero.");
            googleSheetsService.atualizarPrecoMaterial(id, precoValido);
            return voltarComAviso(ra, "/admin/precos", "Preço atualizado.", null);
        } catch (Exception e) {
            return voltarComAviso(ra, "/admin/precos", null, e);
        }
    }

    @PostMapping("/admin/material/deletar")
    public String deletarMaterial(@RequestParam Long id, HttpSession session, RedirectAttributes ra) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        try {
            googleSheetsService.deletarMaterial(id);
            return voltarComAviso(ra, "/admin/precos", "Material excluído.", null);
        } catch (Exception e) {
            return voltarComAviso(ra, "/admin/precos", null, e);
        }
    }

    /** Histórico de alterações de preços e despesas: somente leitura, somente para o dono (GESTOR). */
    @GetMapping("/admin/historico-alteracoes")
    public String historicoAlteracoes(@RequestParam(required = false) String tipo, Model model, HttpSession s, HttpServletResponse response) {
        if (s.getAttribute("adminLogado") == null) return "redirect:/login";
        if (s.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) s.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String entidade = null;
        if ("MATERIAL".equals(tipo) || "DESPESA".equals(tipo) || "PRECO_VIP".equals(tipo) || "ESTOQUE".equals(tipo)) entidade = tipo;
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
    public String salvarFornecedorVip(@RequestParam String cpf, @RequestParam String material, @RequestParam Double preco, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try {
            BigDecimal precoValido = Valores.preco(preco);
            if (precoValido == null) throw new IllegalArgumentException("Informe um preço maior que zero.");
            googleSheetsService.salvarFornecedorVip(cpf, material, precoValido);
            return voltarComAviso(ra, "/admin/fornecedores-vip", "Preço VIP salvo.", null);
        } catch (Exception e) {
            return voltarComAviso(ra, "/admin/fornecedores-vip", null, e);
        }
    }

    @PostMapping("/admin/fornecedores-vip/deletar")
    public String deletarFornecedorVip(@RequestParam String cpf, @RequestParam String material, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try {
            googleSheetsService.deletarFornecedorVip(cpf, material);
            return voltarComAviso(ra, "/admin/fornecedores-vip", "Preço VIP removido.", null);
        } catch (Exception e) {
            return voltarComAviso(ra, "/admin/fornecedores-vip", null, e);
        }
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
            Map<String, String> mapaUnidades = mapaUnidades(todosMateriais);
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

            Map<String, BigDecimal> mapaPrecos = mapaPrecos(todosMateriais);

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
    public String registrarDespesa(@RequestParam String descricao, @RequestParam Double valor, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/admin/coletas";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        try {
            BigDecimal valorValido = Valores.dinheiro(valor);
            if (valorValido == null) throw new IllegalArgumentException("Informe um valor maior que zero.");
            googleSheetsService.salvarDespesa(descricao, valorValido);
            return voltarComAviso(ra, "/admin/despesas", "Despesa registrada.", null);
        } catch (Exception e) {
            return voltarComAviso(ra, "/admin/despesas", null, e);
        }
    }

    @PostMapping("/admin/despesas/deletar")
    public String deletarDespesa(@RequestParam String id, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/admin/coletas";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try {
            googleSheetsService.deletarDespesa(id);
            return voltarComAviso(ra, "/admin/despesas", "Despesa excluída.", null);
        } catch (Exception e) {
            return voltarComAviso(ra, "/admin/despesas", null, e);
        }
    }

    /**
     * Fecha o caixa de um dia. Só a DATA vem da tela: entradas, saídas, despesas, lucro e peso são
     * recalculados no servidor com o que está gravado (antes eram campos escondidos que qualquer um podia alterar).
     */
    @PostMapping("/admin/fechar-caixa")
    public String fecharCaixaDiario(@RequestParam String dataHoje, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/admin/coletas";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        try {
            googleSheetsService.fecharCaixaDoDia(dataHoje);
            return voltarComAviso(ra, "/admin/analises", "Caixa do dia fechado.", null);
        } catch (Exception e) {
            return voltarComAviso(ra, "/admin/analises", null, e);
        }
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
            Map<String, String> mapaUnidades = mapaUnidades(todosMateriais);
            Map<String, BigDecimal> mapaPrecos = mapaPrecos(todosMateriais);

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
        model.addAttribute("codigoArmazem", googleSheetsService.obterCodigoArmazemLogado());
        return "admin-operadores";
    }

    @PostMapping("/admin/operadores/novo")
    public String novoOperador(@RequestParam String nome, @RequestParam String login, @RequestParam String senha, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/login";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        try {
            googleSheetsService.salvarFuncionario(nome, login, senha);
        } catch (IllegalArgumentException e) {
            if ("login_duplicado".equals(e.getMessage())) return "redirect:/admin/operadores?erro=login_duplicado";
            ra.addFlashAttribute("erroTela", e.getMessage());
            return "redirect:/admin/operadores";
        } catch (Exception e) {
            log.error("Erro ao criar funcionário", e);
            ra.addFlashAttribute("erroTela", "Não foi possível criar o operador. Tente de novo.");
            return "redirect:/admin/operadores";
        }
        ra.addFlashAttribute("sucessoTela", "Operador criado.");
        return "redirect:/admin/operadores";
    }

    @PostMapping("/admin/operadores/status")
    public String alterarStatusOperador(@RequestParam Long id, @RequestParam boolean ativo, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) {
            return "redirect:/login";
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";

        try {
            googleSheetsService.alterarStatusFuncionario(id, ativo);
            ra.addFlashAttribute("sucessoTela", ativo ? "Operador reativado." : "Operador desativado: ele sai do sistema em instantes.");
        } catch (Exception e) {
            log.error("Erro ao alterar operador", e);
            ra.addFlashAttribute("erroTela", "Não foi possível alterar o operador. Tente de novo.");
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
                session.setAttribute("credFp", parceiroService.impressaoDaSenha(parceiroId)); // esta sessão continua valendo; as outras caem
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
                // Soma direto no banco, de TODOS os armazéns (antes lia o histórico do armazém da sessão: o master não tem, dava sempre 0)
                double[] mes = googleSheetsService.resumoMensalMaster();
                volumeGmv = mes[0];
                volumeToneladas = mes[1];
            } catch (Exception e) {
                log.warn("Painel master: não consegui somar o volume do mês", e);
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
    public String loginMaster(@RequestParam String senhaMaster, jakarta.servlet.http.HttpServletRequest request) {
        String chave = "master:" + request.getRemoteAddr();
        if (throttle.bloqueado(chave)) return "redirect:/login?erro=tentativas";
        if (senhaMasterValida(senhaMaster)) {
            throttle.sucesso(chave);
            HttpSession session = request.getSession(true);
            request.changeSessionId(); // evita fixação de sessão
            limparIdentidade(session);
            session.setAttribute("masterLogado", true);
            return "redirect:/master/painel";
        }
        throttle.falhou(chave);
        return "redirect:/login?erro=true";
    }

    @PostMapping("/master/mudar-status")
    public String mudarStatusCliente(@RequestParam Long id, @RequestParam String status, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("masterLogado") == null) return "redirect:/login";
        try {
            if (googleSheetsService.atualizarStatusArmazem(id, status)) ra.addFlashAttribute("sucessoTela", "Status atualizado.");
            else ra.addFlashAttribute("erroTela", "Armazém não encontrado.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("erroTela", e.getMessage());
        } catch (Exception e) {
            log.error("Erro ao mudar status", e);
            ra.addFlashAttribute("erroTela", "Não foi possível mudar o status.");
        }
        return "redirect:/master/painel";
    }


    // =========================================================================
    // 10. UTILITÁRIOS E CLASSES AUXILIARES (DTOs)
    // =========================================================================

    /** Mapas nome -> unidade/preço. Nome repetido ou campo vazio nunca derruba a tela (antes: IllegalStateException/NPE). */
    static Map<String, String> mapaUnidades(List<Material> materiais) {
        Map<String, String> m = new HashMap<>();
        for (Material mat : materiais) if (mat.getNome() != null) m.putIfAbsent(mat.getNome(), mat.getUnidade() == null ? "KG" : mat.getUnidade());
        return m;
    }

    static Map<String, BigDecimal> mapaPrecos(List<Material> materiais) {
        Map<String, BigDecimal> m = new HashMap<>();
        for (Material mat : materiais) if (mat.getNome() != null) m.putIfAbsent(mat.getNome(), mat.getPrecoPorKg() == null ? BigDecimal.ZERO : mat.getPrecoPorKg());
        return m;
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