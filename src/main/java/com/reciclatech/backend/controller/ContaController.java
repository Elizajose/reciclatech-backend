package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Armazem;
import com.reciclatech.backend.model.Parceiro;
import com.reciclatech.backend.service.LoginThrottle;
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

    public ContaController(ParceiroService parceiroService,
                           LoginThrottle throttle, RecuperacaoSenhaService recuperacaoSenhaService) {
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
                    return "redirect:/conta/login?senhaRedefinida=true";
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
