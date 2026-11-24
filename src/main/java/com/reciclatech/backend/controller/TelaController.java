package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.*;
import com.reciclatech.backend.repository.*;
import com.reciclatech.backend.service.AnaliseFinanceira;
import com.reciclatech.backend.service.DatabaseService;
import com.reciclatech.backend.service.LimitadorPublico;
import com.reciclatech.backend.service.LoginThrottle;
import com.reciclatech.backend.service.ParceiroService;
import com.reciclatech.backend.service.VariacaoPrecoException;
import com.reciclatech.backend.util.BuscaCliente;
import com.reciclatech.backend.util.CodigoArmazem;
import com.reciclatech.backend.util.Assinatura;
import com.reciclatech.backend.util.DocumentoValidator;
import com.reciclatech.backend.util.Engajamento;
import com.reciclatech.backend.util.ExportacaoCsv;
import com.reciclatech.backend.util.Impressao;
import com.reciclatech.backend.util.PlanoAcesso;
import com.reciclatech.backend.util.PrecoPlano;
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
    @Autowired private com.reciclatech.backend.service.CotacaoPublicaService cotacaoPublicaService;
    @Autowired(required = false) private com.reciclatech.backend.service.ImagemContaService imagemContaService; // Fase 28
    @Autowired(required = false) private com.reciclatech.backend.service.EmailService emailService;

    @Autowired(required = false)
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @Autowired(required = false)
    private com.reciclatech.backend.service.ChamadoService chamadoService;   // Fase 32: chamados guardados no sistema

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
            // Fase 27: só a lista leve para o "Agendar Coleta"; os preços vêm sob demanda de /cotacoes/... (CotacaoController)
            model.addAttribute("listaArmazens", cotacaoPublicaService.listaParaPedido());
        } catch (Exception e) {
            log.warn("Página inicial sem a lista de armazéns: {}", e.getMessage());
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
        prepararTopoDoPainel(model, session);
        return "admin-lista-coletas";
    }

    /**
     * Fase 28: topo do painel com a logo do armazém e, para o dono, a foto dele.
     * "Trocar de armazém" só aparece para o dono que tem 2 armazéns ou mais.
     * Qualquer falha aqui só esconde as imagens: a tela abre normalmente.
     */
    /** Fase 32: só a logo do armazém para o topo das telas de cadastro (preços, VIP, despesas). */
    private void logoDoTopo(Model model, HttpSession session) {
        try {
            Object idSessao = session.getAttribute("idPlanilhaAtiva");
            if (imagemContaService != null && idSessao != null) {
                model.addAttribute("versaoLogoPainel", imagemContaService.versao(
                        com.reciclatech.backend.service.ImagemContaService.ARMAZEM, Long.parseLong(idSessao.toString())));
            }
        } catch (RuntimeException e) { /* sem logo: a tela abre normalmente */ }
    }

    private static String nomeDoMes(int m) {
        return new String[]{"janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto",
                "setembro", "outubro", "novembro", "dezembro"}[m - 1];
    }

    private void prepararTopoDoPainel(Model model, HttpSession session) {
        Long armazemId = null;
        Long parceiroId = session.getAttribute("parceiroId") instanceof Long p ? p : null;
        try {
            Object idSessao = session.getAttribute("idPlanilhaAtiva");
            if (idSessao != null) armazemId = Long.parseLong(idSessao.toString());
        } catch (NumberFormatException ignorado) { /* sem armazém válido: fica sem logo */ }
        try {
            if (imagemContaService != null) {
                model.addAttribute("versaoLogoPainel", imagemContaService.versao(com.reciclatech.backend.service.ImagemContaService.ARMAZEM, armazemId));
                if (parceiroId != null) {
                    model.addAttribute("versaoFotoPainel", imagemContaService.versao(com.reciclatech.backend.service.ImagemContaService.DONO, parceiroId));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Topo do painel sem imagens: {}", e.getMessage());
        }
        // Fase 32: sininho de respostas do suporte
        try {
            if (chamadoService != null && armazemId != null) model.addAttribute("chamadosNaoLidos", chamadoService.naoLidosDoArmazem(armazemId));
        } catch (RuntimeException e) { log.warn("Sininho sem contagem: {}", e.getMessage()); }
        boolean podeTrocar = false;
        if (parceiroId != null) {
            try {
                podeTrocar = parceiroService.listarArmazens(parceiroId).size() > 1;
            } catch (RuntimeException e) {
                log.warn("Não foi possível contar os armazéns do dono: {}", e.getMessage());
            }
            Object nome = session.getAttribute("parceiroNome");
            String primeiro = nome == null ? "" : nome.toString().trim().split("\\s+")[0];
            model.addAttribute("primeiroNomeDono", primeiro.isEmpty() ? "Meu Perfil" : primeiro);
        }
        model.addAttribute("podeTrocarArmazem", podeTrocar);
    }

    /** Fase 28: logo do armazém em que a pessoa está logada (dono ou operador). */
    @GetMapping("/admin/logo")
    @ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> logoDoPainel(@RequestParam(required = false) String v, HttpSession session) {
        if (session.getAttribute("adminLogado") == null || session.getAttribute("idPlanilhaAtiva") == null || imagemContaService == null) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }
        long armazemId;
        try {
            armazemId = Long.parseLong(session.getAttribute("idPlanilhaAtiva").toString());
        } catch (NumberFormatException e) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }
        return com.reciclatech.backend.util.RespostaImagem.jpeg(
                imagemContaService.buscar(com.reciclatech.backend.service.ImagemContaService.ARMAZEM, armazemId), v);
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
                ra.addFlashAttribute("erroTela", "CPF ou CNPJ inválido. Confira ou deixe em branco.");
                return "redirect:/admin/coletas";
            }
            cpfPadronizado = DocumentoValidator.formatar(cpf);
        }

        // Fase 30: sem nenhum material na Tabela de Preços não há o que pesar; antes o pedido ficava preso na fila
        try {
            if (googleSheetsService.listarMateriais().isEmpty()) {
                ra.addFlashAttribute("erroTela", "Cadastre pelo menos um material na Tabela de Preços antes de iniciar uma pesagem.");
                return "redirect:/admin/coletas";
            }
        } catch (Exception e) {
            log.warn("Não foi possível conferir os materiais antes da pesagem: {}", e.getMessage());
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
            model.addAttribute("precosVip", precosVip); // Fase 31: marca "VIP" na revisão
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
                    ra.addFlashAttribute("erroTela", "CPF ou CNPJ inválido. Nada foi gravado; refaça a pesagem com o documento correto (ou sem ele).");
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
            String recibo = googleSheetsService.finalizarAtendimentoComRecibo(idVendedor, cpfGravar, loteDeVendas);
            if (recibo == null) return "redirect:/admin/coletas?aviso=ja_finalizado";
            // Fase 31: o recibo mostra só este atendimento; "fim=1": o navegador apaga o rascunho da pesagem
            return "redirect:/extrato/" + idVendedor + "?recibo=" + recibo + "&fim=1";
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

    /** Endereço público do site (variável URL_BASE), usado no QR Code de verificação do recibo. */
    @Value("${coletae.url-base:}")
    private String urlBasePublica;

    /**
     * Recibo de um atendimento. Fase 31: mostra SÓ o atendimento pedido (?recibo=CÓDIGO) ou, sem código, o mais recente;
     * antes somava todas as compras do cliente. Data, hora e atendente são os do atendimento, não os de quem imprime.
     */
    @GetMapping("/extrato/{id}")
    public String gerarExtratoIndividual(@PathVariable String id, @RequestParam(required = false) String recibo,
                                         Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        try {
            Usuario usuario = googleSheetsService.listarUsuarios().stream().filter(u -> BuscaCliente.corresponde(u, id)).findFirst().orElse(null);
            if (usuario == null) return "redirect:/";

            List<com.reciclatech.backend.util.Recibos.Grupo> grupos =
                    com.reciclatech.backend.util.Recibos.agrupar(googleSheetsService.buscarVendasPorUsuario(id));
            // Fase 33: operador só abre os recibos que ele atendeu (nem pelo endereço direto)
            boolean soOsProprios = googleSheetsService.veSoOsProprios();
            if (soOsProprios) grupos = grupos.stream().filter(googleSheetsService::podeVerRecibo).collect(Collectors.toList());
            model.addAttribute("soOsProprios", soOsProprios);
            com.reciclatech.backend.util.Recibos.Grupo escolhido =
                    com.reciclatech.backend.util.Recibos.escolher(grupos, recibo).orElse(null);
            List<Oferta> vendasReais = escolhido == null ? new ArrayList<>() : escolhido.itens;
            BigDecimal totalGeral = escolhido == null ? BigDecimal.ZERO : escolhido.total;
            List<Material> mats = googleSheetsService.listarMateriais();
            Map<String, String> unidades = mapaUnidades(mats);

            String responsavel = escolhido != null ? escolhido.atendente : null;
            if (responsavel == null || responsavel.isBlank()) {
                responsavel = (String) session.getAttribute("nomeFuncionarioLogado"); // vendas antigas não guardavam quem atendeu
                if (responsavel == null || responsavel.trim().isEmpty()) responsavel = "Gestor / Administrador";
            }
            double totalKg = 0, totalUn = 0;
            for (Oferta v : vendasReais) {
                double q = v.getPeso() == null ? 0 : v.getPeso();
                if ("UN".equalsIgnoreCase(unidades.getOrDefault(v.getMaterial(), "KG"))) totalUn += q; else totalKg += q;
            }

            model.addAttribute("responsavelAtendimento", responsavel);
            model.addAttribute("mapaUnidades", unidades);
            model.addAttribute("mapaPrecos", mapaPrecos(mats));
            model.addAttribute("vendedor", usuario);
            model.addAttribute("vendas", vendasReais);
            model.addAttribute("total", totalGeral);
            model.addAttribute("totalKg", totalKg);
            model.addAttribute("totalUn", totalUn);
            model.addAttribute("valorExtenso", com.reciclatech.backend.util.ValorPorExtenso.reais(totalGeral));
            model.addAttribute("recibo", escolhido);
            model.addAttribute("reciboPedidoNaoExiste", recibo != null && !recibo.isBlank() && escolhido == null);
            model.addAttribute("outrosRecibos", grupos.stream().filter(g -> g != escolhido).limit(12).collect(Collectors.toList()));
            model.addAttribute("dataHoje", escolhido != null && escolhido.data != null ? escolhido.data : LocalDate.now(ZoneId.of("America/Recife")));
            model.addAttribute("horaRecibo", escolhido != null && escolhido.quando != null
                    ? escolhido.quando.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null);
            Armazem armazemRecibo = googleSheetsService.armazemDaSessao().orElse(null);
            model.addAttribute("armazem", armazemRecibo);
            String nomeArm = armazemRecibo != null && armazemRecibo.getNome() != null ? armazemRecibo.getNome()
                    : (session.getAttribute("nomeArmazem") != null ? session.getAttribute("nomeArmazem").toString() : "Armazém");
            model.addAttribute("nomeArmazem", nomeArm);
            model.addAttribute("nomeCliente", usuario.getNome() == null ? "" : usuario.getNome().replace("(SAÍDA)", "").trim());
            if (imagemContaService != null) {
                Object idArm = session.getAttribute("idPlanilhaAtiva");
                try {
                    if (idArm != null) model.addAttribute("versaoLogoRecibo",
                            imagemContaService.versao(com.reciclatech.backend.service.ImagemContaService.ARMAZEM, Long.parseLong(idArm.toString())));
                } catch (RuntimeException ignorado) { /* sem logo */ }
            }
            if (escolhido != null && escolhido.lote != null) {
                String base = urlBasePublica == null ? "" : urlBasePublica.trim().replaceAll("/+$", "");
                model.addAttribute("urlVerificacao", base + "/recibo/" + escolhido.getCodigo());
            }
        } catch (Exception e) {
            log.error("Erro ao montar o recibo", e);
            return "redirect:/?erro=extrato";
        }
        return "extrato";
    }

    /** Fase 31: verificação pública do recibo (QR Code). Sem login; mostra só o essencial, sem CPF nem telefone. */
    @GetMapping("/recibo/{codigo}")
    public String verificarRecibo(@PathVariable String codigo, Model model, jakarta.servlet.http.HttpServletRequest request) {
        if (!limitadorPublico.permitir("recibo:" + request.getRemoteAddr(), 30, 60_000L)) {
            model.addAttribute("limite", true);
            return "recibo-verificar";
        }
        try {
            googleSheetsService.verificarRecibo(codigo).ifPresent(r -> model.addAttribute("r", r));
        } catch (Exception e) {
            log.warn("Falha ao verificar recibo: {}", e.getMessage());
        }
        return "recibo-verificar";
    }

    /** Fase 31: recibos de um dia (hoje por padrão), um por atendimento, com hora, itens e total. */
    @GetMapping("/meus-extratos")
    public String listaExtratosDoDia(@RequestParam(required = false) String dia, Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        LocalDate hoje = LocalDate.now(ZoneId.of("America/Recife"));
        LocalDate escolhido = hoje;
        if (dia != null && !dia.isBlank()) {
            try { escolhido = LocalDate.parse(dia.trim()); } catch (RuntimeException ignorado) { escolhido = hoje; }
            if (escolhido.isAfter(hoje)) escolhido = hoje;
        }
        model.addAttribute("dia", escolhido);
        model.addAttribute("hoje", hoje);
        model.addAttribute("ehHoje", escolhido.equals(hoje));
        model.addAttribute("diaAnterior", escolhido.minusDays(1));
        model.addAttribute("diaSeguinte", escolhido.plusDays(1));
        model.addAttribute("soOsProprios", googleSheetsService.veSoOsProprios());
        try {
            List<DatabaseService.ReciboDoDia> recibos = googleSheetsService.recibosDoDia(escolhido);
            model.addAttribute("recibos", recibos);
            BigDecimal soma = BigDecimal.ZERO;
            for (DatabaseService.ReciboDoDia r : recibos) soma = soma.add(r.recibo.total);
            model.addAttribute("somaDia", soma);
        } catch (Exception e) {
            log.error("Erro na Central de Extratos", e);
            model.addAttribute("recibos", new ArrayList<>());
            model.addAttribute("somaDia", BigDecimal.ZERO);
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

    // =========================================================================
    // Fase 31: planilha de preços (baixar .xlsx e importar .xlsx/.csv com conferência)
    // =========================================================================

    private static final String S_IMPORTACAO = "importacaoPrecos";
    private static final long IMPORTACAO_VALIDADE_MS = 15 * 60 * 1000L;

    /**
     * O que fica guardado na sessão entre a conferência e a confirmação (só textos e números: serializável).
     * Fase 32: guarda as LINHAS da planilha; a cada "Revisar" ou "Confirmar" a conferência é refeita com o que está na tela.
     */
    static final class ImportacaoPendente implements java.io.Serializable {
        private static final long serialVersionUID = 2L;
        final String token;
        final String armazemId;
        long criadaEm;                 // renovado a cada "Revisar" (o dono pode levar um tempo corrigindo)
        final String nomeArquivo;
        java.util.ArrayList<String[]> linhas = new java.util.ArrayList<>();   // {nº da linha, nome, unidade, preço}
        java.util.HashSet<Integer> ignoradas = new java.util.HashSet<>();     // linhas desmarcadas na última conferência
        ImportacaoPendente(String token, String armazemId, String nomeArquivo) {
            this.token = token; this.armazemId = armazemId; this.criadaEm = System.currentTimeMillis(); this.nomeArquivo = nomeArquivo;
        }
        List<com.reciclatech.backend.util.ImportacaoPrecos.Entrada> entradas() {
            List<com.reciclatech.backend.util.ImportacaoPrecos.Entrada> l = new ArrayList<>();
            for (String[] x : linhas) l.add(new com.reciclatech.backend.util.ImportacaoPrecos.Entrada(Integer.parseInt(x[0]), x[1], x[2], x[3]));
            return l;
        }
    }

    private boolean gestorDePrecos(HttpSession s) {
        return s.getAttribute("adminLogado") != null && s.getAttribute("bloqueadoPagamento") == null
                && "GESTOR".equalsIgnoreCase((String) s.getAttribute("perfilUser"));
    }

    /** Baixa a tabela atual em .xlsx; o mesmo arquivo serve de modelo para importar. */
    @GetMapping("/admin/precos/planilha")
    public org.springframework.http.ResponseEntity<byte[]> baixarPlanilhaPrecos(HttpSession session) {
        if (!gestorDePrecos(session)) return org.springframework.http.ResponseEntity.status(302).header("Location", "/login").build();
        try {
            List<com.reciclatech.backend.util.PlanilhaModelo.Linha> linhas = new ArrayList<>();
            for (Material m : googleSheetsService.listarMateriais()) {
                linhas.add(new com.reciclatech.backend.util.PlanilhaModelo.Linha(m.getNome(), m.getUnidade(), m.getPrecoPorKg()));
            }
            String nomeArm = String.valueOf(session.getAttribute("nomeArmazem") == null ? "" : session.getAttribute("nomeArmazem"));
            byte[] corpo = com.reciclatech.backend.util.PlanilhaModelo.gerar(nomeArm, linhas);
            String arquivo = "precos-" + java.text.Normalizer.normalize(nomeArm, java.text.Normalizer.Form.NFD)
                    .replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "").toLowerCase(Locale.ROOT)
                    + "-" + LocalDate.now(ZoneId.of("America/Recife")) + ".xlsx";
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + arquivo.replace("--", "-") + "\"")
                    .header("Cache-Control", "no-store")
                    .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(corpo);
        } catch (Exception e) {
            log.error("Falha ao gerar a planilha de preços", e);
            return org.springframework.http.ResponseEntity.status(302).header("Location", "/admin/precos").build();
        }
    }

    /** Recebe a planilha e mostra a conferência. Nada é gravado aqui. */
    @PostMapping("/admin/precos/importar")
    public String conferirImportacao(@RequestParam(value = "arquivo", required = false) org.springframework.web.multipart.MultipartFile arquivo,
                                     HttpSession session, Model model, RedirectAttributes ra) {
        if (!gestorDePrecos(session)) return "redirect:/admin/coletas";
        session.removeAttribute(S_IMPORTACAO);
        if (arquivo == null || arquivo.isEmpty()) {
            ra.addFlashAttribute("erroTela", "Escolha a planilha (.xlsx ou .csv) para importar.");
            return "redirect:/admin/precos";
        }
        if (arquivo.getSize() > com.reciclatech.backend.util.PlanilhaLeitura.TAMANHO_MAX) {
            ra.addFlashAttribute("erroTela", "O arquivo passa de 1 MB. Envie só a lista de preços.");
            return "redirect:/admin/precos";
        }
        try {
            List<List<String>> tabela = com.reciclatech.backend.util.PlanilhaLeitura.ler(arquivo.getBytes());
            List<com.reciclatech.backend.util.ImportacaoPrecos.Entrada> entradas = com.reciclatech.backend.util.ImportacaoPrecos.extrair(tabela);
            String nomeArquivo = arquivo.getOriginalFilename() == null ? "planilha" : arquivo.getOriginalFilename();
            com.reciclatech.backend.util.ImportacaoPrecos.Previa previa = com.reciclatech.backend.util.ImportacaoPrecos.montar(
                    entradas, Set.of(), googleSheetsService.materiaisParaImportacao(), Valores.PRECO_MAX);
            ImportacaoPendente pendente = new ImportacaoPendente(java.util.UUID.randomUUID().toString(),
                    String.valueOf(session.getAttribute("idPlanilhaAtiva")), nomeArquivo);
            for (com.reciclatech.backend.util.ImportacaoPrecos.Entrada e : entradas) {
                pendente.linhas.add(new String[]{String.valueOf(e.linha()), e.nome(), e.unidade(), e.preco()});
            }
            prepararConferencia(model, previa, pendente, null, session);
            return "admin-precos-importar";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("erroTela", e.getMessage());
            return "redirect:/admin/precos";
        } catch (Exception e) {
            log.error("Falha ao ler a planilha de preços", e);
            ra.addFlashAttribute("erroTela", "Não foi possível ler a planilha. Baixe o modelo nesta tela e tente de novo.");
            return "redirect:/admin/precos";
        }
    }

    private static String corta(String v, int max) { return v.length() > max ? v.substring(0, max) : v; }

    /** Troca a coluna {@code col} da linha pelo valor da tela (se veio); diz se mudou. */
    private static boolean trocar(String[] linha, int col, List<String> tela, int i, int max) {
        if (tela == null || i >= tela.size() || tela.get(i) == null) return false;
        String novo = corta(tela.get(i).trim(), max);
        String antes = linha[col] == null ? "" : linha[col].trim();
        linha[col] = novo;
        return !novo.equals(antes);
    }

    private void prepararConferencia(Model model, com.reciclatech.backend.util.ImportacaoPrecos.Previa previa, ImportacaoPendente p, String aviso, HttpSession session) {
        // guarda as linhas exatamente como a tela vai mostrar: assim "mudou" só acusa o que o dono editou
        Map<Integer, String[]> porLinha = new HashMap<>();
        for (String[] l : p.linhas) porLinha.put(Integer.parseInt(l[0]), l);
        for (com.reciclatech.backend.util.ImportacaoPrecos.Item it : previa.itens) {
            String[] l = porLinha.get(it.linha);
            if (l == null) continue;
            l[1] = it.getNomeCampo() == null ? "" : it.getNomeCampo();
            l[2] = it.getUnidadeCampo();
            l[3] = it.getPrecoCampo();
        }
        model.addAttribute("previa", previa);
        model.addAttribute("token", p.token);
        model.addAttribute("nomeArquivo", p.nomeArquivo);
        if (aviso != null) model.addAttribute("avisoConferencia", aviso);
        session.setAttribute(S_IMPORTACAO, p);   // grava de novo: sessão serializada também pega as linhas atualizadas
    }

    /**
     * Fase 32: "Revisar" (refaz a conferência com as correções feitas na tela) ou "Confirmar" (grava, tudo ou nada).
     * O servidor sempre refaz as regras com o que veio da tela; nada do navegador é aceito sem conferir.
     */
    @PostMapping("/admin/precos/importar/confirmar")
    public String confirmarImportacao(@RequestParam String token,
                                      @RequestParam(required = false) String acao,
                                      @RequestParam(required = false) String confirmarVariacoes,
                                      @RequestParam(value = "linha", required = false) List<String> linhasTela,
                                      @RequestParam(value = "nome", required = false) List<String> nomesTela,
                                      @RequestParam(value = "unidade", required = false) List<String> unidadesTela,
                                      @RequestParam(value = "preco", required = false) List<String> precosTela,
                                      @RequestParam(value = "incluir", required = false) List<String> incluirTela,
                                      HttpSession session, Model model, RedirectAttributes ra) {
        if (!gestorDePrecos(session)) return "redirect:/admin/coletas";
        Object guardado = session.getAttribute(S_IMPORTACAO);
        if (!(guardado instanceof ImportacaoPendente p) || !p.token.equals(token)
                || !p.armazemId.equals(String.valueOf(session.getAttribute("idPlanilhaAtiva")))
                || System.currentTimeMillis() - p.criadaEm > IMPORTACAO_VALIDADE_MS) {
            session.removeAttribute(S_IMPORTACAO);
            ra.addFlashAttribute("erroTela", "A conferência expirou ou já foi usada. Envie a planilha de novo.");
            return "redirect:/admin/precos";
        }
        // aplica as correções da tela, só nas linhas que vieram da planilha (não dá para inventar linha nova)
        Map<String, String[]> porLinha = new LinkedHashMap<>();
        for (String[] l : p.linhas) porLinha.put(l[0], l);
        boolean mudou = false;
        int n = linhasTela == null ? 0 : linhasTela.size();
        for (int i = 0; i < n; i++) {
            String[] l = porLinha.get(linhasTela.get(i));
            if (l == null) continue;
            mudou |= trocar(l, 1, nomesTela, i, 120);
            mudou |= trocar(l, 2, unidadesTela, i, 10);
            mudou |= trocar(l, 3, precosTela, i, 30);
        }
        Set<Integer> ignoradas = new HashSet<>(p.ignoradas);
        if (linhasTela != null) {
            Set<String> marcadas = incluirTela == null ? Set.of() : new HashSet<>(incluirTela);
            ignoradas.clear();
            for (String l : linhasTela) if (porLinha.containsKey(l) && !marcadas.contains(l)) ignoradas.add(Integer.parseInt(l));
        }
        mudou |= !ignoradas.equals(p.ignoradas);
        p.ignoradas = new java.util.HashSet<>(ignoradas);
        com.reciclatech.backend.util.ImportacaoPrecos.Previa previa;
        try {
            previa = com.reciclatech.backend.util.ImportacaoPrecos.montar(p.entradas(), ignoradas,
                    googleSheetsService.materiaisParaImportacao(), Valores.PRECO_MAX);
        } catch (IllegalArgumentException e) {
            session.removeAttribute(S_IMPORTACAO);
            ra.addFlashAttribute("erroTela", e.getMessage());
            return "redirect:/admin/precos";
        }
        p.criadaEm = System.currentTimeMillis();
        if (!"confirmar".equals(acao)) {
            prepararConferencia(model, previa, p, mudou ? "Conferência refeita com as suas correções. Nada foi gravado ainda: confira e confirme."
                    : "Nada mudou na tela. Confira e confirme.", session);
            return "admin-precos-importar";
        }
        if (mudou) {   // mudou algo e clicou direto em Confirmar: mostra a conferência nova antes de gravar
            prepararConferencia(model, previa, p, "Você mudou valores na tela. Veja a conferência atualizada e clique em Confirmar de novo. Nada foi gravado ainda.", session);
            return "admin-precos-importar";
        }
        if (!previa.temMudanca()) {
            prepararConferencia(model, previa, p, "Nada a gravar: nenhuma linha marcada traz material novo ou preço diferente.", session);
            return "admin-precos-importar";
        }
        if (previa.variacoesGrandes > 0 && confirmarVariacoes == null) {
            prepararConferencia(model, previa, p, "Marque a confirmação dos preços em amarelo antes de importar.", session);
            return "admin-precos-importar";
        }
        List<String[]> novos = new ArrayList<>();
        List<Object[]> alteracoes = new ArrayList<>();
        for (com.reciclatech.backend.util.ImportacaoPrecos.Item it : previa.itens) {
            if (it.tipo == com.reciclatech.backend.util.ImportacaoPrecos.Tipo.NOVO) novos.add(new String[]{it.material, it.unidade, it.precoNovo.toPlainString()});
            else if (it.tipo == com.reciclatech.backend.util.ImportacaoPrecos.Tipo.ALTERAR) alteracoes.add(new Object[]{it.idExistente, it.precoNovo});
        }
        try {
            int[] r = googleSheetsService.aplicarImportacaoPrecos(novos, alteracoes);
            session.removeAttribute(S_IMPORTACAO);
            ra.addFlashAttribute("sucessoTela", "Planilha importada: " + r[0] + (r[0] == 1 ? " material novo" : " materiais novos")
                    + " e " + r[1] + (r[1] == 1 ? " preço alterado." : " preços alterados.") + " Tudo ficou registrado no Histórico.");
        } catch (IllegalArgumentException e) {
            prepararConferencia(model, previa, p, e.getMessage() + " Nada foi gravado.", session);
            return "admin-precos-importar";
        } catch (Exception e) {
            log.error("Falha ao aplicar a importação de preços", e);
            session.removeAttribute(S_IMPORTACAO);
            ra.addFlashAttribute("erroTela", "Não foi possível importar. Nada foi gravado; tente de novo.");
        }
        return "redirect:/admin/precos";
    }

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
        logoDoTopo(m, s);
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

    /** Preço muito diferente do anterior: volta para a tela mostrando o valor e pedindo confirmação (nada foi gravado). */
    private String pedirConfirmacaoPreco(RedirectAttributes ra, String destino, String acao, String mensagem, Map<String, String> campos) {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("acao", acao);
        pedido.put("mensagem", mensagem);
        pedido.put("campos", campos);
        ra.addFlashAttribute("confirmarPreco", pedido);
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
    public String upd(@RequestParam Long id, @RequestParam Double novoPreco,
                      @RequestParam(required = false, defaultValue = "false") boolean confirmar, HttpSession session, RedirectAttributes ra) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        try {
            BigDecimal precoValido = Valores.preco(novoPreco);
            if (precoValido == null) throw new IllegalArgumentException("Informe um preço maior que zero.");
            googleSheetsService.atualizarPrecoMaterial(id, precoValido, confirmar);
            return voltarComAviso(ra, "/admin/precos", "Preço atualizado.", null);
        } catch (VariacaoPrecoException e) {
            return pedirConfirmacaoPreco(ra, "/admin/precos", "/admin/atualizar", e.getMessage(),
                    Map.of("id", String.valueOf(id), "novoPreco", String.valueOf(novoPreco)));
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
        logoDoTopo(model, s);   // Fase 34: logo do armazém no topo
        return "admin-historico-alteracoes";
    }

    @GetMapping("/admin/fornecedores-vip")
    public String telaFornecedoresVip(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        try {
            List<Map<String, String>> vips = googleSheetsService.listarFornecedoresVip();
            model.addAttribute("vips", vips);
            // Fase 32: um cartão por fornecedor (CPF/CNPJ), com os materiais dele
            Map<String, List<Map<String, String>>> grupos = new java.util.TreeMap<>();
            for (Map<String, String> v : vips) grupos.computeIfAbsent(v.get("cpf"), k -> new ArrayList<>()).add(v);
            List<Map<String, Object>> cartoes = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, String>>> g : grupos.entrySet()) {
                g.getValue().sort(Comparator.comparing(x -> String.valueOf(x.get("material"))));
                String documento = DocumentoValidator.formatar(g.getKey());
                StringBuilder busca = new StringBuilder(g.getKey()).append(' ').append(documento);
                for (Map<String, String> x : g.getValue()) busca.append(' ').append(x.get("material"));
                Map<String, Object> c = new HashMap<>();
                c.put("cpf", g.getKey());
                c.put("documento", documento);
                c.put("tipoDocumento", DocumentoValidator.normalizar(g.getKey()).length() == 14 ? "CNPJ" : "CPF");
                c.put("itens", g.getValue());
                c.put("busca", busca.toString());
                cartoes.add(c);
            }
            model.addAttribute("gruposVip", cartoes);
            model.addAttribute("materiais", googleSheetsService.listarMateriais());
        } catch (Exception e) { log.warn("Falha ignorada", e); }
        logoDoTopo(model, session);
        return "admin-fornecedores-vip";
    }

    @PostMapping("/admin/fornecedores-vip/novo")
    public String salvarFornecedorVip(@RequestParam String cpf, @RequestParam String material, @RequestParam Double preco,
                                      @RequestParam(required = false, defaultValue = "false") boolean confirmar, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null || !"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        try {
            BigDecimal precoValido = Valores.preco(preco);
            if (precoValido == null) throw new IllegalArgumentException("Informe um preço maior que zero.");
            googleSheetsService.salvarFornecedorVip(cpf, material, precoValido, confirmar);
            return voltarComAviso(ra, "/admin/fornecedores-vip", "Preço VIP salvo.", null);
        } catch (VariacaoPrecoException e) {
            return pedirConfirmacaoPreco(ra, "/admin/fornecedores-vip", "/admin/fornecedores-vip/novo", e.getMessage(),
                    Map.of("cpf", cpf, "material", material, "preco", String.valueOf(preco)));
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

    /** Fase 33: atendimentos por funcionário (só o dono/gestor; fica dentro do Financeiro). */
    @GetMapping("/admin/analises/equipe")
    public String desempenhoEquipe(@RequestParam(required = false) String periodo,
                                   @RequestParam(required = false) String de,
                                   @RequestParam(required = false) String ate,
                                   Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        LocalDate hoje = LocalDate.now(ZoneId.of("America/Recife"));
        String p = periodo == null ? "mes" : periodo;
        LocalDate ini, fim = hoje;
        switch (p) {
            case "hoje" -> ini = hoje;
            case "7d" -> ini = hoje.minusDays(6);
            case "30d" -> ini = hoje.minusDays(29);
            case "mesPassado" -> { ini = hoje.withDayOfMonth(1).minusMonths(1); fim = hoje.withDayOfMonth(1).minusDays(1); }
            case "custom" -> {
                try {
                    ini = LocalDate.parse(de); fim = LocalDate.parse(ate);
                    if (fim.isAfter(hoje)) fim = hoje;
                    if (ini.isAfter(fim)) { LocalDate t = ini; ini = fim; fim = t; }
                    if (ini.isBefore(fim.minusDays(366))) ini = fim.minusDays(366);
                } catch (RuntimeException e) { p = "mes"; ini = hoje.withDayOfMonth(1); fim = hoje; }
            }
            default -> { p = "mes"; ini = hoje.withDayOfMonth(1); }
        }
        model.addAttribute("periodoSel", p);
        model.addAttribute("periodoDe", ini);
        model.addAttribute("periodoAte", fim);
        try {
            List<com.reciclatech.backend.util.DesempenhoEquipe.Funcionario> equipe = new ArrayList<>();
            for (com.reciclatech.backend.model.Funcionario f : googleSheetsService.listarFuncionarios()) {
                equipe.add(new com.reciclatech.backend.util.DesempenhoEquipe.Funcionario(f.getId(), f.getNome(), f.isAtivo()));
            }
            model.addAttribute("desempenho", com.reciclatech.backend.util.DesempenhoEquipe.calcular(
                    googleSheetsService.getHistoricoCompleto(), equipe, mapaUnidades(googleSheetsService.listarMateriais()), ini, fim));
        } catch (Exception e) {
            log.error("Erro no desempenho da equipe", e);
            model.addAttribute("erroTela", "Não foi possível calcular agora. Tente de novo.");
        }
        logoDoTopo(model, session);
        return "admin-equipe-desempenho";
    }

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

            logoDoTopo(model, session);   // Fase 34: logo do armazém no topo
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
        logoDoTopo(model, session);   // Fase 34: logo do armazém no topo
        return "admin-historico";
    }

    @GetMapping("/admin/despesas")
    public String telaDespesas(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        if (session.getAttribute("bloqueadoPagamento") != null) return "redirect:/admin/assinatura";
        if (!"GESTOR".equalsIgnoreCase((String) session.getAttribute("perfilUser"))) return "redirect:/admin/coletas";

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache"); response.setDateHeader("Expires", 0);

        List<Map<String, String>> despesas;
        try { despesas = googleSheetsService.listarDespesas(); }
        catch (Exception e) { despesas = new ArrayList<>(); }
        model.addAttribute("despesas", despesas);
        // Fase 32: despesas agrupadas por mês (a lista já vem da mais nova para a mais antiga)
        Map<String, Map<String, Object>> meses = new LinkedHashMap<>();
        for (Map<String, String> d : despesas) {
            String mes = d.getOrDefault("mes", "");
            Map<String, Object> g = meses.computeIfAbsent(mes, k -> {
                Map<String, Object> n = new HashMap<>();
                String rotulo = k;
                try {
                    java.time.YearMonth ym = java.time.YearMonth.parse(k);
                    String nm = nomeDoMes(ym.getMonthValue());
                    rotulo = Character.toUpperCase(nm.charAt(0)) + nm.substring(1) + " de " + ym.getYear();
                } catch (RuntimeException ignorado) { /* mês ilegível: mostra como veio */ }
                n.put("rotulo", rotulo);
                n.put("itens", new ArrayList<Map<String, String>>());
                n.put("soma", BigDecimal.ZERO);
                return n;
            });
            ((List<Map<String, String>>) g.get("itens")).add(d);
            try { g.put("soma", ((BigDecimal) g.get("soma")).add(new BigDecimal(d.get("valor").replace(".", "").replace(",", ".")))); }
            catch (RuntimeException ignorado) { /* valor ilegível fica fora da soma */ }
        }
        model.addAttribute("mesesDespesa", new ArrayList<>(meses.values()));
        // Fase 32: resumo no topo (total do mês atual e do mês passado)
        try {
            java.time.LocalDate hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Recife"));
            java.time.YearMonth mes = java.time.YearMonth.from(hoje), anterior = mes.minusMonths(1);
            BigDecimal noMes = BigDecimal.ZERO, noAnterior = BigDecimal.ZERO;
            for (com.reciclatech.backend.model.Despesa d : googleSheetsService.listarDespesasBrutas()) {
                if (d.getDataDespesa() == null || d.getValor() == null) continue;
                java.time.YearMonth m = java.time.YearMonth.from(d.getDataDespesa());
                if (m.equals(mes)) noMes = noMes.add(d.getValor());
                else if (m.equals(anterior)) noAnterior = noAnterior.add(d.getValor());
            }
            model.addAttribute("totalMes", noMes);
            model.addAttribute("totalMesAnterior", noAnterior);
            model.addAttribute("nomeMes", nomeDoMes(mes.getMonthValue()));
            model.addAttribute("nomeMesAnterior", nomeDoMes(anterior.getMonthValue()));
            model.addAttribute("mesAtual", mes.toString());
        } catch (Exception e) { log.warn("Resumo de despesas indisponível: {}", e.getMessage()); }
        logoDoTopo(model, session);
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
        model.addAttribute("senhaOperadorMin", DatabaseService.SENHA_OPERADOR_MIN);
        prepararTopoDoPainel(model, session); // Fase 30: logo e nome do armazém no topo
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
                                        @RequestParam(required = false) String confirmarSenha,
                                        HttpSession session,
                                        org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {

        if (session.getAttribute("adminLogado") == null) return "redirect:/login";

        if (!(session.getAttribute("parceiroId") instanceof Long parceiroId)) {
            ra.addFlashAttribute("erroPerfil", "Somente o dono da conta pode alterar a senha. Entre pela sua conta e tente de novo.");
            return "redirect:/admin/coletas";
        }
        // Fase 29: nova senha obrigatória e repetida igual (a tela antiga aceitava em branco e dizia "nenhuma alteração")
        String erroNova = com.reciclatech.backend.util.RegraSenha.validarTroca(novaSenha, confirmarSenha);
        if (erroNova != null) {
            ra.addFlashAttribute("erroPerfil", erroNova);
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
                if (emailService != null) {
                    parceiroService.buscar(parceiroId).ifPresent(p -> {
                        String destino = p.getEmail(), nome = p.getNome();
                        java.util.concurrent.CompletableFuture.runAsync(() -> emailService.enviarSenhaAlterada(destino, nome));
                    });
                }
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

    /** Valor mensal de cada armazém adicional no plano Personalizado (variável PRECO_ARMAZEM_EXTRA). */
    @org.springframework.beans.factory.annotation.Value("${coletae.preco-armazem-extra:129.90}")
    private double precoArmazemExtra;

    @GetMapping("/admin/assinatura")
    public String telaAssinaturaSaaS(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String plano = (String) session.getAttribute("planoAssinatura");
        String status = (String) session.getAttribute("statusAssinatura");

        double valor;
        String planoNome;
        if (PlanoAcesso.PERSONALIZADO.equals(plano)) {
            int qtd = 1;
            try { qtd = googleSheetsService.contarPersonalizadosDoDono(Long.parseLong(String.valueOf(session.getAttribute("idPlanilhaAtiva")))); }
            catch (NumberFormatException ignorado) { /* sessão sem armazém: mostra o valor de 1 */ }
            valor = PrecoPlano.personalizado(qtd, precoArmazemExtra);
            planoNome = "Coletaê SaaS Personalizado (" + qtd + (qtd == 1 ? " armazém)" : " armazéns)");
        } else {
            valor = PrecoPlano.individual(plano);
            planoNome = "PRO".equals(plano) ? "Coletaê SaaS Pro" : "Coletaê SaaS Start";
        }

        model.addAttribute("planoCodigo", plano == null ? "START" : plano);
        model.addAttribute("planoNome", planoNome);
        model.addAttribute("valorMensalidade", valor);
        model.addAttribute("statusAssinatura", status != null ? status : "TRIAL");
        model.addAttribute("proximoVencimento", LocalDate.now(ZoneId.of("America/Recife")).plusDays(7).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        model.addAttribute("faturas", new ArrayList<>());
        logoDoTopo(model, session);   // Fase 34: logo do armazém no topo
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
            @RequestParam(required = false) String voltar,
            jakarta.servlet.http.HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        String destinoChamado = "chamados".equals(voltar) ? "redirect:/admin/chamados" : "redirect:/admin/coletas";
        if (printTela != null && !printTela.isEmpty() && !anexoPermitido(printTela)) {
            redirectAttributes.addFlashAttribute("erroSuporte", "Anexo não permitido. Envie apenas imagem (PNG, JPG, WEBP, GIF) ou PDF de até 5 MB.");
            return destinoChamado;
        }
        // no máximo 5 chamados por hora por armazém (evita abuso e gasto da cota do e-mail)
        String chaveChamado = "chamado:" + session.getAttribute("idPlanilhaAtiva");
        if (!limitadorPublico.permitir(chaveChamado, 5, 60 * 60 * 1000L)) {
            redirectAttributes.addFlashAttribute("erroSuporte", "Você já enviou vários chamados na última hora. Aguarde um pouco ou chame no WhatsApp.");
            return destinoChamado;
        }
        if (emailRetorno != null && !emailRetorno.isBlank()
                && !com.reciclatech.backend.util.EmailValido.formatoValido(emailRetorno)) {
            redirectAttributes.addFlashAttribute("erroSuporte", "O e-mail de retorno parece inválido. Confira e tente de novo.");
            return destinoChamado;
        }
        // Fase 30: limites de tamanho (a tela já limita; aqui é a garantia)
        if (assunto.isBlank() || assunto.length() > 150 || descricao.isBlank() || descricao.length() > 5000) {
            redirectAttributes.addFlashAttribute("erroSuporte", "Preencha o assunto (até 150 letras) e a descrição (até 5000 letras).");
            return destinoChamado;
        }
        // evita quebra de linha no assunto do e-mail (injeção de cabeçalho)
        assunto = assunto.replaceAll("[\\r\\n]+", " ").trim();
        prioridade = prioridade.replaceAll("[\\r\\n]+", " ").trim();
        // Fase 32: guarda o chamado no sistema (conversa + sininho). Se falhar, o e-mail ainda sai como antes.
        com.reciclatech.backend.model.Chamado registrado = null;
        if (chamadoService != null) {
            try {
                Long armazemChamado = Long.parseLong(String.valueOf(session.getAttribute("idPlanilhaAtiva")));
                com.reciclatech.backend.service.ChamadoService.Anexo anexo = null;
                if (printTela != null && !printTela.isEmpty()) {
                    anexo = new com.reciclatech.backend.service.ChamadoService.Anexo(printTela.getOriginalFilename(), printTela.getBytes());
                }
                try {
                    registrado = chamadoService.abrir(armazemChamado, (String) session.getAttribute("nomeArmazem"),
                            ChamadoController.autorDa(session), assunto, prioridade, descricao, emailRetorno, anexo);
                } catch (IllegalArgumentException anexoRecusado) {
                    // Fase 34: o print não passou na conferência do conteúdo; grava o chamado sem ele (o e-mail ainda leva o arquivo)
                    if (anexo == null) throw anexoRecusado;
                    log.warn("Anexo do chamado recusado: {}", anexoRecusado.getMessage());
                    registrado = chamadoService.abrir(armazemChamado, (String) session.getAttribute("nomeArmazem"),
                            ChamadoController.autorDa(session), assunto, prioridade, descricao, emailRetorno, null);
                }
            } catch (Exception e) {
                log.error("Chamado não ficou gravado no sistema (segue só por e-mail)", e);
            }
        }
        try {
            String nomeArmazem = (String) session.getAttribute("nomeArmazem");
            String telefoneAdmin = (String) session.getAttribute("telefone");

            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(emailSuporte);
            helper.setSubject("[CHAMADO " + (registrado != null ? registrado.getCodigo() + " " : "") + prioridade.toUpperCase() + "] " + assunto);

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
                    + "<p>" + linkZap + "</p>"
                    + (registrado != null ? "<p style='color:#555;'><b>Responda pelo painel Master &gt; Chamados</b> (" + registrado.getCodigo()
                        + "): o armazém vê a resposta no sininho. Resposta dada só por este e-mail não aparece no Coletaê.</p>" : "");

            helper.setText(corpoHTML, true);

            if (printTela != null && !printTela.isEmpty()) {
                helper.addAttachment(nomeAnexoSeguro(printTela.getOriginalFilename()), printTela);
            }

            mailSender.send(message);
            redirectAttributes.addFlashAttribute("sucessoSuporte", registrado != null
                    ? "Chamado " + registrado.getCodigo() + " enviado! A resposta aparece no sininho do menu do perfil (Meus chamados)."
                    : "Chamado enviado com sucesso! Nossa equipe técnica analisará o caso.");
        } catch (Exception e) {
            log.error("Erro inesperado", e);
            if (registrado != null) {
                // gravou no sistema, só o e-mail de aviso falhou: o chamado aparece no painel Master mesmo assim
                redirectAttributes.addFlashAttribute("sucessoSuporte", "Chamado " + registrado.getCodigo() + " registrado. A resposta aparece no sininho (Meus chamados).");
            } else {
                redirectAttributes.addFlashAttribute("erroSuporte", "Ocorreu um erro ao enviar seu chamado. Tente novamente ou chame no WhatsApp.");
            }
        }

        return destinoChamado;
    }


    // =========================================================================
    // 9. ÁREA RESTRITA DO MASTER (O SEU PAINEL DE ADMIN DO SAAS)
    // =========================================================================

    @GetMapping("/master/painel")
    public String telaPainelMaster(Model model, HttpSession session) {
        if (session.getAttribute("masterLogado") == null) {
            return "redirect:/login";
        }
        try { if (chamadoService != null) model.addAttribute("chamadosPendentes", chamadoService.naoLidosDoSuporte()); }
        catch (RuntimeException e) { log.warn("Contagem de chamados indisponível: {}", e.getMessage()); }

        try {
            List<Map<String, Object>> clientesBanco = (List<Map<String, Object>>) (List<?>) googleSheetsService.listarTodosArmazensSaaS();
            int totalClientes = clientesBanco.size();
            int ativos = 0; int trials = 0; int bloqueados = 0;
            int ativosStart = 0; int ativosPro = 0;
            int trialsStart = 0; int trialsPro = 0;
            double faturamentoMensal = 0.0;
            int ativosPersonalizado = 0; int trialsPersonalizado = 0;
            Map<Object, Integer> personalizadosAtivosPorDono = new HashMap<>();   // dono -> armazéns ATIVOS no Personalizado (cobrança é por dono)

            List<Armazem> clientesFormatados = new ArrayList<>();
            LocalDate hojeMaster = LocalDate.now(ZoneId.of("America/Recife"));
            Map<Long, String> infoCadastro = new HashMap<>();   // id -> "Cadastro em 01/11/2026 · teste vence em 2 dias"
            Map<Long, String> ultimoAcesso = new HashMap<>();   // id -> "Último acesso do dono: 13/11/2026 09:30"
            List<Map<String, Object>> testesVencendo = new ArrayList<>();
            Map<Long, Object[]> movimento = googleSheetsService.movimentoPorArmazem();
            Map<Long, String> infoMovimento = new HashMap<>();   // id -> "Última movimentação: ... · 12 no mês"
            List<String> ociosos = new ArrayList<>();
            int testesVencidos = 0;
            java.time.format.DateTimeFormatter fData = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            java.time.format.DateTimeFormatter fDataHora = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            for (Map<String, Object> c : clientesBanco) {
                String status = c.get("status") != null ? c.get("status").toString().toUpperCase() : "ATIVO";
                String plano = c.get("plano") != null ? c.get("plano").toString().toUpperCase() : "START";

                if ("ATIVO".equals(status)) {
                    ativos++;
                    if (PlanoAcesso.PERSONALIZADO.equals(plano)) {
                        ativosPersonalizado++;
                        Object dono = c.get("proprietario_id") != null ? c.get("proprietario_id") : ("sem-dono-" + c.get("id"));
                        personalizadosAtivosPorDono.merge(dono, 1, Integer::sum);
                    }
                    else if ("PRO".equals(plano)) { ativosPro++; faturamentoMensal += PrecoPlano.PRO; }
                    else { ativosStart++; faturamentoMensal += PrecoPlano.START; }
                } else if ("TRIAL".equals(status)) {
                    trials++;
                    if (PlanoAcesso.PERSONALIZADO.equals(plano)) { trialsPersonalizado++; }
                    else if ("PRO".equals(plano)) { trialsPro++; } else { trialsStart++; }
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

                LocalDate cadastro = c.get("data_cadastro") instanceof java.sql.Date d ? d.toLocalDate() : null;
                a.setDataCadastro(cadastro);
                if (cadastro != null) {
                    String texto = "Cadastro em " + cadastro.format(fData);
                    if ("TRIAL".equals(status)) {
                        long restam = Assinatura.DIAS_TRIAL - java.time.temporal.ChronoUnit.DAYS.between(cadastro, hojeMaster);
                        if (restam >= 0) texto += " · teste vence em " + restam + (restam == 1 ? " dia" : " dias");
                        else texto += " · teste vencido há " + (-restam) + (restam == -1 ? " dia" : " dias");
                        if (restam <= 3) {
                            Map<String, Object> aviso = new HashMap<>();
                            aviso.put("id", a.getId()); aviso.put("nome", a.getNome()); aviso.put("restam", restam);
                            aviso.put("texto", restam >= 0 ? "vence em " + restam + (restam == 1 ? " dia" : " dias") : "vencido há " + (-restam) + (restam == -1 ? " dia" : " dias"));
                            testesVencendo.add(aviso);
                        }
                    }
                    infoCadastro.put(a.getId(), texto);
                }
                LocalDate ultimaMov = null;
                Object[] mov = movimento.get(a.getId());
                if (mov != null) ultimaMov = (LocalDate) mov[0];
                infoMovimento.put(a.getId(), mov == null || ultimaMov == null ? "Sem movimentação ainda"
                        : "Última movimentação: " + ultimaMov.format(fData) + " · " + mov[1] + " no mês");
                if (Engajamento.ocioso(status, cadastro, ultimaMov, hojeMaster)) ociosos.add(a.getNome());
                if (Assinatura.trialVencido(status, cadastro, hojeMaster)) testesVencidos++;
                if (c.get("ultimo_acesso_dono") instanceof java.sql.Timestamp t) {
                    ultimoAcesso.put(a.getId(), "Último acesso do dono: " + t.toLocalDateTime().format(fDataHora));
                }

                clientesFormatados.add(a);
            }

            // Donos com armazém no Personalizado e outros ainda em START/PRO: o suporte precisa unificar ao ativar
            Map<Object, List<String>> fora = new LinkedHashMap<>();
            Set<Object> comPersonalizado = new HashSet<>();
            Map<Object, String> nomeDono = new HashMap<>();
            for (Map<String, Object> c : clientesBanco) {
                Object dono = c.get("proprietario_id");
                if (dono == null) continue;
                nomeDono.put(dono, c.get("nome_dono") != null ? c.get("nome_dono").toString() : "Dono #" + dono);
                String pl = c.get("plano") != null ? c.get("plano").toString().toUpperCase() : "START";
                if (PlanoAcesso.PERSONALIZADO.equals(pl)) comPersonalizado.add(dono);
                else fora.computeIfAbsent(dono, k -> new ArrayList<>()).add((c.get("nome") != null ? c.get("nome").toString() : "Sem nome") + " (" + pl + ")");
            }
            List<String> unificarPlano = new ArrayList<>();
            for (Map.Entry<Object, List<String>> e : fora.entrySet()) {
                if (comPersonalizado.contains(e.getKey()))
                    unificarPlano.add(nomeDono.get(e.getKey()) + ": " + String.join(", ", e.getValue()));
            }
            model.addAttribute("unificarPlano", unificarPlano);

            // Personalizado: a mensalidade é por dono (199,90 + extra por armazém adicional)
            int donosPersonalizados = personalizadosAtivosPorDono.size();
            for (int qtdAtivos : personalizadosAtivosPorDono.values()) faturamentoMensal += PrecoPlano.personalizado(qtdAtivos, precoArmazemExtra);

            double volumeGmv = 0.0;
            double volumeToneladas = 0.0;
            int churnMes = bloqueados + testesVencidos;   // hoje fora de operação: suspensos, bloqueados ou teste vencido
            int clientesEmRisco = ociosos.size();

            try {
                // Soma direto no banco, de TODOS os armazéns (antes lia o histórico do armazém da sessão: o master não tem, dava sempre 0)
                double[] mes = googleSheetsService.resumoMensalMaster();
                volumeGmv = mes[0];
                volumeToneladas = mes[1];
            } catch (Exception e) {
                log.warn("Painel master: não consegui somar o volume do mês", e);
            }

            testesVencendo.sort(Comparator.comparingLong(m -> (Long) m.get("restam")));
            model.addAttribute("infoMovimento", infoMovimento);
            model.addAttribute("ociosos", ociosos);
            model.addAttribute("infoCadastro", infoCadastro);
            model.addAttribute("ultimoAcesso", ultimoAcesso);
            model.addAttribute("testesVencendo", testesVencendo);
            model.addAttribute("listaClientes", clientesFormatados);
            model.addAttribute("totalClientes", totalClientes);
            model.addAttribute("ativos", ativos);
            model.addAttribute("trials", trials);
            model.addAttribute("bloqueados", bloqueados);
            model.addAttribute("ativosStart", ativosStart);
            model.addAttribute("ativosPro", ativosPro);
            model.addAttribute("ativosPersonalizado", ativosPersonalizado);
            model.addAttribute("donosPersonalizados", donosPersonalizados);
            model.addAttribute("trialsPersonalizado", trialsPersonalizado);
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

    @PostMapping("/master/mudar-plano")
    public String mudarPlanoCliente(@RequestParam Long id, @RequestParam String plano, HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("masterLogado") == null) return "redirect:/login";
        try {
            if (googleSheetsService.atualizarPlanoArmazem(id, plano)) ra.addFlashAttribute("sucessoTela", "Plano atualizado.");
            else ra.addFlashAttribute("erroTela", "Armazém não encontrado.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("erroTela", e.getMessage());
        } catch (Exception e) {
            log.error("Erro ao mudar plano", e);
            ra.addFlashAttribute("erroTela", "Não foi possível mudar o plano.");
        }
        return "redirect:/master/painel";
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