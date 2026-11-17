package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Armazem;
import com.reciclatech.backend.model.Parceiro;
import com.reciclatech.backend.service.ComparacaoArmazens;
import com.reciclatech.backend.service.DatabaseService;
import com.reciclatech.backend.service.LoginThrottle;
import com.reciclatech.backend.util.PeriodoComparacao;
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

    public ContaController(ParceiroService parceiroService,
                           LoginThrottle throttle, RecuperacaoSenhaService recuperacaoSenhaService,
                           DatabaseService databaseService) {
        this.databaseService = databaseService;
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
        model.addAttribute("podeNovo", parceiroService.podeCadastrarOutro(id));
        model.addAttribute("personalizado", parceiroService.temPersonalizado(id));
        model.addAttribute("podeComparar", lista.size() >= 2);
        model.addAttribute("parceiroNome", session.getAttribute(S_PARCEIRO_NOME));
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
    public String novoForm(HttpSession session, Model model) {
        Long id = parceiroLogado(session);
        if (id == null) return "redirect:/login";
        if (!parceiroService.podeCadastrarOutro(id)) return "redirect:/conta/armazens?erro=limite";
        model.addAttribute("personalizado", parceiroService.temPersonalizado(id));
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
            model.addAttribute("personalizado", parceiroService.temPersonalizado(id));
            return "conta-armazem-novo";
        }
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
