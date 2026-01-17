package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Material;
import com.reciclatech.backend.model.Oferta;
import com.reciclatech.backend.model.Usuario;
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

    // --- HOME E LOGIN ---
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("materiais", materialRepository.findAll());
        return "index";
    }

    @GetMapping("/login") public String telaLogin() { return "login-admin"; }
    @PostMapping("/login-admin")
    public String login(@RequestParam String senha, HttpSession session) {
        // TROQUE "SUA_SENHA_FORTE_AQUI" pelo que você quiser
        if ("pych@rmy13".equals(senha)) {
            session.setAttribute("adminLogado", true);
            return "redirect:/admin/coletas";
        }
        return "redirect:/login?erro=true";
    }

    // --- FLUXO DO CLIENTE (SOLICITAÇÃO) ---
    // CORREÇÃO AQUI: Agora os nomes batem com o HTML (nomeVendedor, telefoneVendedor...)
    @PostMapping("/publicar")
    public String solicitarColeta(@RequestParam(required = false) List<String> materiaisSelecionados,
                                  @RequestParam String endereco,
                                  @RequestParam String nomeVendedor,
                                  @RequestParam String telefoneVendedor) {

        String zapLimpo = telefoneVendedor.replaceAll("\\D", "");

        // Busca ou Cria Usuário
        Usuario user = usuarioRepository.findByTelefone(zapLimpo)
                .map(u -> { u.setNome(nomeVendedor); return usuarioRepository.save(u); })
                .orElseGet(() -> {
                    Usuario novo = new Usuario();
                    novo.setNome(nomeVendedor);
                    novo.setTelefone(zapLimpo);
                    novo.setTipo(Usuario.TipoUsuario.CATADOR);
                    return usuarioRepository.save(novo);
                });

        // Cria um pedido genérico na fila para a equipe ver
        Oferta pedido = new Oferta();
        pedido.setMaterial("Solicitação de Coleta");
        pedido.setEndereco(endereco);
        pedido.setVendedor(user);
        pedido.setPeso(0.0);
        pedido.setPrecoEstimado(BigDecimal.ZERO);
        pedido.setStatus(Oferta.StatusOferta.DISPONIVEL); // Fica pendente

        ofertaRepository.save(pedido);

        return "redirect:/?sucesso=true";
    }

    // --- FLUXO DA EQUIPE ---

    // 1. LISTA DE CLIENTES AGUARDANDO
    @GetMapping("/admin/coletas")
    public String telaListaColetas(Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";

        List<Long> idsPendentes = ofertaRepository.findByStatus(Oferta.StatusOferta.DISPONIVEL).stream()
                .map(o -> o.getVendedor().getId()).distinct().toList();

        model.addAttribute("usuarios", usuarioRepository.findAllById(idsPendentes));
        return "admin-lista-coletas";
    }

    // 2. CHECKLIST DE PESAGEM
    @GetMapping("/admin/atender/{idUsuario}")
    public String telaChecklist(@PathVariable Long idUsuario, Model model, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";

        Usuario vendedor = usuarioRepository.findById(idUsuario).orElseThrow();
        model.addAttribute("vendedor", vendedor);
        model.addAttribute("todosMateriais", materialRepository.findAll());
        return "admin-checklist";
    }

    // 3. TELA DE REVISÃO (Intermediária)
    @PostMapping("/admin/revisar-coleta")
    public String revisarColeta(@RequestParam Long idVendedor,
                                @RequestParam Map<String, String> params,
                                Model model) {

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

    // 4. FINALIZAR E GERAR RECIBO
    @PostMapping("/admin/confirmar-finalizacao")
    public String confirmarFinalizacao(@RequestParam Long idVendedor,
                                       @RequestParam(required = false) String cpfFinal,
                                       @RequestParam List<Long> idsMateriais,
                                       @RequestParam List<Double> pesosFinais,
                                       @RequestParam List<Double> precosFinais) {

        Usuario vendedor = usuarioRepository.findById(idVendedor).orElseThrow();

        if (cpfFinal != null && !cpfFinal.isEmpty()) {
            vendedor.setCpf(cpfFinal);
            usuarioRepository.save(vendedor);
        }

        // Limpa a fila de espera
        List<Oferta> antigas = ofertaRepository.findByVendedorIdAndStatus(idVendedor, Oferta.StatusOferta.DISPONIVEL);
        String enderecoSalvo = antigas.isEmpty() ? "Local" : antigas.get(0).getEndereco();
        ofertaRepository.deleteAll(antigas);

        // Salva as vendas reais
        for (int i = 0; i < idsMateriais.size(); i++) {
            Material mat = materialRepository.findById(idsMateriais.get(i)).orElseThrow();
            Double peso = pesosFinais.get(i);
            Double precoUnitario = precosFinais.get(i);

            Oferta venda = new Oferta();
            venda.setMaterial(mat.getNome());
            venda.setPeso(peso);
            venda.setVendedor(vendedor);
            venda.setStatus(Oferta.StatusOferta.VENDIDO);
            venda.setEndereco(enderecoSalvo);
            venda.setPrecoEstimado(BigDecimal.valueOf(precoUnitario).multiply(BigDecimal.valueOf(peso)));

            ofertaRepository.save(venda);
        }

        return "redirect:/extrato/" + idVendedor;
    }

    // --- EXTRATO E PREÇOS ---
    @GetMapping("/extrato/{id}")
    public String gerarExtratoIndividual(@PathVariable Long id, Model model) {
        Usuario usuario = usuarioRepository.findById(id).orElse(null);
        if (usuario == null) return "redirect:/";

        List<Oferta> vendas = ofertaRepository.findByVendedorIdAndStatus(id, Oferta.StatusOferta.VENDIDO);
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

    // Auxiliares
    @GetMapping("/meus-extratos") public String listarExtratos(Model m) { m.addAttribute("usuarios", usuarioRepository.findAll()); return "lista-extratos"; }
    @GetMapping("/admin/precos") public String painelPrecos(Model m, HttpSession s) { if(s.getAttribute("adminLogado")==null) return "redirect:/login"; m.addAttribute("materiais", materialRepository.findAll()); return "admin-precos"; }
    @PostMapping("/admin/atualizar") public String upd(@RequestParam Long id, @RequestParam Double novoPreco) { Material m = materialRepository.findById(id).get(); m.setPrecoPorKg(BigDecimal.valueOf(novoPreco)); materialRepository.save(m); return "redirect:/admin/precos"; }

    // DTO Auxiliar
    public static class PreVendaDTO {
        public Material material;
        public Double peso;
        public BigDecimal total;
        public PreVendaDTO(Material m, Double p, BigDecimal t) { this.material=m; this.peso=p; this.total=t; }
    }
}