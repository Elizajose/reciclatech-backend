package com.reciclatech.backend.service;

import com.reciclatech.backend.model.*;
import com.reciclatech.backend.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DatabaseService {
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired private ArmazemRepository armazemRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private OfertaRepository ofertaRepository;
    @Autowired private DespesaRepository despesaRepository;
    @Autowired private CaixaDiarioRepository caixaDiarioRepository;
    @Autowired private PrecoVipRepository precoVipRepository;
    @Autowired private HttpSession session;

    private Long getArmazemIdLogado() {
        Object id = session.getAttribute("idPlanilhaAtiva");
        if (id != null) return Long.parseLong(id.toString());
        return 1L; // Fallback segurança
    }

    private Armazem getArmazemLogado() {
        return armazemRepository.findById(getArmazemIdLogado()).orElse(null);
    }

    // ==========================================
    // AUTENTICAÇÃO E SAAS (ATUALIZADO COM PLANO E DATA)
    // ==========================================
    public Map<String, String> autenticarSaaS(String login, String senha) {
        try {
            // Nota: Puxa o status, o plano e a data de cadastro para verificar o Trial
            String sql = "SELECT id, nome, cnpj, senha_financeira, perfil, status, plano, data_cadastro FROM armazem WHERE login = ? AND senha = ?";
            return jdbcTemplate.queryForObject(sql, new Object[]{login, senha}, (rs, rowNum) -> {
                Map<String, String> dados = new HashMap<>();
                dados.put("idPlanilha", rs.getString("id")); // Usando o ID do banco como referência
                dados.put("perfil", rs.getString("perfil"));
                dados.put("nomeArmazem", rs.getString("nome"));
                dados.put("cnpj", rs.getString("cnpj"));
                dados.put("senhaFinanceira", rs.getString("senha_financeira") != null ? rs.getString("senha_financeira") : "admin123");
                dados.put("status", rs.getString("status"));
                dados.put("plano", rs.getString("plano"));
                dados.put("data_cadastro", rs.getString("data_cadastro"));
                return dados;
            });
        } catch (Exception e) {
            return null; // Login ou senha inválidos
        }
    }

    public List<Map<String, Object>> obterCotacoesPublicas() throws IOException {
        List<Armazem> armazens = armazemRepository.findAllByStatus("ATIVO");
        List<Map<String, Object>> cotacoes = new ArrayList<>();
        for (Armazem a : armazens) {
            Map<String, Object> dados = new HashMap<>();
            dados.put("idPlanilha", a.getId().toString());
            dados.put("nome", a.getNome());
            dados.put("htmlId", a.getNome().replaceAll("[^a-zA-Z0-9]", "").toLowerCase());
            dados.put("telefone", a.getTelefone());
            dados.put("materiais", materialRepository.findAllByArmazemIdOrderByNomeAsc(a.getId()));
            cotacoes.add(dados);
        }
        return cotacoes;
    }

    // --- MATERIAIS ---
    public List<Material> listarMateriais() throws IOException {
        return materialRepository.findAllByArmazemIdOrderByNomeAsc(getArmazemIdLogado());
    }

    public List<Material> listarMateriais(String sheetId) throws IOException {
        return materialRepository.findAllByArmazemIdOrderByNomeAsc(Long.parseLong(sheetId));
    }

    public void salvarMaterial(Material material) throws IOException {
        material.setArmazem(getArmazemLogado());
        materialRepository.save(material);
    }

    public void atualizarPrecoMaterial(Long id, BigDecimal novoPreco) throws IOException {
        materialRepository.findById(id).ifPresent(m -> {
            if (m.getArmazem().getId().equals(getArmazemIdLogado())) {
                m.setPrecoPorKg(novoPreco);
                materialRepository.save(m);
            }
        });
    }

    public void deletarMaterial(Long id) throws IOException {
        materialRepository.findById(id).ifPresent(m -> {
            if (m.getArmazem().getId().equals(getArmazemIdLogado())) materialRepository.delete(m);
        });
    }

    // --- USUÁRIOS E SOLICITAÇÕES ---
    public List<Usuario> listarUsuarios() throws IOException {
        return usuarioRepository.findAllByArmazemId(getArmazemIdLogado());
    }

    public void salvarUsuario(Usuario usuario) throws IOException {
        salvarUsuario(usuario, getArmazemIdLogado().toString());
    }

    public void salvarUsuario(Usuario usuario, String planilhaAlvo) throws IOException {
        usuario.setArmazem(armazemRepository.findById(Long.parseLong(planilhaAlvo)).orElse(getArmazemLogado()));
        usuario.prePersist();
        usuarioRepository.save(usuario);
    }

    public void atualizarCpfUsuario(String idVendedor, String cpfFinal) throws IOException {
        if (cpfFinal == null || cpfFinal.trim().isEmpty() || cpfFinal.equals("NÃO INFORMADO")) return;
        usuarioRepository.findByTelefoneAndArmazemId(idVendedor, getArmazemIdLogado()).ifPresent(u -> {
            u.setCpf(cpfFinal);
            usuarioRepository.save(u);
        });
    }

    public void salvarSolicitacaoInicial(Usuario usuario, String endereco) throws IOException {
        salvarSolicitacaoInicial(usuario, endereco, getArmazemIdLogado().toString());
    }

    public void salvarSolicitacaoInicial(Usuario usuario, String endereco, String idArmazemDestino) throws IOException {
        Long armazemId = idArmazemDestino != null ? Long.parseLong(idArmazemDestino) : getArmazemIdLogado();
        Armazem destino = armazemRepository.findById(armazemId).orElse(getArmazemLogado());

        usuario.setArmazem(destino);
        usuario.setEndereco(endereco);
        usuario.prePersist();
        usuario = usuarioRepository.save(usuario);

        Oferta oferta = new Oferta();
        oferta.setArmazem(destino);
        oferta.setUsuario(usuario);
        oferta.setMaterial("Solicitação de Coleta");
        oferta.setPeso(0.0);
        oferta.setPrecoEstimado(BigDecimal.ZERO);
        oferta.setStatus(Oferta.StatusOferta.DISPONIVEL);
        oferta.setData(LocalDate.now().toString());
        ofertaRepository.save(oferta);
    }

    public List<Usuario> buscarUsuariosComColetasPendentes() throws IOException {
        List<Oferta> pendentes = ofertaRepository.findAllByArmazemIdOrderByDataCriacaoDesc(getArmazemIdLogado()).stream()
                .filter(o -> (o.getStatus() == Oferta.StatusOferta.DISPONIVEL || o.getStatus() == Oferta.StatusOferta.EM_ATENDIMENTO) && o.getData().equals(LocalDate.now().toString()))
                .collect(Collectors.toList());

        Set<Usuario> usuarios = new HashSet<>();
        for (Oferta o : pendentes) {
            Usuario u = o.getUsuario();
            u.setStatusPlanilha(o.getStatus().toString());
            usuarios.add(u);
        }
        return new ArrayList<>(usuarios);
    }

    public List<Usuario> buscarUsuariosComVendasHoje() throws IOException {
        String hoje = LocalDate.now().toString();
        List<Oferta> vendasHoje = ofertaRepository.findAllByArmazemIdOrderByDataCriacaoDesc(getArmazemIdLogado()).stream()
                .filter(o -> o.getStatus() == Oferta.StatusOferta.VENDIDO && o.getData().equals(hoje))
                .collect(Collectors.toList());

        Set<Usuario> usuarios = new HashSet<>();
        for (Oferta o : vendasHoje) {
            usuarios.add(o.getUsuario());
        }
        return new ArrayList<>(usuarios);
    }

    // --- VENDAS, OFERTAS E ESTOQUE ---
    @Transactional
    public void registrarVendasEmLote(List<List<Object>> linhasParaSalvar) throws IOException {
        for (List<Object> row : linhasParaSalvar) {
            Oferta o = new Oferta();
            o.setArmazem(getArmazemLogado());
            o.setMaterial(row.get(1).toString());
            o.setPeso(Double.parseDouble(row.get(2).toString().replace(",", ".")));
            o.setPrecoEstimado(new BigDecimal(row.get(5).toString().replace(",", ".")));
            o.setData(row.get(6).toString());

            String docUsuario = row.get(7).toString();
            usuarioRepository.findByTelefoneAndArmazemId(docUsuario, getArmazemIdLogado()).ifPresent(o::setUsuario);

            o.setStatus(Oferta.StatusOferta.valueOf(row.get(8).toString()));
            ofertaRepository.save(o);
        }
    }

    public void marcarComoEmAtendimento(String idVendedor) throws IOException {
        mudarStatusOferta(idVendedor, Oferta.StatusOferta.DISPONIVEL, Oferta.StatusOferta.EM_ATENDIMENTO);
    }

    public void marcarSolicitacaoComoConcluida(String idVendedor) throws IOException {
        mudarStatusOferta(idVendedor, Oferta.StatusOferta.EM_ATENDIMENTO, Oferta.StatusOferta.FINALIZADO);
        mudarStatusOferta(idVendedor, Oferta.StatusOferta.DISPONIVEL, Oferta.StatusOferta.FINALIZADO);
    }

    private void mudarStatusOferta(String idVendedor, Oferta.StatusOferta de, Oferta.StatusOferta para) {
        usuarioRepository.findByTelefoneAndArmazemId(idVendedor, getArmazemIdLogado()).ifPresent(u -> {
            List<Oferta> ofertas = ofertaRepository.findAllByUsuarioIdAndStatusIn(u.getId(), Arrays.asList(de));
            for(Oferta o : ofertas) {
                o.setStatus(para);
                ofertaRepository.save(o);
            }
        });
    }

    public List<Oferta> buscarVendasPorUsuario(String telefone) throws IOException {
        Optional<Usuario> u = usuarioRepository.findByTelefoneAndArmazemId(telefone, getArmazemIdLogado());
        if (u.isPresent()) {
            return ofertaRepository.findAllByUsuarioIdAndStatusIn(u.get().getId(), Arrays.asList(Oferta.StatusOferta.VENDIDO, Oferta.StatusOferta.SAIDA_INDUSTRIA));
        }
        return new ArrayList<>();
    }

    public List<Oferta> getHistoricoCompleto() throws IOException {
        return ofertaRepository.findAllByArmazemIdOrderByDataCriacaoDesc(getArmazemIdLogado());
    }

    public void salvarAjusteEstoque(String material, Double peso, String tipoAjuste, String motivo) throws IOException {
        Oferta o = new Oferta();
        o.setArmazem(getArmazemLogado());
        o.setMaterial(material);
        o.setPeso(peso);
        o.setEndereco("AJUSTE MANUAL: " + motivo);
        o.setPrecoEstimado(BigDecimal.ZERO);
        o.setData(LocalDate.now(ZoneId.of("America/Recife")).toString());
        o.setStatus(Oferta.StatusOferta.valueOf(tipoAjuste));
        ofertaRepository.save(o);
    }

    // --- FINANCEIRO E DESPESAS ---
    public void salvarDespesa(String descricao, BigDecimal valor) throws IOException {
        Despesa d = new Despesa();
        d.setArmazem(getArmazemLogado());
        d.setDescricao(descricao);
        d.setValor(valor);
        d.setDataDespesa(LocalDate.now(ZoneId.of("America/Recife")));
        despesaRepository.save(d);
    }

    public List<Map<String, String>> listarDespesas() throws IOException {
        return despesaRepository.findAllByArmazemIdOrderByDataDespesaDesc(getArmazemIdLogado()).stream().map(d -> {
            Map<String, String> map = new HashMap<>();
            map.put("id", d.getId().toString());
            map.put("descricao", d.getDescricao());
            map.put("valor", String.format("%.2f", d.getValor()).replace(".", ","));
            map.put("data", d.getDataDespesa().toString());
            return map;
        }).collect(Collectors.toList());
    }

    public Double calcularDespesasDoDia(String dataHoje) throws IOException {
        LocalDate data = LocalDate.parse(dataHoje);
        return despesaRepository.findAllByArmazemIdAndDataDespesa(getArmazemIdLogado(), data).stream()
                .mapToDouble(d -> d.getValor().doubleValue()).sum();
    }

    public void deletarDespesa(String id) throws IOException {
        despesaRepository.deleteById(Long.parseLong(id));
    }

    public void fecharCaixaDoDia(String data, BigDecimal entradas, BigDecimal saidas, BigDecimal despesas, BigDecimal lucro, Double pesoComprado) throws IOException {
        LocalDate localDate = LocalDate.parse(data);
        CaixaDiario caixa = caixaDiarioRepository.findByArmazemIdAndDataFechamento(getArmazemIdLogado(), localDate).orElse(new CaixaDiario());

        caixa.setArmazem(getArmazemLogado());
        caixa.setDataFechamento(localDate);
        caixa.setEntradas(entradas);
        caixa.setSaidas(saidas);
        caixa.setDespesas(despesas);
        caixa.setLucroLiquido(lucro);
        caixa.setPesoComprado(pesoComprado);
        caixa.setStatus("FECHADO");

        caixaDiarioRepository.save(caixa);
    }

    public List<Map<String, String>> listarFechamentosCaixa() throws IOException {
        return caixaDiarioRepository.findAllByArmazemIdOrderByDataFechamentoDesc(getArmazemIdLogado()).stream().map(c -> {
            Map<String, String> map = new HashMap<>();
            map.put("data", c.getDataFechamento().toString());
            map.put("entradas", String.format("%.2f", c.getEntradas()).replace(".", ","));
            map.put("saidas", String.format("%.2f", c.getSaidas()).replace(".", ","));
            map.put("despesas", String.format("%.2f", c.getDespesas()).replace(".", ","));
            map.put("lucro", String.format("%.2f", c.getLucroLiquido()).replace(".", ","));
            map.put("peso", String.format("%.2f", c.getPesoComprado()).replace(".", ","));
            return map;
        }).collect(Collectors.toList());
    }

    // --- PREÇOS VIP ---
    public Map<String, BigDecimal> buscarPrecosEspeciais(String telefone) throws IOException {
        return precoVipRepository.findAllByArmazemId(getArmazemIdLogado()).stream()
                .filter(p -> p.getTelefoneCatador().equals(telefone))
                .collect(Collectors.toMap(PrecoVip::getMaterialNome, PrecoVip::getPrecoEspecial));
    }

    public List<Map<String, String>> listarFornecedoresVip() throws IOException {
        return precoVipRepository.findAllByArmazemId(getArmazemIdLogado()).stream().map(p -> {
            Map<String, String> map = new HashMap<>();
            map.put("telefone", p.getTelefoneCatador());
            map.put("material", p.getMaterialNome());
            map.put("preco", String.format("%.2f", p.getPrecoEspecial()).replace(".", ","));
            return map;
        }).collect(Collectors.toList());
    }

    public void salvarFornecedorVip(String telefone, String material, BigDecimal preco) throws IOException {
        PrecoVip p = new PrecoVip();
        p.setArmazem(getArmazemLogado());
        p.setTelefoneCatador(telefone.replaceAll("\\D", ""));
        p.setMaterialNome(material);
        p.setPrecoEspecial(preco);
        precoVipRepository.save(p);
    }

    public void deletarFornecedorVip(String telefone, String material) throws IOException {
        precoVipRepository.findByArmazemIdAndTelefoneCatadorAndMaterialNome(getArmazemIdLogado(), telefone, material)
                .ifPresent(precoVipRepository::delete);
    }

    // ==========================================
    // NOVA FUNÇÃO: CADASTRO DE PARCEIRO SAAS (ATUALIZADO COM PLANO E DATA)
    // ==========================================
    public boolean cadastrarParceiroSaaS(String nomeArmazem, String nomeProprietario, String cnpj, String telefone, String endereco, String login, String senha, String planoEscolhido) {
        try {
            // 1. Verifica se o login já existe na tabela 'armazem' para evitar duplicidade
            String sqlCheck = "SELECT COUNT(*) FROM armazem WHERE login = ?";
            Integer count = jdbcTemplate.queryForObject(sqlCheck, Integer.class, login);

            if (count != null && count > 0) {
                return false; // Retorna falso indicando que o usuário já existe
            }

            // CORREÇÃO: Define a data de hoje como java.sql.Date para o Postgres aceitar perfeitamente
            java.sql.Date dataHoje = java.sql.Date.valueOf(java.time.LocalDate.now(java.time.ZoneId.of("America/Recife")));

            // 2. Insere o novo dono de armazém na tabela 'armazem'
            String sqlInsert = "INSERT INTO armazem (nome, cnpj, telefone, login, senha, perfil, senha_financeira, status, plano, data_cadastro) " +
                    "VALUES (?, ?, ?, ?, ?, 'GESTOR', 'admin123', 'TRIAL', ?, ?)";

            jdbcTemplate.update(sqlInsert, nomeArmazem, cnpj, telefone, login, senha, planoEscolhido, dataHoje);

            return true; // Cadastro realizado com sucesso!

        } catch (Exception e) {
            System.err.println("Erro ao cadastrar parceiro: " + e.getMessage());
            throw new RuntimeException("Erro no banco de dados ao salvar o cadastro.");
        }
    }

    // ==========================================
    // ROTAS DO PAINEL MASTER (SaaS)
    // ==========================================

    // 1. Busca todos os galpões cadastrados
    public List<Map<String, Object>> listarTodosArmazensSaaS() {
        try {
            String sql = "SELECT id, nome, cnpj, telefone, login, status FROM armazem ORDER BY id DESC";
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            System.err.println("Erro ao listar armazéns: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // 2. Bloqueia ou Desbloqueia um galpão
    public void atualizarStatusArmazem(Long id, String novoStatus) {
        try {
            String sql = "UPDATE armazem SET status = ? WHERE id = ?";
            jdbcTemplate.update(sql, novoStatus, id);
        } catch (Exception e) {
            System.err.println("Erro ao atualizar status: " + e.getMessage());
        }
    }

    // ==========================================
    // ATUALIZAR SENHAS DO ARMAZÉM (GESTOR)
    // ==========================================
    public void atualizarSenhasArmazem(Long idArmazem, String novaSenhaLogin, String novaSenhaFinanceira) {
        try {
            if (novaSenhaLogin != null && !novaSenhaLogin.trim().isEmpty() &&
                    novaSenhaFinanceira != null && !novaSenhaFinanceira.trim().isEmpty()) {
                String sql = "UPDATE armazem SET senha = ?, senha_financeira = ? WHERE id = ?";
                jdbcTemplate.update(sql, novaSenhaLogin, novaSenhaFinanceira, idArmazem);
            } else if (novaSenhaLogin != null && !novaSenhaLogin.trim().isEmpty()) {
                String sql = "UPDATE armazem SET senha = ? WHERE id = ?";
                jdbcTemplate.update(sql, novaSenhaLogin, idArmazem);
            } else if (novaSenhaFinanceira != null && !novaSenhaFinanceira.trim().isEmpty()) {
                String sql = "UPDATE armazem SET senha_financeira = ? WHERE id = ?";
                jdbcTemplate.update(sql, novaSenhaFinanceira, idArmazem);
            }
        } catch (Exception e) {
            System.err.println("Erro ao atualizar senhas do armazém: " + e.getMessage());
            throw new RuntimeException("Falha ao atualizar credenciais.");
        }
    }
}