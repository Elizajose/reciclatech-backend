package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Material;
import com.reciclatech.backend.model.Oferta;
import com.reciclatech.backend.model.Usuario;
import com.reciclatech.backend.model.StatusColeta;
import com.reciclatech.backend.repository.MaterialRepository;
import com.reciclatech.backend.repository.OfertaRepository;
import com.reciclatech.backend.repository.UsuarioRepository;
import com.reciclatech.backend.service.GoogleSheetsService; // Novo motor

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class TelaController {

    // Repositórios mantidos temporariamente para evitar erros de compilação
    @Autowired(required = false) private OfertaRepository ofertaRepository;
    @Autowired(required = false) private UsuarioRepository usuarioRepository;
    @Autowired(required = false) private MaterialRepository materialRepository;

    // Injeção do novo serviço do Google
    @Autowired private GoogleSheetsService googleSheetsService;

    // --- HOME ATUALIZADA (Busca materiais da Planilha) ---
    @GetMapping("/")
    public String home(Model model) {
        try {
            // 1. Materiais para o formulário (da Planilha)
            List<Material> materiaisPlanilha = googleSheetsService.listarMateriais();
            model.addAttribute("materiais", materiaisPlanilha);

            // 2. Ranking Top 3 (Calculado direto dos dados da Planilha)
            List<RankingDTO> top3 = googleSheetsService.buscarRankingMateriais();
            model.addAttribute("topMateriais", top3);

            // 3. Volume Total para o Reciclômetro (Calculado da Planilha)
            Double total = googleSheetsService.calcularTotalReciclado();
            model.addAttribute("totalReciclado", total != null ? total : 0.0);

        } catch (Exception e) {
            // Se der erro no Google, mostramos o site vazio em vez de dar erro 500
            model.addAttribute("materiais", new ArrayList<>());
            model.addAttribute("topMateriais", new ArrayList<>());
            model.addAttribute("totalReciclado", 0.0);
            System.err.println("Erro ao carregar Home via Planilha: " + e.getMessage());
        }
        return "index";
    }

    // --- GESTÃO DE MATERIAIS (TOTALMENTE GOOGLE SHEETS) ---
    @GetMapping("/admin/precos")
    public String painelPrecos(Model m, HttpSession s) {
        if(s.getAttribute("adminLogado")==null) return "redirect:/login";
        try {
            m.addAttribute("materiais", googleSheetsService.listarMateriais());
        } catch (Exception e) {
            m.addAttribute("materiais", new ArrayList<>());
        }
        return "admin-precos";
    }

    @PostMapping("/admin/material/novo")
    public String novoMaterial(@RequestParam String nome,
                               @RequestParam String unidade,
                               @RequestParam Double preco,
                               HttpSession session) {
        if(session.getAttribute("adminLogado")==null) return "redirect:/login";

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
        if(session.getAttribute("adminLogado")==null) return "redirect:/login";
        try {
            googleSheetsService.deletarMaterial(id);
        } catch (Exception e) {
            System.err.println("Erro ao deletar: " + e.getMessage());
        }
        return "redirect:/admin/precos";
    }

    // --- SEÇÃO DE LOGIN E ACESSO ---
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

    @GetMapping("/sair") public String logout(HttpSession session) { session.invalidate(); return "redirect:/login"; }

    // --- EXTRATOS (Busca unidades da Planilha) ---
    @GetMapping("/extrato/{id}")
    public String gerarExtratoIndividual(@PathVariable Long id, Model model) {
        Usuario usuario = usuarioRepository.findById(id).orElse(null);
        if (usuario == null) return "redirect:/";

        List<Oferta> vendas = ofertaRepository.findByUsuarioIdAndStatus(id, Oferta.StatusOferta.VENDIDO);

        try {
            List<Material> mats = googleSheetsService.listarMateriais();
            Map<String, String> mapaUnidades = mats.stream()
                    .collect(Collectors.toMap(Material::getNome, Material::getUnidade));
            Map<String, BigDecimal> mapaPrecos = mats.stream()
                    .collect(Collectors.toMap(Material::getNome, Material::getPrecoPorKg));

            model.addAttribute("mapaUnidades", mapaUnidades);
            model.addAttribute("mapaPrecos", mapaPrecos);
        } catch (Exception e) {
            model.addAttribute("mapaUnidades", Map.of());
            model.addAttribute("mapaPrecos", Map.of());
        }

        BigDecimal total = vendas.stream().map(Oferta::getPrecoEstimado).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("vendedor", usuario);
        model.addAttribute("vendas", vendas);
        model.addAttribute("total", total);
        model.addAttribute("dataHoje", LocalDate.now());

        return "extrato";
    }

    // --- MÉTODOS QUE AINDA USAM REPOSITORY (Próximos da Migração) ---

    @PostMapping("/publicar")
    public String solicitarColeta(@RequestParam(required = false) List<String> materiaisSelecionados,
                                  @RequestParam String endereco,
                                  @RequestParam String nomeVendedor,
                                  @RequestParam String telefoneVendedor) {
        // TODO: Migrar para googleSheetsService.salvarUsuario()
        String zapLimpo = telefoneVendedor.replaceAll("\\D", "");
        Usuario user = usuarioRepository.findByTelefone(zapLimpo)
                .map(u -> { u.setNome(nomeVendedor); return usuarioRepository.save(u); })
                .orElseGet(() -> {
                    Usuario novo = new Usuario();
                    novo.setNome(nomeVendedor);
                    novo.setTelefone(zapLimpo);
                    novo.setTipo(Usuario.TipoUsuario.CATADOR);
                    return usuarioRepository.save(novo);
                });
        Oferta pedido = new Oferta();
        pedido.setMaterial("Solicitação de Coleta");
        pedido.setEndereco(endereco);
        pedido.setUsuario(user);
        pedido.setPeso(0.0);
        pedido.setPrecoEstimado(BigDecimal.ZERO);
        pedido.setStatus(Oferta.StatusOferta.DISPONIVEL);
        ofertaRepository.save(pedido);
        return "redirect:/?sucesso=true";
    }

    @// No TelaController.java, atualize este método:

    @GetMapping("/admin/coletas")
    public String telaListaColetas(Model model, HttpSession session) {
        // Verifica se o gestor está logado (usando a senha que você definiu no Render)
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";

        try {
            // Agora busca a lista direto da lógica da planilha!
            List<Usuario> pendentes = googleSheetsService.buscarUsuariosComColetasPendentes();
            model.addAttribute("usuarios", pendentes);
        } catch (IOException e) {
            model.addAttribute("usuarios", new java.util.ArrayList<>());
            model.addAttribute("erro", "Erro ao carregar dados do Google: " + e.getMessage());
        }

        return "admin-lista-coletas";
    }

    @GetMapping("/admin/atender/{idUsuario}")
    public String telaChecklist(@PathVariable Long idUsuario, Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        Usuario vendedor = usuarioRepository.findById(idUsuario).orElseThrow();
        model.addAttribute("vendedor", vendedor);
        try {
            model.addAttribute("todosMateriais", googleSheetsService.listarMateriais());
        } catch (Exception e) {
            model.addAttribute("todosMateriais", new ArrayList<>());
        }
        return "admin-checklist";
    }

    @PostMapping("/admin/revisar-coleta")
    public String revisarColeta(@RequestParam String idVendedor, @RequestParam Map<String, String> params, Model model) {
        try {
            // Buscamos o vendedor na aba de Usuarios (usamos o telefone/id)
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
                            // Busca o preço atualizado na planilha
                            Material mat = googleSheetsService.buscarMaterialPorId(idMaterial);
                            BigDecimal totalItem = mat.getPrecoPorKg().multiply(BigDecimal.valueOf(quantidade));

                            itensRevisao.add(new PreVendaDTO(mat, quantidade, totalItem));
                            totalEstimado = totalEstimado.add(totalItem);
                        }
                    } catch (Exception e) { /* Ignora campos vazios ou erros de formato */ }
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
                                       @RequestParam(required = false) String cpfFinal,
                                       @RequestParam List<Long> idsMateriais,
                                       @RequestParam List<Double> pesosFinais,
                                       @RequestParam List<Double> precosFinais) {
        try {
            // 1. Atualizamos o status do vendedor para CONCLUIDO na aba Usuarios
            // (Isso requer um método de 'atualizarUsuario' no Service, mas para o MVP
            // vamos focar em registrar as novas Ofertas VENDIDAS)

            for (int i = 0; i < idsMateriais.size(); i++) {
                Material mat = googleSheetsService.buscarMaterialPorId(idsMateriais.get(i));
                Double peso = pesosFinais.get(i);
                BigDecimal precoPago = BigDecimal.valueOf(precosFinais.get(i));

                Oferta venda = new Oferta();
                venda.setMaterial(mat.getNome());
                venda.setPeso(peso);
                venda.setStatus(Oferta.StatusOferta.VENDIDO);
                venda.setPrecoEstimado(precoPago.multiply(BigDecimal.valueOf(peso)));

                // Usamos o telefone do vendedor para vincular a oferta na planilha
                Usuario v = new Usuario();
                v.setTelefone(idVendedor);
                venda.setUsuario(v);

                // Salva a linha de venda na aba "Ofertas"
                googleSheetsService.salvarSolicitacaoInicial(v, "Coleta Finalizada");
                // Dica: Crie um método 'salvarVendaFinal' no Service para ser mais limpo depois
            }

            return "redirect:/extrato/" + idVendedor;

        } catch (IOException e) {
            return "redirect:/admin/coletas?erro=venda";
        }
    }

    @GetMapping("/meus-extratos")
    public String meusExtratos(Model model) {
        if (usuarioRepository == null) return "lista-extratos";
        List<Usuario> concluidosHoje = usuarioRepository.findByStatus(StatusColeta.CONCLUIDO)
                .stream()
                .filter(u -> u.getDataColeta() != null)
                .filter(u -> u.getDataColeta().toLocalDate().isEqual(LocalDate.now()))
                .collect(Collectors.toList());
        model.addAttribute("usuarios", concluidosHoje);
        return "lista-extratos";
    }

    @PostMapping("/admin/reset-sistema")
    public String resetSistema(HttpSession session) {
        if(session.getAttribute("adminLogado")==null) return "redirect:/login";
        if (ofertaRepository != null) ofertaRepository.deleteAll();
        if (usuarioRepository != null) usuarioRepository.deleteAll();
        return "redirect:/admin/coletas?msg=sistema_zerado";
    }

    // --- DTOs MANTIDOS ---
    public static class PreVendaDTO {
        public Material material;
        public Double peso;
        public BigDecimal total;
        public PreVendaDTO(Material m, Double p, BigDecimal t) { this.material=m; this.peso=p; this.total=t; }
    }

    public static class RankingDTO {
        public String nome;
        public Double peso;
        public String unidade;
        public RankingDTO(String n, Double p, String u) { this.nome = n; this.peso = p; this.unidade = u; }
    }
}