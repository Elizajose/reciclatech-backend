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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
//import java.util.Arrays;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.time.ZoneId;

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
            // Mantemos apenas o essencial para a vitrine do site
            model.addAttribute("materiais", googleSheetsService.listarMateriais());
            model.addAttribute("topMateriais", googleSheetsService.buscarRankingMateriais());

            // Removemos a linha do 'totalReciclado' para economizar processamento
        } catch (Exception e) {
            model.addAttribute("materiais", new ArrayList<>());
            model.addAttribute("topMateriais", new ArrayList<>());
        }
        return "index";
    }

    // --- GESTÃO DE MATERIAIS ---
    @GetMapping("/admin/precos")
    public String painelPrecos(Model m, HttpSession s, HttpServletResponse response) {
        if(s.getAttribute("adminLogado") == null) return "redirect:/login";

        // BLOQUEIA O CACHE PARA EVITAR O BUG DE VOLTAR
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

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
    public String telaListaColetas(Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";

        // BLOQUEIA O CACHE PARA EVITAR O BUG DE VOLTAR
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        try {
            model.addAttribute("usuarios", googleSheetsService.buscarUsuariosComColetasPendentes());
        } catch (IOException e) {
            model.addAttribute("usuarios", new ArrayList<>());
        }
        return "admin-lista-coletas";
    }

    @GetMapping("/admin/atender/{idUsuario}")
    public String telaChecklist(@PathVariable String idUsuario, Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";

        // BLOQUEIA O CACHE PARA EVITAR O BUG DE VOLTAR
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        try {
            // 🌟 TRAVA DE CONCORRÊNCIA: Avisa a planilha que este usuário já está sendo atendido por você
            googleSheetsService.marcarComoEmAtendimento(idUsuario);

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

    @GetMapping("/admin/cancelar/{id}")
    public String cancelarColeta(@PathVariable String id, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";

        try {
            // Reaproveita o metodo que marca como concluída na planilha, limpando da tela
            googleSheetsService.marcarSolicitacaoComoConcluida(id);
        } catch (Exception e) {
            System.err.println("Erro ao cancelar coleta: " + e.getMessage());
        }

        return "redirect:/admin/coletas";
    }

    @PostMapping("/admin/revisar-coleta")
    public String revisarColeta(@RequestParam String idVendedor, @RequestParam Map<String, String> params, Model model) {
        try {
            Usuario vendedor = googleSheetsService.listarUsuarios().stream()
                    .filter(u -> u.getTelefone().equals(idVendedor) || u.getId().toString().equals(idVendedor))
                    .findFirst().orElseThrow();

            List<PreVendaDTO> itensRevisao = new ArrayList<>();
            BigDecimal totalEstimado = BigDecimal.ZERO;

            // 🌟 OTIMIZAÇÃO: Busca todos os materiais UMA SÓ VEZ!
            List<Material> todosMateriais = googleSheetsService.listarMateriais();

            for (String key : params.keySet()) {
                if (key.startsWith("qtd_") && !params.get(key).isEmpty()) {
                    try {
                        Long idMaterial = Long.parseLong(key.replace("qtd_", ""));
                        Double quantidade = Double.parseDouble(params.get(key).replace(",", ".")); // Previne erro de vírgula

                        if (quantidade > 0) {
                            // 🌟 Busca na memória, sem perturbar o Google Sheets
                            Material mat = todosMateriais.stream()
                                    .filter(m -> m.getId().equals(idMaterial))
                                    .findFirst().orElse(null);

                            if (mat != null) {
                                BigDecimal totalItem = mat.getPrecoPorKg().multiply(BigDecimal.valueOf(quantidade));
                                itensRevisao.add(new PreVendaDTO(mat, quantidade, totalItem));
                                totalEstimado = totalEstimado.add(totalItem);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao processar item na revisão: " + e.getMessage());
                    }
                }
            }
            model.addAttribute("vendedor", vendedor);
            model.addAttribute("itens", itensRevisao);
            model.addAttribute("totalEstimado", totalEstimado);
            return "admin-revisao";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/admin/coletas?erro=planilha";
        }
    }

    @PostMapping("/admin/confirmar-finalizacao")
    public String confirmarFinalizacao(@RequestParam String idVendedor,
                                       @RequestParam(required = false) String cpfFinal,
                                       @RequestParam(required = false) List<Long> idsMateriais,
                                       @RequestParam(required = false) List<Double> pesosFinais,
                                       @RequestParam(required = false) List<Double> precosFinais) {
        try {
            if (idsMateriais == null || idsMateriais.isEmpty()) {
                return "redirect:/admin/coletas?erro=sem_materiais";
            }

            List<Material> todosMateriais = googleSheetsService.listarMateriais();
            List<List<Object>> loteDeVendas = new ArrayList<>();

            for (int i = 0; i < idsMateriais.size(); i++) {
                Long idMat = idsMateriais.get(i);
                Material mat = todosMateriais.stream()
                        .filter(m -> m.getId().equals(idMat))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Material não encontrado: " + idMat));

                BigDecimal precoUn = BigDecimal.valueOf(precosFinais.get(i));
                BigDecimal total = precoUn.multiply(BigDecimal.valueOf(pesosFinais.get(i)));

                List<Object> row = Arrays.asList(
                        System.currentTimeMillis() + i,
                        mat.getNome(),
                        pesosFinais.get(i).toString().replace(".", ","),
                        "ENTREGA NO LOCAL",
                        precoUn.toString().replace(".", ","),
                        total.toString().replace(".", ","),
                        LocalDate.now(ZoneId.of("America/Recife")).toString(),
                        //LocalDate.now().toString(),
                        idVendedor,
                        "VENDIDO",
                        cpfFinal != null && !cpfFinal.trim().isEmpty() ? cpfFinal : "NÃO INFORMADO"
                );
                loteDeVendas.add(row);
            }

            // Salva as vendas
            googleSheetsService.registrarVendasEmLote(loteDeVendas);

            // 👇 A CORREÇÃO ENTRA AQUI! Atualiza o CPF no cadastro do cliente 👇
            googleSheetsService.atualizarCpfUsuario(idVendedor, cpfFinal);

            // Limpa a tela
            googleSheetsService.marcarSolicitacaoComoConcluida(idVendedor);

            return "redirect:/extrato/" + idVendedor;
        } catch (Exception e) {
            System.err.println("ERRO GRAVE NA FINALIZAÇÃO DA COLETA:");
            e.printStackTrace();
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

            // BUSCA AS VENDAS REAIS QUE ACABAMOS DE SALVAR NA PLANILHA!
            List<Oferta> vendasReais = googleSheetsService.buscarVendasPorUsuario(id);

            BigDecimal totalGeral = vendasReais.stream()
                    .map(Oferta::getPrecoEstimado)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<Material> mats = googleSheetsService.listarMateriais();
            model.addAttribute("mapaUnidades", mats.stream().collect(Collectors.toMap(Material::getNome, Material::getUnidade)));
            model.addAttribute("mapaPrecos", mats.stream().collect(Collectors.toMap(Material::getNome, Material::getPrecoPorKg)));

            model.addAttribute("vendedor", usuario);
            model.addAttribute("vendas", vendasReais); // Agora a lista NÃO está mais vazia!
            model.addAttribute("total", totalGeral);
            model.addAttribute("dataHoje", LocalDate.now(ZoneId.of("America/Recife")));
           // model.addAttribute("dataHoje", java.time.LocalDate.now());

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

    // Rota para abrir a lista de extratos do dia
    @GetMapping("/meus-extratos")
    public String listaExtratosDoDia(Model model, HttpSession session, HttpServletResponse response) {
        // 1. Verifica se o gestor está logado
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";

        // 2. Bloqueia o cache para evitar o bug do "Voltar"
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        try {
            // Buscamos quem vendeu hoje para listar na tela
            model.addAttribute("usuarios", googleSheetsService.buscarUsuariosComVendasHoje());
        } catch (IOException e) {
            model.addAttribute("usuarios", new ArrayList<>());
        }
        return "lista-extratos"; // Nome do seu novo arquivo HTML
    }

    // --- PESAGEM RÁPIDA (ATENDIMENTO AVULSO SEM TELEFONE) ---
    @PostMapping("/admin/pesagem-rapida")
    public String pesagemRapida(@RequestParam String nome, HttpSession session) {
        if(session.getAttribute("adminLogado") == null) return "redirect:/login";

        // Geramos o ID único do cliente avulso na hora
        Long idGerado = System.currentTimeMillis();

        Usuario novoAvulso = new Usuario();
        novoAvulso.setId(idGerado); // Define o ID
        novoAvulso.setNome(nome);
        // Colocamos o ID no lugar do telefone para o sistema não se perder, mas o cliente não vê isso!
        novoAvulso.setTelefone(idGerado.toString());
        novoAvulso.setEndereco("Atendimento Avulso");
        novoAvulso.setTipo(Usuario.TipoUsuario.CATADOR);

        try {
            // Salva na planilha como "DISPONIVEL"
            googleSheetsService.salvarSolicitacaoInicial(novoAvulso, novoAvulso.getEndereco());

            // Redireciona direto para a tela de pesar os materiais usando o ID
            return "redirect:/admin/atender/" + idGerado;
        } catch (IOException e) {
            System.err.println("Erro ao gerar pesagem rápida: " + e.getMessage());
            return "redirect:/admin/coletas?erro=pesagem_rapida";
        }
    }

    // --- ROTA DE ANALISES COM TRAVA DE SEGURANÇA ---
    @GetMapping("/admin/analises")
    public String mostrarAnalises(Model model, HttpSession session) {
        // 1. Verifica login geral
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";

        // 2. VERIFICAÇÃO DE SENHA DO GESTOR
        if (session.getAttribute("gestorAutorizado") == null) {
            return "redirect:/admin/analises/autenticar";
        }

        try {
            List<Oferta> historico = googleSheetsService.getHistoricoCompleto();
            List<Usuario> usuariosCadastrados = googleSheetsService.listarUsuarios();
            List<Material> materiaisCadastrados = googleSheetsService.listarMateriais();

            // Agrupamento Financeiro
            Map<String, Double> financeiroDados = historico.stream()
                    .filter(o -> o.getMaterial() != null)
                    .collect(Collectors.groupingBy(Oferta::getMaterial,
                            Collectors.summingDouble(o -> o.getPrecoEstimado() != null ? o.getPrecoEstimado().doubleValue() : 0.0)));

            // Volume por Data
            Map<String, Double> volumePorDia = historico.stream()
                    .filter(o -> o.getData() != null)
                    .collect(Collectors.groupingBy(Oferta::getData,
                            TreeMap::new,
                            Collectors.summingDouble(o -> o.getPeso() != null ? o.getPeso() : 0.0)));

            Map<String, String> unidadesMap = materiaisCadastrados.stream()
                    .collect(Collectors.toMap(Material::getNome, Material::getUnidade, (v1, v2) -> v1));

            Double totalKg = 0.0;
            Double totalUn = 0.0;
            for (Oferta o : historico) {
                String unidade = unidadesMap.getOrDefault(o.getMaterial(), "KG");
                Double valor = (o.getPeso() != null) ? o.getPeso() : 0.0;
                if ("UN".equalsIgnoreCase(unidade)) totalUn += valor; else totalKg += valor;
            }

            // Gráficos de Rosca
            Map<String, Double> dadosRoscaKg = historico.stream()
                    .filter(o -> !"UN".equalsIgnoreCase(unidadesMap.getOrDefault(o.getMaterial(), "KG")))
                    .collect(Collectors.groupingBy(Oferta::getMaterial, Collectors.summingDouble(o -> o.getPeso() != null ? o.getPeso() : 0.0)));

            Map<String, Double> dadosRoscaUn = historico.stream()
                    .filter(o -> "UN".equalsIgnoreCase(unidadesMap.getOrDefault(o.getMaterial(), "KG")))
                    .collect(Collectors.groupingBy(Oferta::getMaterial, Collectors.summingDouble(o -> o.getPeso() != null ? o.getPeso() : 0.0)));

            // --- LÓGICA DE RANKING (INSIGHTS) ---
            List<Map.Entry<String, Double>> rankingMaisColetados = dadosRoscaKg.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(3).collect(Collectors.toList());

            List<Map.Entry<String, Double>> baixaColeta = dadosRoscaKg.entrySet().stream()
                    .filter(e -> e.getValue() < 50.0).collect(Collectors.toList());

            long totalAtendimentosReais = (usuariosCadastrados != null) ? usuariosCadastrados.size() : 0;
            Double totalPagoAosClientes = financeiroDados.values().stream().mapToDouble(Double::doubleValue).sum();
            Double custoMedioPorAtendimento = (totalAtendimentosReais > 0) ? totalPagoAosClientes / totalAtendimentosReais : 0.0;

            model.addAttribute("volumeDados", volumePorDia);
            model.addAttribute("dadosRoscaKg", dadosRoscaKg);
            model.addAttribute("dadosRoscaUn", dadosRoscaUn);
            model.addAttribute("rankingMaisColetados", rankingMaisColetados);
            model.addAttribute("baixaColeta", baixaColeta);
            model.addAttribute("totalAtendimentos", totalAtendimentosReais);
            model.addAttribute("totalPago", totalPagoAosClientes);
            model.addAttribute("totalKg", totalKg);
            model.addAttribute("totalUn", totalUn);
            model.addAttribute("custoMedio", custoMedioPorAtendimento);

        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/admin/coletas?erro=analises";
        }
        return "analises";
    }

    // --- ROTAS DA SENHA DE GESTOR ---
    @GetMapping("/admin/analises/autenticar")
    public String telaSenhaGestor() { return "autenticar-analises"; }

    @PostMapping("/admin/analises/autenticar")
    public String processarSenhaGestor(@RequestParam String senhaGestor, HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        if ("1234".equals(senhaGestor)) { // ALTERE A SENHA AQUI
            session.setAttribute("gestorAutorizado", true);
            return "redirect:/admin/analises";
        }
        ra.addFlashAttribute("erro", "Senha incorreta!");
        return "redirect:/admin/analises/autenticar";
    }
}