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
    @Autowired private FuncionarioRepository funcionarioRepository;
    @Autowired private HttpSession session;


    // =========================================================================
    // 1. HELPERS E CONTEXTO DA SESSÃO
    // =========================================================================

    private Long getArmazemIdLogado() {
        Object id = session.getAttribute("idPlanilhaAtiva");
        if (id != null) return Long.parseLong(id.toString());
        return 1L; // Fallback segurança
    }

    private Armazem getArmazemLogado() {
        return armazemRepository.findById(getArmazemIdLogado()).orElse(null);
    }

    private Optional<Usuario> buscarUsuarioPorIdOuTelefone(String idOuTelefone) {
        if (idOuTelefone == null || idOuTelefone.trim().isEmpty()) return Optional.empty();

        try {
            Long id = Long.parseLong(idOuTelefone);
            Optional<Usuario> u = usuarioRepository.findById(id);
            if (u.isPresent() && u.get().getArmazem().getId().equals(getArmazemIdLogado())) {
                return u;
            }
        } catch (NumberFormatException e) {
            // Não é ID numérico, busca pelo telefone
        }

        return usuarioRepository.findByTelefoneAndArmazemId(idOuTelefone, getArmazemIdLogado());
    }


    // =========================================================================
    // 2. AUTENTICAÇÃO, SaaS E COTAÇÕES PÚBLICAS
    // =========================================================================

    public Map<String, String> autenticarSaaS(String login, String senhaPura) {
        String senhaCriptografada = criptografarSenha(senhaPura);

        // 1. TENTA LOGAR COMO DONO (GESTOR)
        try {
            // ADICIONADO 'telefone' NA QUERY ABAIXO:
            String sqlGestor = "SELECT id, nome, cnpj, endereco, telefone, senha, perfil, status, plano, data_cadastro FROM armazem WHERE login = ? AND senha = ?";
            return jdbcTemplate.queryForObject(sqlGestor, new Object[]{login, senhaCriptografada}, (rs, rowNum) -> {
                Map<String, String> dados = new HashMap<>();
                dados.put("idPlanilha", rs.getString("id"));
                dados.put("perfil", rs.getString("perfil"));
                dados.put("nomeArmazem", rs.getString("nome"));
                dados.put("cnpj", rs.getString("cnpj"));
                dados.put("endereco", rs.getString("endereco"));
                dados.put("telefone", rs.getString("telefone")); // <--- ADICIONADO AQUI
                dados.put("senhaLogin", rs.getString("senha"));
                dados.put("status", rs.getString("status"));
                dados.put("plano", rs.getString("plano"));
                dados.put("trialVencido", "false");
                return dados;
            });
        } catch (Exception e) {
            // Não é o dono. Segue para o funcionário.
        }

        // 2. TENTA LOGAR COMO FUNCIONÁRIO (OPERADOR)
        try {
            // ADICIONADO 'a.telefone' NA QUERY ABAIXO:
            String sqlOperador = "SELECT f.id as func_id, f.nome as func_nome, f.perfil, f.armazem_id, " +
                    "a.nome as nome_armazem, a.cnpj, a.endereco, a.telefone, a.status, a.plano " +
                    "FROM funcionario f " +
                    "JOIN armazem a ON f.armazem_id = a.id " +
                    "WHERE f.login = ? AND f.senha = ? AND f.ativo = true";

            return jdbcTemplate.queryForObject(sqlOperador, new Object[]{login, senhaCriptografada}, (rs, rowNum) -> {
                Map<String, String> dados = new HashMap<>();
                dados.put("idPlanilha", rs.getString("armazem_id"));
                dados.put("perfil", rs.getString("perfil"));
                dados.put("nomeArmazem", rs.getString("nome_armazem"));
                dados.put("cnpj", rs.getString("cnpj"));
                dados.put("endereco", rs.getString("endereco"));
                dados.put("telefone", rs.getString("telefone"));
                dados.put("status", rs.getString("status"));
                dados.put("plano", rs.getString("plano"));
                dados.put("nomeFuncionarioLogado", rs.getString("func_nome"));
                return dados;
            });
        } catch (Exception e) {
            return null;
        }
    }

    public boolean cadastrarParceiroSaaS(String nomeArmazem, String nomeProprietario, String documentoCnpjCpf, String telefone, String endereco, String login, String senha, String planoEscolhido) {
        try {
            String sqlCheck = "SELECT COUNT(*) FROM armazem WHERE login = ? OR cnpj = ?";
            Integer count = jdbcTemplate.queryForObject(sqlCheck, Integer.class, login, documentoCnpjCpf);
            if (count != null && count > 0) return false;

            java.sql.Date dataHoje = java.sql.Date.valueOf(LocalDate.now(ZoneId.of("America/Recife")));
            String senhaCriptografada = criptografarSenha(senha);

            String sqlInsert = "INSERT INTO armazem (nome, cnpj, telefone, endereco, login, senha, perfil, status, plano, data_cadastro) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'GESTOR', 'TRIAL', ?, ?)";

            jdbcTemplate.update(sqlInsert, nomeArmazem, documentoCnpjCpf, telefone, endereco, login, senhaCriptografada, planoEscolhido, dataHoje);
            return true;

        } catch (Exception e) {
            System.err.println("Erro ao cadastrar parceiro: " + e.getMessage());
            throw new RuntimeException("Erro no banco de dados ao salvar o cadastro.");
        }
    }

    public List<Map<String, Object>> obterCotacoesPublicas() throws IOException {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Recife"));

        List<Armazem> armazens = armazemRepository.findAll().stream()
                .filter(a -> {
                    if ("ATIVO".equals(a.getStatus())) return true;
                    if ("TRIAL".equals(a.getStatus()) && a.getDataCadastro() != null) {
                        long diasUso = java.time.temporal.ChronoUnit.DAYS.between(a.getDataCadastro(), hoje);
                        return diasUso <= 7;
                    }
                    return false;
                })
                .collect(Collectors.toList());

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


    // =========================================================================
    // 3. GESTÃO DE MATERIAIS E PREÇOS
    // =========================================================================

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


    // =========================================================================
    // 4. GESTÃO DE USUÁRIOS E SOLICITAÇÕES DE COLETA
    // =========================================================================

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
        buscarUsuarioPorIdOuTelefone(idVendedor).ifPresent(u -> {
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
        oferta.setData(LocalDate.now(ZoneId.of("America/Recife")).toString());
        ofertaRepository.save(oferta);
    }

    public List<Usuario> buscarUsuariosComColetasPendentes() throws IOException {
        String hoje = LocalDate.now(ZoneId.of("America/Recife")).toString();
        List<Oferta> pendentes = ofertaRepository.findAllByArmazemIdOrderByDataCriacaoDesc(getArmazemIdLogado()).stream()
                .filter(o -> (o.getStatus() == Oferta.StatusOferta.DISPONIVEL || o.getStatus() == Oferta.StatusOferta.EM_ATENDIMENTO) && o.getData().equals(hoje))
                .collect(Collectors.toList());

        Set<Usuario> usuarios = new HashSet<>();
        for (Oferta o : pendentes) {
            if(o.getUsuario() != null) {
                Usuario u = o.getUsuario();
                u.setStatusPlanilha(o.getStatus().toString());
                usuarios.add(u);
            }
        }
        return new ArrayList<>(usuarios);
    }

    public List<Usuario> buscarUsuariosComVendasHoje() throws IOException {
        String hoje = LocalDate.now(ZoneId.of("America/Recife")).toString();
        List<Oferta> vendasHoje = ofertaRepository.findAllByArmazemIdOrderByDataCriacaoDesc(getArmazemIdLogado()).stream()
                .filter(o -> o.getStatus() == Oferta.StatusOferta.VENDIDO && o.getData().equals(hoje))
                .collect(Collectors.toList());

        Set<Usuario> usuarios = new HashSet<>();
        for (Oferta o : vendasHoje) {
            if(o.getUsuario() != null) {
                usuarios.add(o.getUsuario());
            }
        }
        return new ArrayList<>(usuarios);
    }


    // =========================================================================
    // 5. VENDAS, OFERTAS E CONTROLE DE ESTOQUE
    // =========================================================================

    @Transactional
    public void registrarVendasEmLote(List<List<Object>> linhasParaSalvar) throws IOException {
        for (List<Object> row : linhasParaSalvar) {
            Oferta o = new Oferta();
            o.setArmazem(getArmazemLogado());
            o.setMaterial(row.get(1).toString());

            Object pesoObj = row.get(2);
            if(pesoObj instanceof Double) {
                o.setPeso((Double) pesoObj);
            } else {
                o.setPeso(Double.parseDouble(pesoObj.toString().replace(",", ".")));
            }

            Object precoObj = row.get(5);
            if(precoObj instanceof BigDecimal) {
                o.setPrecoEstimado((BigDecimal) precoObj);
            } else {
                o.setPrecoEstimado(new BigDecimal(precoObj.toString().replace(",", ".")));
            }

            o.setData(row.get(6).toString());

            String docUsuario = row.get(7).toString();
            buscarUsuarioPorIdOuTelefone(docUsuario).ifPresent(o::setUsuario);

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
        buscarUsuarioPorIdOuTelefone(idVendedor).ifPresent(u -> {
            List<Oferta> ofertas = ofertaRepository.findAllByUsuarioIdAndStatusIn(u.getId(), Arrays.asList(de));
            for(Oferta o : ofertas) {
                o.setStatus(para);
                ofertaRepository.save(o);
            }
        });
    }

    public List<Oferta> buscarVendasPorUsuario(String idOuTelefone) throws IOException {
        Optional<Usuario> u = buscarUsuarioPorIdOuTelefone(idOuTelefone);
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


    // =========================================================================
    // 6. FINANCEIRO, CAIXA E DESPESAS
    // =========================================================================

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


    // =========================================================================
    // 7. PREÇOS VIP (FORNECEDORES ESPECIAIS)
    // =========================================================================

    public Map<String, BigDecimal> buscarPrecosEspeciais(String cpf) throws IOException {
        if (cpf == null || cpf.trim().isEmpty()) return new HashMap<>();
        String cpfLimpo = cpf.replaceAll("\\D", "");

        return precoVipRepository.findAllByArmazemId(getArmazemIdLogado()).stream()
                .filter(p -> p.getCpfCatador() != null && p.getCpfCatador().equals(cpfLimpo))
                .collect(Collectors.toMap(PrecoVip::getMaterialNome, PrecoVip::getPrecoEspecial));
    }

    public List<Map<String, String>> listarFornecedoresVip() throws IOException {
        return precoVipRepository.findAllByArmazemId(getArmazemIdLogado()).stream().map(p -> {
            Map<String, String> map = new HashMap<>();
            map.put("cpf", p.getCpfCatador() != null ? p.getCpfCatador() : "SEM CPF (Antigo)");
            map.put("material", p.getMaterialNome());
            map.put("preco", String.format("%.2f", p.getPrecoEspecial()).replace(".", ","));
            return map;
        }).collect(Collectors.toList());
    }

    public void salvarFornecedorVip(String cpf, String material, BigDecimal preco) throws IOException {
        PrecoVip p = new PrecoVip();
        p.setArmazem(getArmazemLogado());
        p.setCpfCatador(cpf.replaceAll("\\D", ""));
        p.setMaterialNome(material);
        p.setPrecoEspecial(preco);
        precoVipRepository.save(p);
    }

    public void deletarFornecedorVip(String cpf, String material) throws IOException {
        String cpfLimpo = cpf.replaceAll("\\D", "");
        precoVipRepository.findByArmazemIdAndCpfCatadorAndMaterialNome(getArmazemIdLogado(), cpfLimpo, material)
                .ifPresent(precoVipRepository::delete);
    }


    // =========================================================================
    // 8. PAINEL MASTER (ADMINISTRATIVO SaaS)
    // =========================================================================

    public List<Map<String, Object>> listarTodosArmazensSaaS() {
        try {
            String sql = "SELECT * FROM armazem ORDER BY id DESC";
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            System.err.println("Erro ao listar armazéns: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void atualizarStatusArmazem(Long id, String novoStatus) {
        try {
            String sql = "UPDATE armazem SET status = ? WHERE id = ?";
            jdbcTemplate.update(sql, novoStatus, id);
        } catch (Exception e) {
            System.err.println("Erro ao atualizar status: " + e.getMessage());
        }
    }


    // =========================================================================
    // 9. GESTÃO DE FUNCIONÁRIOS, SEGURANÇA E PERFIL
    // =========================================================================

    public void atualizarSenhasArmazem(Long idArmazem, String novaSenhaLogin) {
        try {
            if (novaSenhaLogin != null && !novaSenhaLogin.trim().isEmpty()) {
                String sql = "UPDATE armazem SET senha = ? WHERE id = ?";
                jdbcTemplate.update(sql, criptografarSenha(novaSenhaLogin), idArmazem);
            }
        } catch (Exception e) {
            System.err.println("Erro ao atualizar senha do armazém: " + e.getMessage());
            throw new RuntimeException("Falha ao atualizar credenciais.");
        }
    }

    public String criptografarSenha(String senha) {
        if (senha == null) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(senha.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criptografar senha", e);
        }
    }

    public List<Funcionario> listarFuncionarios() {
        return funcionarioRepository.findAllByArmazemId(getArmazemIdLogado());
    }

    public void salvarFuncionario(String nome, String login, String senhaPura) {
        Funcionario f = new Funcionario();
        f.setArmazem(getArmazemLogado());
        f.setNome(nome);
        f.setLogin(login);
        f.setSenha(criptografarSenha(senhaPura));
        f.setAtivo(true);
        f.setPerfil("OPERADOR");
        funcionarioRepository.save(f);
    }

    public void alterarStatusFuncionario(Long id, boolean ativo) {
        funcionarioRepository.findById(id).ifPresent(f -> {
            if(f.getArmazem().getId().equals(getArmazemIdLogado())) {
                f.setAtivo(ativo);
                funcionarioRepository.save(f);
            }
        });
    }
}