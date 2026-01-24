package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Material;
import com.reciclatech.backend.model.Oferta;
import com.reciclatech.backend.model.Usuario;
import com.reciclatech.backend.model.StatusColeta;
import com.reciclatech.backend.repository.MaterialRepository;
import com.reciclatech.backend.repository.OfertaRepository;
import com.reciclatech.backend.repository.UsuarioRepository;

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

    @Autowired private OfertaRepository ofertaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private MaterialRepository materialRepository;

    // --- HOME COM RECICLÔMETRO ---
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("materiais", materialRepository.findAll());

        // 1. Total Geral (Para o número grandão)
        Double total = ofertaRepository.somarPesoTotal(Oferta.StatusOferta.VENDIDO);
        model.addAttribute("totalReciclado", total != null ? total : 0.0);

        // 2. Ranking Top 3 (Para a lista amarela abaixo)
        List<Object[]> ranking = ofertaRepository.findRankingMateriais();
        List<RankingDTO> top3 = new ArrayList<>();

        // Pega apenas os 3 primeiros (ou menos, se tiver pouco dado)
        int limite = Math.min(ranking.size(), 3);
        for (int i = 0; i < limite; i++) {
            Object[] row = ranking.get(i);
            String nome = (String) row[0];
            Double peso = (Double) row[1];
            top3.add(new RankingDTO(nome, peso));
        }
        model.addAttribute("topMateriais", top3);

        return "index";
    }

    // --- GESTÃO DE MATERIAIS (ADICIONAR/REMOVER) ---
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
        materialRepository.save(m);
        return "redirect:/admin/precos";
    }

    @GetMapping("/admin/material/deletar/{id}")
    public String deletarMaterial(@PathVariable Long id, HttpSession session) {
        if(session.getAttribute("adminLogado")==null) return "redirect:/login";

        // Nota: Se já tiver oferta com esse material, pode dar erro de chave estrangeira.
        // O ideal seria "desativar", mas para simplificar vamos tentar deletar.
        try {
            materialRepository.deleteById(id);
        } catch (Exception e) {
            // Se der erro (ex: já tem vendas), apenas ignora por enquanto
            System.out.println("Não foi possível deletar: " + e.getMessage());
        }
        return "redirect:/admin/precos";
    }

    // --- MANTIVE TODO O RESTO IGUAL ---

    @GetMapping("/login") public String telaLogin() { return "login-admin"; }

    // ---  ALTERAÇÃO DE SEGURANÇA ---
    @PostMapping("/login-admin")
    public String login(@RequestParam String senha, HttpSession session) {

        // 1. Busca a senha configurada no Servidor
        String senhaSecreta = System.getenv("SENHA_ADMIN");


        if (senhaSecreta == null) {
            senhaSecreta = "admin123";
        }

        // 3. Compara a senha
        if (senhaSecreta.equals(senha)) {
            session.setAttribute("adminLogado", true);
            return "redirect:/admin/coletas";
        }

        return "redirect:/login?erro=true";
    }
    // ------------------------------------------------

    @PostMapping("/publicar")
    public String solicitarColeta(@RequestParam(required = false) List<String> materiaisSelecionados,
                                  @RequestParam String endereco,
                                  @RequestParam String nomeVendedor,
                                  @RequestParam String telefoneVendedor) {
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

    @GetMapping("/admin/coletas")
    public String telaListaColetas(Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        List<Long> idsPendentes = ofertaRepository.findByStatus(Oferta.StatusOferta.DISPONIVEL).stream()
                .map(o -> o.getUsuario().getId()).distinct().toList();
        model.addAttribute("usuarios", usuarioRepository.findAllById(idsPendentes));
        return "admin-lista-coletas";
    }

    @GetMapping("/admin/atender/{idUsuario}")
    public String telaChecklist(@PathVariable Long idUsuario, Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        Usuario vendedor = usuarioRepository.findById(idUsuario).orElseThrow();
        model.addAttribute("vendedor", vendedor);
        model.addAttribute("todosMateriais", materialRepository.findAll());
        return "admin-checklist";
    }

    @PostMapping("/admin/revisar-coleta")
    public String revisarColeta(@RequestParam Long idVendedor, @RequestParam Map<String, String> params, Model model) {
        Usuario vendedor = usuarioRepository.findById(idVendedor).orElseThrow();
        List<PreVendaDTO> itensRevisao = new ArrayList<>();
        BigDecimal totalEstimado = BigDecimal.ZERO;
        for (String key : params.keySet()) {
            if (key.startsWith("qtd_") && !params.get(key).isEmpty()) {
                try {
                    Long idMaterial = Long.parseLong(key.replace("qtd_", ""));
                    Double quantidade = Double.parseDouble(params.get(key));
                    if (quantidade > 0) {
                        Material mat = materialRepository.findById(idMaterial).orElseThrow();
                        BigDecimal totalItem = mat.getPrecoPorKg().multiply(BigDecimal.valueOf(quantidade));
                        itensRevisao.add(new PreVendaDTO(mat, quantidade, totalItem));
                        totalEstimado = totalEstimado.add(totalItem);
                    }
                } catch (NumberFormatException e) {}
            }
        }
        model.addAttribute("vendedor", vendedor);
        model.addAttribute("itens", itensRevisao);
        model.addAttribute("totalEstimado", totalEstimado);
        return "admin-revisao";
    }

    @PostMapping("/admin/confirmar-finalizacao")
    public String confirmarFinalizacao(@RequestParam Long idVendedor, @RequestParam(required = false) String cpfFinal,
                                       @RequestParam List<Long> idsMateriais, @RequestParam List<Double> pesosFinais,
                                       @RequestParam List<Double> precosFinais) {
        Usuario vendedor = usuarioRepository.findById(idVendedor).orElseThrow();
        if (cpfFinal != null && !cpfFinal.isEmpty()) { vendedor.setCpf(cpfFinal); }
        vendedor.setStatus(StatusColeta.CONCLUIDO);
        vendedor.setDataColeta(java.time.LocalDateTime.now());
        usuarioRepository.save(vendedor);

        List<Oferta> antigas = ofertaRepository.findByUsuarioIdAndStatus(idVendedor, Oferta.StatusOferta.DISPONIVEL);
        String enderecoSalvo = antigas.isEmpty() ? "Local" : antigas.get(0).getEndereco();
        ofertaRepository.deleteAll(antigas);

        for (int i = 0; i < idsMateriais.size(); i++) {
            Material mat = materialRepository.findById(idsMateriais.get(i)).orElseThrow();
            Double peso = pesosFinais.get(i);
            Double precoUnitario = precosFinais.get(i);
            Oferta venda = new Oferta();
            venda.setMaterial(mat.getNome());
            venda.setPeso(peso);
            venda.setUsuario(vendedor);
            venda.setStatus(Oferta.StatusOferta.VENDIDO);
            venda.setEndereco(enderecoSalvo);
            venda.setPrecoEstimado(BigDecimal.valueOf(precoUnitario).multiply(BigDecimal.valueOf(peso)));
            ofertaRepository.save(venda);
        }
        return "redirect:/extrato/" + idVendedor;
    }

    @GetMapping("/extrato/{id}")
    public String gerarExtratoIndividual(@PathVariable Long id, Model model) {
        Usuario usuario = usuarioRepository.findById(id).orElse(null);
        if (usuario == null) return "redirect:/";
        List<Oferta> vendas = ofertaRepository.findByUsuarioIdAndStatus(id, Oferta.StatusOferta.VENDIDO);
        Map<String, String> mapaUnidades = materialRepository.findAll().stream()
                .collect(Collectors.toMap(Material::getNome, Material::getUnidade));
        BigDecimal total = vendas.stream().map(Oferta::getPrecoEstimado).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("vendedor", usuario);
        model.addAttribute("vendas", vendas);
        model.addAttribute("total", total);
        model.addAttribute("dataHoje", LocalDate.now());
        model.addAttribute("mapaUnidades", mapaUnidades);
        return "extrato";
    }

    @GetMapping("/meus-extratos")
    public String meusExtratos(Model model) {
        List<Usuario> concluidosHoje = usuarioRepository.findByStatus(StatusColeta.CONCLUIDO)
                .stream()
                .filter(u -> u.getDataColeta() != null)
                .filter(u -> u.getDataColeta().toLocalDate().isEqual(LocalDate.now()))
                .collect(Collectors.toList());
        model.addAttribute("usuarios", concluidosHoje);
        return "lista-extratos";
    }

    @GetMapping("/admin/precos")
    public String painelPrecos(Model m, HttpSession s) {
        if(s.getAttribute("adminLogado")==null) return "redirect:/login";
        m.addAttribute("materiais", materialRepository.findAll());
        return "admin-precos";
    }

    @PostMapping("/admin/atualizar")
    public String upd(@RequestParam Long id, @RequestParam Double novoPreco) {
        Material m = materialRepository.findById(id).get();
        m.setPrecoPorKg(BigDecimal.valueOf(novoPreco));
        materialRepository.save(m);
        return "redirect:/admin/precos";
    }

    @GetMapping("/sair") public String logout(HttpSession session) { session.invalidate(); return "redirect:/login"; }

    public static class PreVendaDTO {
        public Material material;
        public Double peso;
        public BigDecimal total;
        public PreVendaDTO(Material m, Double p, BigDecimal t) { this.material=m; this.peso=p; this.total=t; }
    }

    public static class RankingDTO {
        public String nome;
        public Double peso;
        public RankingDTO(String n, Double p) { this.nome = n; this.peso = p; }
    }
}