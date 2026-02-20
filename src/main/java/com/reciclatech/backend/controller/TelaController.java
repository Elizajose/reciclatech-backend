package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Material;
import com.reciclatech.backend.model.Oferta;
import com.reciclatech.backend.model.Usuario;
import com.reciclatech.backend.model.StatusColeta;
import com.reciclatech.backend.repository.MaterialRepository;
import com.reciclatech.backend.repository.OfertaRepository;
import com.reciclatech.backend.repository.UsuarioRepository;
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

    @Autowired(required = false) private OfertaRepository ofertaRepository;
    @Autowired(required = false) private UsuarioRepository usuarioRepository;
    @Autowired(required = false) private MaterialRepository materialRepository;

    @Autowired private GoogleSheetsService googleSheetsService;

    // --- HOME (Reciclômetro e Ranking via Google Sheets) ---
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

        try {
            googleSheetsService.salvarMaterial(m);
        } catch (Exception e) {
            System.err.println("Erro ao gravar material: " + e.getMessage());
        }
        return "redirect:/admin/precos";
    }

    @PostMapping("/admin/atualizar")
    public String upd(@RequestParam Long id, @RequestParam Double novoPreco) {
        try {
            googleSheetsService.atualizarPrecoMaterial(id, BigDecimal.valueOf(novoPreco));
        } catch (Exception e) {
            System.err.println("Erro ao atualizar preço: " + e.getMessage());
        }
        return "redirect:/admin/precos";
    }

    @GetMapping("/admin/material/deletar/{id}")
    public String deletarMaterial(@PathVariable Long id, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";
        try {
            googleSheetsService.deletarMaterial(id);
        } catch (Exception e) {
            System.err.println("Erro ao deletar: " + e.getMessage());
        }
        return "redirect:/admin/precos";
    }

    // --- LOGIN ---
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

    // --- OPERAÇÕES DE COLETA (Agora via Planilha) ---
    @PostMapping("/publicar")
    public String solicitarColeta(@RequestParam String endereco,
                                  @RequestParam String nomeVendedor,
                                  @RequestParam String telefoneVendedor) {
        String zapLimpo = telefoneVendedor.replaceAll("\\D", "");
        Usuario novo = new Usuario();
        novo.setNome(nomeVendedor);
        novo.setTelefone(zapLimpo);
        novo.setEndereco(endereco);
        novo.setTipo(Usuario.TipoUsuario.CATADOR);

        try {
            googleSheetsService.salvarSolicitacaoInicial(novo, endereco);
        } catch (IOException e) {
            System.err.println("Erro ao publicar: " + e.getMessage());
        }
        return "redirect:/?sucesso=true";
    }

    @GetMapping("/admin/coletas")
    public String telaListaColetas(Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        try {
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
            // Busca o vendedor na lista da planilha
            Usuario vendedor = googleSheetsService.listarUsuarios().stream()
                    .filter(u -> u.getTelefone().equals(idUsuario) || u.getId().toString().equals(idUsuario))
                    .findFirst().orElseThrow();

            model.addAttribute("vendedor", vendedor);
            model.addAttribute("todosMateriais", googleSheetsService.listarMateriais());
        } catch (Exception e) {
            return "redirect:/admin/coletas?erro=usuario";
        }
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
        } catch (IOException e) {
            return "redirect:/admin/coletas?erro=planilha";
        }
    }

    @PostMapping("/admin/confirmar-finalizacao")
    public String confirmarFinalizacao(@RequestParam String idVendedor,
                                       @RequestParam List<Long> idsMateriais,
                                       @RequestParam List<Double> pesosFinais,
                                       @RequestParam List<Double> precosFinais) {
        try {
            for (int i = 0; i < idsMateriais.size(); i++) {
                Material mat = googleSheetsService.buscarMaterialPorId(idsMateriais.get(i));
                Usuario v = new Usuario();
                v.setTelefone(idVendedor);

                // Registra como VENDIDO na aba Ofertas
                // Aqui você pode criar um método específico no service se desejar separar de 'solicitacaoInicial'
                googleSheetsService.salvarSolicitacaoInicial(v, "Venda Finalizada: " + mat.getNome());
            }
            return "redirect:/extrato/" + idVendedor;
        } catch (IOException e) {
            return "redirect:/admin/coletas?erro=venda";
        }
    }

    @GetMapping("/extrato/{id}")
    public String gerarExtratoIndividual(@PathVariable String id, Model model) {
        try {
            Usuario usuario = googleSheetsService.listarUsuarios().stream()
                    .filter(u -> u.getTelefone().equals(id) || u.getId().toString().equals(id))
                    .findFirst().orElse(null);

            if (usuario == null) return "redirect:/";

            List<Material> mats = googleSheetsService.listarMateriais();
            model.addAttribute("mapaUnidades", mats.stream().collect(Collectors.toMap(Material::getNome, Material::getUnidade)));
            model.addAttribute("mapaPrecos", mats.stream().collect(Collectors.toMap(Material::getNome, Material::getPrecoPorKg)));
            model.addAttribute("vendedor", usuario);
            model.addAttribute("vendas", new ArrayList<>()); // TODO: Buscar vendas reais da aba Ofertas
            model.addAttribute("total", BigDecimal.ZERO);
            model.addAttribute("dataHoje", LocalDate.now());
        } catch (Exception e) {
            return "redirect:/?erro=extrato";
        }
        return "extrato";
    }

    // --- DTOs ---
    public static class PreVendaDTO {
        public Material material;
        public Double peso;
        public BigDecimal total;
        public PreVendaDTO(Material m, Double p, BigDecimal t) { this.material = m; this.peso = p; this.total = t; }
    }

    public static class RankingDTO {
        public String nome;
        public Double peso;
        public String unidade;
        public RankingDTO(String n, Double p, String u) { this.nome = n; this.peso = p; this.unidade = u; }
    }
}