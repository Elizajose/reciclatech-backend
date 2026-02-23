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
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class TelaController {

    @Autowired private GoogleSheetsService googleSheetsService;

    @GetMapping("/")
    public String home(Model model) {
        try {
            model.addAttribute("materiais", googleSheetsService.listarMateriais());
            model.addAttribute("topMateriais", googleSheetsService.buscarRankingMateriais());
            model.addAttribute("totalReciclado", googleSheetsService.calcularTotalReciclado());
        } catch (Exception e) { }
        return "index";
    }

    @GetMapping("/admin/precos")
    public String painelPrecos(Model m, HttpSession s) {
        if(s.getAttribute("adminLogado") == null) return "redirect:/login";
        try { m.addAttribute("materiais", googleSheetsService.listarMateriais()); } catch (Exception e) { }
        return "admin-precos";
    }

    @PostMapping("/admin/material/novo")
    public String novoMaterial(@RequestParam String nome, @RequestParam String unidade, @RequestParam Double preco, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        Material m = new Material(); m.setNome(nome); m.setUnidade(unidade); m.setPrecoPorKg(BigDecimal.valueOf(preco));
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
        String s = System.getenv("SENHA_ADMIN"); if (s == null) s = "admin123";
        if (s.equals(senha)) { session.setAttribute("adminLogado", true); return "redirect:/admin/coletas"; }
        return "redirect:/login?erro=true";
    }

    @GetMapping("/sair") public String logout(HttpSession session) { session.invalidate(); return "redirect:/login"; }

    @PostMapping("/publicar")
    public String solicitarColeta(@RequestParam String endereco, @RequestParam String nomeVendedor, @RequestParam String telefoneVendedor) {
        Usuario n = new Usuario(); n.setNome(nomeVendedor); n.setTelefone(telefoneVendedor.replaceAll("\\D", "")); n.setEndereco(endereco);
        try { googleSheetsService.salvarSolicitacaoInicial(n, endereco); } catch (IOException e) { }
        return "redirect:/?sucesso=true";
    }

    @GetMapping("/admin/coletas")
    public String telaListaColetas(Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        try { model.addAttribute("usuarios", googleSheetsService.buscarUsuariosComColetasPendentes()); } catch (IOException e) { }
        return "admin-lista-coletas";
    }

    @GetMapping("/admin/atender/{idUsuario}")
    public String telaChecklist(@PathVariable String idUsuario, Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        try {
            Usuario v = googleSheetsService.listarUsuarios().stream()
                    .filter(u -> u.getTelefone().equals(idUsuario) || u.getId().toString().equals(idUsuario))
                    .findFirst().orElseThrow();
            model.addAttribute("vendedor", v);
            model.addAttribute("todosMateriais", googleSheetsService.listarMateriais());
        } catch (Exception e) { return "redirect:/admin/coletas?erro=usuario"; }
        return "admin-checklist";
    }

    @PostMapping("/admin/revisar-coleta")
    public String revisarColeta(@RequestParam String idVendedor, @RequestParam Map<String, String> params, Model model) {
        try {
            Usuario v = googleSheetsService.listarUsuarios().stream()
                    .filter(u -> u.getTelefone().equals(idVendedor) || u.getId().toString().equals(idVendedor))
                    .findFirst().orElseThrow();
            List<PreVendaDTO> itens = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (String key : params.keySet()) {
                if (key.startsWith("qtd_") && !params.get(key).isEmpty()) {
                    try {
                        Long idMat = Long.parseLong(key.replace("qtd_", ""));
                        Double qtd = Double.parseDouble(params.get(key));
                        if (qtd > 0) {
                            Material m = googleSheetsService.buscarMaterialPorId(idMat);
                            BigDecimal sub = m.getPrecoPorKg().multiply(BigDecimal.valueOf(qtd));
                            itens.add(new PreVendaDTO(m, qtd, sub));
                            total = total.add(sub);
                        }
                    } catch (Exception e) { }
                }
            }
            model.addAttribute("vendedor", v); model.addAttribute("itens", itens); model.addAttribute("totalEstimado", total);
            return "admin-revisao";
        } catch (Exception e) { return "redirect:/admin/coletas?erro=revisao"; }
    }

    @PostMapping("/admin/confirmar-finalizacao")
    public String confirmarFinalizacao(@RequestParam String idVendedor, @RequestParam(required = false) String cpfFinal,
                                       @RequestParam List<Long> idsMateriais, @RequestParam List<Double> pesosFinais, @RequestParam List<Double> precosFinais) {
        try {
            for (int i = 0; i < idsMateriais.size(); i++) {
                Material m = googleSheetsService.buscarMaterialPorId(idsMateriais.get(i));
                BigDecimal pr = BigDecimal.valueOf(precosFinais.get(i));
                BigDecimal t = pr.multiply(BigDecimal.valueOf(pesosFinais.get(i)));
                googleSheetsService.registrarVendaFinal(idVendedor, m.getNome(), pesosFinais.get(i), pr, t, cpfFinal);
            }
            return "redirect:/extrato/" + idVendedor;
        } catch (Exception e) { return "redirect:/admin/coletas?erro=venda"; }
    }

    @GetMapping("/extrato/{id}")
    public String gerarExtratoIndividual(@PathVariable String id, Model model) {
        try {
            Usuario u = googleSheetsService.listarUsuarios().stream()
                    .filter(user -> user.getTelefone().equals(id) || user.getId().toString().equals(id))
                    .findFirst().orElse(null);
            if (u == null) return "redirect:/?erro=usuario_nao_encontrado";
            List<Oferta> vendas = googleSheetsService.buscarVendasPorUsuario(id);
            BigDecimal total = vendas.stream().map(Oferta::getPrecoEstimado).reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("vendedor", u); model.addAttribute("vendas", vendas); model.addAttribute("total", total); model.addAttribute("dataHoje", LocalDate.now());
        } catch (Exception e) { return "redirect:/?erro=extrato"; }
        return "extrato";
    }

    @GetMapping("/meus-extratos")
    public String buscaManual(@RequestParam(required = false) String telefone) {
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