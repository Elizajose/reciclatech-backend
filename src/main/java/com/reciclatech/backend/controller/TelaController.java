package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Material;
import com.reciclatech.backend.model.Oferta;
import com.reciclatech.backend.model.Usuario;
import com.reciclatech.backend.service.GoogleSheetsService;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class TelaController {

    @Autowired private GoogleSheetsService googleSheetsService;

    @GetMapping("/")
    public String home(Model model) {
        try {
            model.addAttribute("materiais", googleSheetsService.listarMateriais());
            model.addAttribute("topMateriais", googleSheetsService.buscarRankingMateriais());
            Double total = googleSheetsService.calcularTotalReciclado();
            model.addAttribute("totalReciclado", total != null ? total : 0.0);
        } catch (Exception e) {
            model.addAttribute("materiais", new ArrayList<>());
            model.addAttribute("topMateriais", new ArrayList<>());
            model.addAttribute("totalReciclado", 0.0);
        }
        return "index";
    }

    // --- GESTÃO DE MATERIAIS ---
    @GetMapping("/admin/precos")
    public String painelPrecos(Model m, HttpSession s) {
        if(s.getAttribute("adminLogado") == null) return "redirect:/login";
        try {
            m.addAttribute("materiais", googleSheetsService.listarMateriais());
        } catch (Exception e) {
            m.addAttribute("materiais", new ArrayList<>());
        }
        return "admin-precos";
    }

    @PostMapping("/admin/material/novo")
    public String novoMaterial(@RequestParam String nome, @RequestParam String unidade,
                               @RequestParam Double preco, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        Material m = new Material();
        m.setNome(nome);
        m.setUnidade(unidade);
        m.setPrecoPorKg(BigDecimal.valueOf(preco));
        try { googleSheetsService.salvarMaterial(m); } catch (Exception e) { }
        return "redirect:/admin/precos";
    }

    @PostMapping("/admin/atualizar")
    public String upd(@RequestParam Long id, @RequestParam Double novoPreco) {
        try { googleSheetsService.atualizarPrecoMaterial(id, BigDecimal.valueOf(novoPreco)); } catch (Exception e) { }
        return "redirect:/admin/precos";
    }

    @GetMapping("/admin/material/deletar/{id}")
    public String deletarMaterial(@PathVariable Long id, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        try { googleSheetsService.deletarMaterial(id); } catch (Exception e) { }
        return "redirect:/admin/precos";
    }

    @GetMapping("/login") public String telaLogin() { return "login-admin"; }

    @PostMapping("/login-admin")
    public String login(@RequestParam String senha, HttpSession session) {
        String senhaSecreta = System.getenv("SENHA_ADMIN");
        if (senhaSecreta == null) senhaSecreta = "admin123";
        if (senhaSecreta.equals(senha)) {
            session.setAttribute("adminLogado", true);
            return "redirect:/admin/coletas";
        }
        return "redirect:/login?erro=true";
    }

    @GetMapping("/sair") public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    // --- OPERAÇÕES DE COLETA ---
    @PostMapping("/publicar")
    public String solicitarColeta(@RequestParam String endereco,
                                  @RequestParam String nomeVendedor,
                                  @RequestParam String telefoneVendedor) {
        Usuario novo = new Usuario();
        novo.setNome(nomeVendedor);
        novo.setTelefone(telefoneVendedor.replaceAll("\\D", ""));
        novo.setEndereco(endereco);
        novo.setTipo(Usuario.TipoUsuario.CATADOR);
        try { googleSheetsService.salvarSolicitacaoInicial(novo, endereco); } catch (IOException e) { }
        return "redirect:/?sucesso=true";
    }

    @GetMapping("/admin/coletas")
    public String telaListaColetas(Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        try {
            // Agora buscando o endereço corretamente pela coluna F do Service
            model.addAttribute("usuarios", googleSheetsService.buscarUsuariosComColetasPendentes());
        } catch (IOException e) {
            model.addAttribute("usuarios", new ArrayList<>());
        }
        return "admin-lista-coletas";
    }

    @GetMapping("/admin/atender/{idUsuario}")
    public String telaChecklist(@PathVariable String idUsuario, Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        try {
            Usuario vendedor = googleSheetsService.listarUsuarios().stream()
                    .filter(u -> u.getTelefone().equals(idUsuario) || u.getId().toString().equals(idUsuario))
                    .findFirst().orElseThrow();
            model.addAttribute("vendedor", vendedor);
            model.addAttribute("todosMateriais", googleSheetsService.listarMateriais());
        } catch (Exception e) { return "redirect:/admin/coletas?erro=usuario"; }
        return "admin-checklist";
    }

    @PostMapping("/admin/revisar-coleta")
    public String revisarColeta(@RequestParam String idVendedor, @RequestParam Map<String, String> params, Model model) {
        try {
            Usuario vendedor = googleSheetsService.listarUsuarios().stream()
                    .filter(u -> u.getTelefone().equals(idVendedor) || u.getId().toString().equals(idVendedor))
                    .findFirst().orElseThrow();
            List<PreVendaDTO> itensRevisao = new ArrayList<>();
            BigDecimal totalEstimado = BigDecimal.ZERO;
            for (String key : params.keySet()) {
                if (key.startsWith("qtd_") && !params.get(key).isEmpty()) {
                    try {
                        Long idMaterial = Long.parseLong(key.replace("qtd_", ""));
                        Double quantidade = Double.parseDouble(params.get(key));
                        if (quantidade > 0) {
                            Material mat = googleSheetsService.buscarMaterialPorId(idMaterial);
                            BigDecimal totalItem = mat.getPrecoPorKg().multiply(BigDecimal.valueOf(quantidade));
                            itensRevisao.add(new PreVendaDTO(mat, quantidade, totalItem));
                            totalEstimado = totalEstimado.add(totalItem);
                        }
                    } catch (Exception e) { }
                }
            }
            model.addAttribute("vendedor", vendedor);
            model.addAttribute("itens", itensRevisao);
            model.addAttribute("totalEstimado", totalEstimado);
            return "admin-revisao";
        } catch (IOException e) { return "redirect:/admin/coletas?erro=planilha"; }
    }

    @PostMapping("/admin/confirmar-finalizacao")
    public String confirmarFinalizacao(@RequestParam String idVendedor,
                                       @RequestParam(required = false) String cpfFinal,
                                       @RequestParam List<Long> idsMateriais,
                                       @RequestParam List<Double> pesosFinais,
                                       @RequestParam List<Double> precosFinais) {
        try {
            for (int i = 0; i < idsMateriais.size(); i++) {
                Material mat = googleSheetsService.buscarMaterialPorId(idsMateriais.get(i));
                BigDecimal precoUn = BigDecimal.valueOf(precosFinais.get(i));
                BigDecimal total = precoUn.multiply(BigDecimal.valueOf(pesosFinais.get(i)));
                googleSheetsService.registrarVendaFinal(idVendedor, mat.getNome(), pesosFinais.get(i), precoUn, total, cpfFinal);
            }
            // REDIRECIONAMENTO CORRIGIDO PARA BATER COM A ROTA ABAIXO
            return "redirect:/extrato/" + idVendedor;
        } catch (IOException e) { return "redirect:/admin/coletas?erro=venda"; }
    }

    // --- ROTA DE EXTRATO CORRIGIDA ---
    @GetMapping("/extrato/{id}")
    public String gerarExtratoIndividual(@PathVariable String id, Model model) {
        try {
            Usuario usuario = googleSheetsService.listarUsuarios().stream()
                    .filter(u -> u.getTelefone().equals(id) || u.getId().toString().equals(id))
                    .findFirst().orElse(null);

            if (usuario == null) return "redirect:/?erro=usuario_nao_encontrado";

            List<Oferta> vendasReais = googleSheetsService.buscarVendasPorUsuario(id);
            BigDecimal totalGeral = vendasReais.stream()
                    .map(Oferta::getPrecoEstimado)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            model.addAttribute("vendedor", usuario);
            model.addAttribute("vendas", vendasReais);
            model.addAttribute("total", totalGeral);
            model.addAttribute("dataHoje", LocalDate.now());
        } catch (Exception e) { return "redirect:/?erro=extrato"; }
        return "extrato";
    }

    // Rota opcional para busca geral se o usuário digitar /meus-extratos
    @GetMapping("/meus-extratos")
    public String buscaExtratosManual(@RequestParam(required = false) String telefone) {
        if(telefone != null) return "redirect:/extrato/" + telefone.replaceAll("\\D", "");
        return "busca-extrato";
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