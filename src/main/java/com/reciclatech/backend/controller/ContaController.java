package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Armazem;
import com.reciclatech.backend.model.Parceiro;
import com.reciclatech.backend.service.DatabaseService;
import com.reciclatech.backend.service.LoginThrottle;
import com.reciclatech.backend.service.ParceiroService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
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
    private final DatabaseService databaseService;
    private final LoginThrottle throttle;

    public ContaController(ParceiroService parceiroService, DatabaseService databaseService, LoginThrottle throttle) {
        this.parceiroService = parceiroService;
        this.databaseService = databaseService;
        this.throttle = throttle;
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
                return "redirect:/conta/login?criada=true";
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

    @GetMapping("/login")
    public String loginForm(HttpSession session) {
        if (parceiroLogado(session) != null) return "redirect:/conta/armazens";
        return "conta-login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String senha,
                        HttpServletRequest request, Model model) {
        String chave = "login:" + email;
        if (throttle.bloqueado(chave)) {
            model.addAttribute("erro", "Muitas tentativas. Aguarde alguns minutos e tente de novo.");
            model.addAttribute("email", email);
            return "conta-login";
        }

        Optional<Parceiro> parceiro = parceiroService.autenticar(email, senha);
        if (parceiro.isEmpty()) {
            throttle.falhou(chave);
            model.addAttribute("erro", "E-mail ou senha inválidos.");
            model.addAttribute("email", email);
            return "conta-login";
        }

        throttle.sucesso(chave);
        request.getSession(true);
        request.changeSessionId(); // evita fixação de sessão
        HttpSession session = request.getSession();
        session.setAttribute(S_PARCEIRO_ID, parceiro.get().getId());
        session.setAttribute(S_PARCEIRO_NOME, parceiro.get().getNome());
        return "redirect:/conta/armazens";
    }

    @PostMapping("/sair")
    public String sair(HttpSession session) {
        session.invalidate();
        return "redirect:/conta/login";
    }

    // ---------------- armazéns do dono ----------------

    @GetMapping("/armazens")
    public String armazens(HttpSession session, Model model) {
        Long id = parceiroLogado(session);
        if (id == null) return "redirect:/conta/login";
        model.addAttribute("armazens", parceiroService.listarArmazens(id));
        model.addAttribute("parceiroNome", session.getAttribute(S_PARCEIRO_NOME));
        return "conta-armazens";
    }

    @GetMapping("/armazens/novo")
    public String novoForm(HttpSession session) {
        if (parceiroLogado(session) == null) return "redirect:/conta/login";
        return "conta-armazem-novo";
    }

    @PostMapping("/armazens/novo")
    public String novoSalvar(@RequestParam String nome, @RequestParam(required = false) String cnpj,
                             @RequestParam String telefone, @RequestParam String endereco,
                             @RequestParam(required = false, defaultValue = "START") String plano,
                             HttpSession session, Model model) {
        Long id = parceiroLogado(session);
        if (id == null) return "redirect:/conta/login";
        try {
            parceiroService.cadastrarArmazem(id, nome, cnpj, telefone, endereco, plano);
            return "redirect:/conta/armazens?criado=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("nome", nome);
            model.addAttribute("cnpj", cnpj);
            model.addAttribute("telefone", telefone);
            model.addAttribute("endereco", endereco);
            model.addAttribute("plano", plano);
            return "conta-armazem-novo";
        }
    }

    /** Escolhe o armazém de trabalho. Só entra se o armazém for DESTE dono. */
    @PostMapping("/armazens/{id}/entrar")
    public String entrar(@PathVariable Long id, HttpSession session) {
        Long parceiroId = parceiroLogado(session);
        if (parceiroId == null) return "redirect:/conta/login";

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
        session.setAttribute("senhaLogin", nvl(a.getSenha(), ""));
        session.setAttribute("enderecoArmazem", nvl(a.getEndereco(), "Endereço não informado"));
        session.setAttribute("nomeFuncionarioLogado", "Gestor / Administrador");

        String status = nvl(a.getStatus(), "ATIVO");
        String plano = nvl(a.getPlano(), "START");
        session.setAttribute("planoAssinatura", plano);
        session.setAttribute("statusAssinatura", status);

        if ("BLOQUEADO".equals(status)) {
            session.setAttribute("bloqueadoPagamento", true);
            return "redirect:/admin/assinatura";
        }
        session.removeAttribute("bloqueadoPagamento");
        return "redirect:/admin/coletas";
    }

    /** Ponte para quem já tem armazém com login/senha antigos: prova a posse e liga o armazém à conta. */
    @PostMapping("/armazens/vincular")
    public String vincular(@RequestParam String login, @RequestParam String senha, HttpSession session) {
        Long parceiroId = parceiroLogado(session);
        if (parceiroId == null) return "redirect:/conta/login";

        String chave = "vincular:" + parceiroId;
        if (throttle.bloqueado(chave)) return "redirect:/conta/armazens?erro=tentativas";

        Map<String, String> dados = databaseService.autenticarSaaS(login, senha);
        if (dados == null || !"GESTOR".equals(dados.get("perfil")) || dados.get("idPlanilha") == null) {
            throttle.falhou(chave);
            return "redirect:/conta/armazens?erro=vinculo";
        }
        try {
            parceiroService.vincularArmazemLegado(parceiroId, Long.valueOf(dados.get("idPlanilha")));
            throttle.sucesso(chave);
            return "redirect:/conta/armazens?vinculado=true";
        } catch (IllegalArgumentException e) {
            return "redirect:/conta/armazens?erro=ja_vinculado";
        }
    }
}
