package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Armazem;
import com.reciclatech.backend.model.Parceiro;
import com.reciclatech.backend.service.ComparacaoArmazens;
import com.reciclatech.backend.service.DatabaseService;
import com.reciclatech.backend.service.ImagemContaService;
import com.reciclatech.backend.util.Assinatura;
import com.reciclatech.backend.service.LoginThrottle;
import com.reciclatech.backend.util.PeriodoComparacao;
import com.reciclatech.backend.util.PrecoPlano;
import com.reciclatech.backend.service.ParceiroService;
import com.reciclatech.backend.service.RecuperacaoSenhaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Fluxo novo (conta do dono = Parceiro): criar conta, entrar, cadastrar armazéns e escolher em qual trabalhar.
 * Ao ENTRAR em um armazém, preenche as mesmas variáveis de sessão do login antigo,
 * então todas as telas /admin/** continuam funcionando sem alteração.
 */
@Controller
@RequestMapping("/conta")
public class ContaController {

    private static final String S_PARCEIRO_ID = "parceiroId";
    private static final String S_PARCEIRO_NOME = "parceiroNome";

    private final ParceiroService parceiroService;
    private final LoginThrottle throttle;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final DatabaseService databaseService;
    private final ImagemContaService imagemContaService;

    @org.springframework.beans.factory.annotation.Value("${coletae.preco-armazem-extra:129.90}")
    private double precoArmazemExtra;

    public ContaController(ParceiroService parceiroService,
                           LoginThrottle throttle, RecuperacaoSenhaService recuperacaoSenhaService,
                           DatabaseService databaseService, ImagemContaService imagemContaService) {
        this.databaseService = databaseService;
        this.imagemContaService = imagemContaService;
        this.parceiroService = parceiroService;
        this.throttle = throttle;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
    }

    private Long parceiroLogado(HttpSession session) {
        return session.getAttribute(S_PARCEIRO_ID) instanceof Long id ? id : null;
    }

    private static String nvl(String v, String padrao) {
        return (v == null || v.isBlank()) ? padrao : v;
    }

    @GetMapping
    public String raiz() {
        return "redirect:/conta/armazens";
    }

    // ---------------- cadastro da conta ----------------

    @GetMapping("/cadastro")
    public String cadastroForm(HttpSession session) {
        if (parceiroLogado(session) != null) return "redirect:/conta/armazens";
        return "conta-cadastro";
    }

    @PostMapping("/cadastro")
    public String cadastrar(@RequestParam String nome, @RequestParam String email,
                            @RequestParam String senha, @RequestParam String confirmarSenha, Model model) {
        String erro;
        if (!senha.equals(confirmarSenha)) {
            erro = "As senhas não conferem.";
        } else {
            try {
                parceiroService.registrar(nome, email, senha);
                return "redirect:/login?criada=true";
            } catch (IllegalArgumentException e) {
                erro = e.getMessage();
            } catch (DataIntegrityViolationException e) {
                erro = "Este e-mail já está cadastrado.";
            }
        }
        model.addAttribute("erro", erro);
        model.addAttribute("nome", nome);
        model.addAttribute("email", email);
        return "conta-cadastro";
    }

    // ---------------- login / sair ----------------

    /** O login do dono agora fica na tela única /login (aba "Sou dono"); este endereço antigo só redireciona. */
    @GetMapping("/login")
    public String loginAntigo(HttpServletRequest request) {
        String consulta = request.getQueryString();
        return "redirect:/login" + (consulta == null || consulta.isBlank() ? "" : "?" + consulta);
    }

    @PostMapping("/sair")
    public String sair(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    // ---------------- armazéns do dono ----------------

    @GetMapping("/armazens")
    public String armazens(HttpSession session, Model model) {
        Long id = parceiroLogado(session);
        if (id == null) return "redirect:/login";
        java.util.List<Armazem> lista = parceiroService.listarArmazens(id);
        model.addAttribute("armazens", lista);
        model.addAttribute("somentePersonalizado", parceiroService.somentePersonalizado(id));
        model.addAttribute("personalizado", parceiroService.temPersonalizado(id));
        model.addAttribute("podeComparar", lista.size() >= 2);
        model.addAttribute("parceiroNome", session.getAttribute(S_PARCEIRO_NOME));
        String nomeDono = String.valueOf(nvl(String.valueOf(session.getAttribute(S_PARCEIRO_NOME)), "?")).trim();
        model.addAttribute("inicial", nomeDono.isEmpty() ? "?" : nomeDono.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        model.addAttribute("temFoto", !imagemContaService.comImagem(ImagemContaService.DONO, java.util.List.of(id)).isEmpty());
        java.util.List<Long> ids = lista.stream().map(Armazem::getId).collect(java.util.stream.Collectors.toList());
        model.addAttribute("comLogo", imagemContaService.comImagem(ImagemContaService.ARMAZEM, ids));

        // situação de cada armazém em palavras e valor mensal estimado da conta
        java.time.LocalDate hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Recife"));
        java.util.Map<Long, String> infoStatus = new java.util.HashMap<>();
        int personalizados = 0;
        double mensalidade = 0;
        for (Armazem a : lista) {
            infoStatus.put(a.getId(), textoSituacao(a, hoje));
            if ("PERSONALIZADO".equalsIgnoreCase(a.getPlano())) personalizados++;
            else mensalidade += PrecoPlano.individual(a.getPlano());
        }
        if (personalizados > 0) mensalidade += PrecoPlano.personalizado(personalizados, precoArmazemExtra);
        model.addAttribute("infoStatus", infoStatus);
        model.addAttribute("mensalidade", mensalidade);
        model.addAttribute("precoStart", PrecoPlano.START);
        model.addAttribute("precoPro", PrecoPlano.PRO);
        model.addAttribute("precoExtra", precoArmazemExtra > 0 ? precoArmazemExtra : PrecoPlano.EXTRA_PADRAO);
        return "conta-armazens";
    }

    /** Compara os armazéns do dono: lucro, compras e materiais por armazém no período escolhido. */
    @GetMapping("/comparar")
    public String comparar(@RequestParam(required = false) String periodo, @RequestParam(required = false) String de,
                           @RequestParam(required = false) String ate, HttpSession session, Model model) {
        Long id = parceiroLogado(session);
        if (id == null) return "redirect:/login";
        java.util.List<Armazem> lista = parceiroService.listarArmazens(id);   // só os armazéns DESTE dono
        if (lista.size() < 2) return "redirect:/conta/armazens";
        java.time.LocalDate hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Recife"));
        PeriodoComparacao p = PeriodoComparacao.resolver(periodo, de, ate, hoje);
        java.util.List<ComparacaoArmazens.Entrada> entradas = new java.util.ArrayList<>();
        for (Armazem a : lista) {
            String local = (a.getCidade() != null && a.getUf() != null) ? a.getCidade() + " - " + a.getUf() : "";
            entradas.add(new ComparacaoArmazens.Entrada(a.getId(), a.getNome(), local,
                    databaseService.movimentosDoArmazem(a.getId()), databaseService.despesasDoArmazem(a.getId())));
        }
        model.addAttribute("cmp", ComparacaoArmazens.comparar(entradas, p.de, p.ate));
        java.time.format.DateTimeFormatter br = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        model.addAttribute("periodoTexto", p.de.format(br) + " a " + p.ate.format(br));
        model.addAttribute("periodoSel", p.selecionado);
        model.addAttribute("periodoDe", p.de.toString());
        model.addAttribute("periodoAte", p.ate.toString());
        model.addAttribute("parceiroNome", session.getAttribute(S_PARCEIRO_NOME));
        return "conta-comparar";
    }

    @GetMapping("/armazens/novo")
    public String novoForm(@RequestParam(required = false) String plano, HttpSession session, Model model) {
        Long id = parceiroLogado(session);
        if (id == null) return "redirect:/login";
        model.addAttribute("plano", plano == null ? null : plano.trim().toUpperCase(java.util.Locale.ROOT));
        preencherPlanos(model, id);
        return "conta-armazem-novo";
    }

    @PostMapping("/armazens/novo")
    public String novoSalvar(@RequestParam String nome, @RequestParam(required = false) String cnpj,
                             @RequestParam String telefone, @RequestParam String endereco,
                             @RequestParam String uf, @RequestParam String cidade,
                             @RequestParam(required = false, defaultValue = "START") String plano,
                             HttpSession session, Model model) {
        Long id = parceiroLogado(session);
        if (id == null) return "redirect:/login";
        try {
            parceiroService.cadastrarArmazem(id, nome, cnpj, telefone, endereco, uf, cidade, plano);
            return "redirect:/conta/armazens?criado=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("nome", nome);
            model.addAttribute("cnpj", cnpj);
            model.addAttribute("telefone", telefone);
            model.addAttribute("endereco", endereco);
            model.addAttribute("uf", uf);
            model.addAttribute("cidade", cidade);
            model.addAttribute("plano", plano);
            preencherPlanos(model, id);
            return "conta-armazem-novo";
        }
    }

    /** Frase curta sobre a situação do armazém (teste grátis, bloqueio...). */
    static String textoSituacao(Armazem a, java.time.LocalDate hoje) {
        String status = nvl(a.getStatus(), "ATIVO");
        if ("ATIVO".equals(status)) return "Assinatura ativa";
        if ("TRIAL".equals(status)) {
            if (a.getDataCadastro() == null) return "Teste grátis";
            long restam = Assinatura.DIAS_TRIAL - java.time.temporal.ChronoUnit.DAYS.between(a.getDataCadastro(), hoje);
            if (restam > 1) return "Teste grátis: faltam " + restam + " dias";
            if (restam == 1) return "Teste grátis: falta 1 dia";
            if (restam == 0) return "Último dia do teste grátis";
            return "O teste grátis terminou. Ative a assinatura para voltar a operar.";
        }
        return "Acesso bloqueado. Regularize a assinatura para voltar a operar.";
    }

    // ---------------- foto do dono e logo do armazém ----------------

    private static org.springframework.http.ResponseEntity<byte[]> imagem(java.util.Optional<byte[]> dados) {
        if (dados.isEmpty()) return org.springframework.http.ResponseEntity.notFound().build();
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.IMAGE_JPEG).body(dados.get());
    }

    private org.springframework.http.ResponseEntity<String> gravarImagem(String tipo, Long refId,
                                                                       org.springframework.web.multipart.MultipartFile arquivo) {
        try {
            imagemContaService.salvar(tipo, refId, arquivo == null ? null : arquivo.getBytes());
            return org.springframework.http.ResponseEntity.ok("ok");
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest().contentType(new org.springframework.http.MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8)).body(e.getMessage());
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.internalServerError().contentType(new org.springframework.http.MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8)).body("Não foi possível salvar a imagem. Tente de novo.");
        }
    }

    @GetMapping("/foto")
    @ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> verFoto(HttpSession session) {
        Long id = parceiroLogado(session);
        if (id == null) return org.springframework.http.ResponseEntity.status(401).build();
        return imagem(imagemContaService.buscar(ImagemContaService.DONO, id));
    }

    @PostMapping("/foto")
    @ResponseBody
    public org.springframework.http.ResponseEntity<String> enviarFoto(@RequestParam(value = "arquivo", required = false) org.springframework.web.multipart.MultipartFile arquivo,
                                                                    HttpSession session) {
        Long id = parceiroLogado(session);
        if (id == null) return org.springframework.http.ResponseEntity.status(401).body("Sessão expirada. Entre de novo.");
        return gravarImagem(ImagemContaService.DONO, id, arquivo);
    }

    @PostMapping("/foto/remover")
    public String removerFoto(HttpSession session) {
        Long id = parceiroLogado(session);
        if (id == null) return "redirect:/login";
        imagemContaService.remover(ImagemContaService.DONO, id);
        return "redirect:/conta/armazens";
    }

    @GetMapping("/armazens/{id}/logo")
    @ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> verLogo(@PathVariable Long id, HttpSession session) {
        Long parceiroId = parceiroLogado(session);
        if (parceiroId == null) return org.springframework.http.ResponseEntity.status(401).build();
        if (parceiroService.buscarArmazemDoDono(id, parceiroId).isEmpty()) return org.springframework.http.ResponseEntity.notFound().build();
        return imagem(imagemContaService.buscar(ImagemContaService.ARMAZEM, id));
    }

    @PostMapping("/armazens/{id}/logo")
    @ResponseBody
    public org.springframework.http.ResponseEntity<String> enviarLogo(@PathVariable Long id,
                                                                    @RequestParam(value = "arquivo", required = false) org.springframework.web.multipart.MultipartFile arquivo,
                                                                    HttpSession session) {
        Long parceiroId = parceiroLogado(session);
        if (parceiroId == null) return org.springframework.http.ResponseEntity.status(401).body("Sessão expirada. Entre de novo.");
        if (parceiroService.buscarArmazemDoDono(id, parceiroId).isEmpty()) return org.springframework.http.ResponseEntity.status(403).body("Esse armazém não é da sua conta.");
        return gravarImagem(ImagemContaService.ARMAZEM, id, arquivo);
    }

    @PostMapping("/armazens/{id}/logo/remover")
    public String removerLogo(@PathVariable Long id, HttpSession session) {
        Long parceiroId = parceiroLogado(session);
        if (parceiroId == null) return "redirect:/login";
        if (parceiroService.buscarArmazemDoDono(id, parceiroId).isPresent()) imagemContaService.remover(ImagemContaService.ARMAZEM, id);
        return "redirect:/conta/armazens";
    }

    /** Dados da escolha de plano: situação do dono e preços (o valor extra do Personalizado vem da configuração). */
    private void preencherPlanos(Model model, Long donoId) {
        model.addAttribute("personalizado", parceiroService.temPersonalizado(donoId));
        model.addAttribute("somentePersonalizado", parceiroService.somentePersonalizado(donoId));
        model.addAttribute("precoStart", PrecoPlano.START);
        model.addAttribute("precoPro", PrecoPlano.PRO);
        model.addAttribute("precoExtra", precoArmazemExtra > 0 ? precoArmazemExtra : PrecoPlano.EXTRA_PADRAO);
    }

    /** Informa (ou corrige) estado e município de um armazém do próprio dono. */
    @PostMapping("/armazens/{id}/regiao")
    public String salvarRegiao(@PathVariable Long id, @RequestParam String uf, @RequestParam String cidade,
                               HttpSession session) {
        Long parceiroId = parceiroLogado(session);
        if (parceiroId == null) return "redirect:/login";
        try {
            parceiroService.definirRegiao(parceiroId, id, uf, cidade);
            return "redirect:/conta/armazens?regiao=ok";
        } catch (IllegalArgumentException e) {
            return "redirect:/conta/armazens?erro=regiao";
        }
    }

    /** Escolhe o armazém de trabalho. Só entra se o armazém for DESTE dono. */
    @PostMapping("/armazens/{id}/entrar")
    public String entrar(@PathVariable Long id, HttpSession session) {
        Long parceiroId = parceiroLogado(session);
        if (parceiroId == null) return "redirect:/login";

        Optional<Armazem> achado = parceiroService.buscarArmazemDoDono(id, parceiroId);
        if (achado.isEmpty()) return "redirect:/conta/armazens?erro=acesso";
        Armazem a = achado.get();

        // Mesmas variáveis de sessão que o login antigo (/login-admin) preenche para o GESTOR
        session.setAttribute("adminLogado", true);
        session.setAttribute("perfilUser", "GESTOR");
        session.setAttribute("idPlanilhaAtiva", String.valueOf(a.getId()));
        session.setAttribute("nomeArmazem", nvl(a.getNome(), "Armazém Parceiro"));
        session.setAttribute("cnpjArmazem", nvl(a.getCnpj(), ""));
        session.setAttribute("telefone", nvl(a.getTelefone(), ""));
        // dados para a revalidação periódica da sessão (ValidadorSessao): dono + impressão da senha atual
        session.setAttribute("origemLogin", "DONO");
        session.removeAttribute("credId");
        session.setAttribute("credFp", parceiroService.impressaoDaSenha(parceiroId));
        session.setAttribute("atendenteNome", nvl(String.valueOf(session.getAttribute(S_PARCEIRO_NOME)), "Proprietário"));
        session.removeAttribute("validadoEm");
        session.setAttribute("enderecoArmazem", nvl(a.getEndereco(), "Endereço não informado"));
        session.setAttribute("nomeFuncionarioLogado", "Gestor / Administrador");

        String status = nvl(a.getStatus(), "ATIVO");
        String plano = nvl(a.getPlano(), "START");
        session.setAttribute("planoAssinatura", plano);
        session.setAttribute("statusAssinatura", status);

        // mesma regra do login de operador: bloqueado, suspenso ou teste de 7 dias vencido => só a tela de assinatura
        if (com.reciclatech.backend.util.Assinatura.bloqueado(status, a.getDataCadastro(),
                java.time.LocalDate.now(java.time.ZoneId.of("America/Recife")))) {
            session.setAttribute("bloqueadoPagamento", true);
            return "redirect:/admin/assinatura";
        }
        session.removeAttribute("bloqueadoPagamento");
        return "redirect:/admin/coletas";
    }

    // ---------------- recuperação de senha ----------------

    @GetMapping("/esqueci-senha")
    public String esqueciForm() {
        return "conta-esqueci-senha";
    }

    @PostMapping("/esqueci-senha")
    public String esqueciEnviar(@RequestParam String email, Model model) {
        String chave = "reset:" + email;
        if (!throttle.bloqueado(chave)) {
            throttle.falhou(chave); // conta cada pedido: no máximo 5 e-mails a cada 15 min por endereço
            recuperacaoSenhaService.solicitar(email);
        }
        model.addAttribute("enviado", true); // mesma resposta exista ou não a conta
        return "conta-esqueci-senha";
    }

    @GetMapping("/redefinir-senha")
    public String redefinirForm(@RequestParam(required = false) String token, Model model) {
        boolean valido = token != null && recuperacaoSenhaService.tokenValido(token);
        model.addAttribute("tokenValido", valido);
        model.addAttribute("token", valido ? token : "");
        return "conta-redefinir-senha";
    }

    @PostMapping("/redefinir-senha")
    public String redefinir(@RequestParam String token, @RequestParam String senha,
                            @RequestParam String confirmarSenha, Model model) {
        String erro = null;
        if (!senha.equals(confirmarSenha)) {
            erro = "As senhas não conferem.";
        } else {
            try {
                if (recuperacaoSenhaService.redefinir(token, senha)) {
                    return "redirect:/login?senhaRedefinida=true";
                }
                model.addAttribute("tokenValido", false);
                model.addAttribute("token", "");
                return "conta-redefinir-senha";
            } catch (IllegalArgumentException e) {
                erro = e.getMessage();
            }
        }
        model.addAttribute("tokenValido", true);
        model.addAttribute("token", token);
        model.addAttribute("erro", erro);
        return "conta-redefinir-senha";
    }
}
