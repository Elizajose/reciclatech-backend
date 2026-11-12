package com.reciclatech.backend.service;

import com.reciclatech.backend.model.*;
import com.reciclatech.backend.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseService.class);


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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private HistoricoAlteracaoRepository historicoRepository;
    @Autowired private ParceiroRepository parceiroRepository;
    @Autowired private HttpSession session;


    // =========================================================================
    // 1. HELPERS E CONTEXTO DA SESSÃO
    // =========================================================================

    private Long getArmazemIdLogado() {
        Object id = session.getAttribute("idPlanilhaAtiva");
        if (id != null) return Long.parseLong(id.toString());
        throw new SessaoInvalidaException(); // falha fechada: sem login nunca se assume um armazém
    }

    private Armazem getArmazemLogado() {
        return armazemRepository.findById(getArmazemIdLogado()).orElse(null);
    }

    // ---- AUDITORIA (fase 8.5): grava na mesma transação da alteração; se um falhar, os dois voltam atrás ----

    private String responsavelAtual() {
        try {
            Object parceiroId = session.getAttribute("parceiroId");
            if (parceiroId != null) {
                String email = parceiroRepository.findById(Long.parseLong(parceiroId.toString()))
                        .map(Parceiro::getEmail).orElse(null);
                if (email != null) return "Proprietário (" + email + ")";
            }
            Object nome = session.getAttribute("nomeFuncionarioLogado");
            if (nome != null) return nome.toString();
        } catch (RuntimeException e) { /* sem sessão */ }
        return "Não identificado";
    }

    private void auditar(String entidade, String acao, String referencia, String unidade, BigDecimal anterior, BigDecimal novo) {
        HistoricoAlteracao h = new HistoricoAlteracao();
        h.setArmazemId(getArmazemIdLogado());
        h.setDataHora(java.time.LocalDateTime.now(ZoneId.of("America/Recife")));
        h.setEntidade(entidade);
        h.setAcao(acao);
        h.setReferencia(referencia != null && referencia.length() > 255 ? referencia.substring(0, 255) : referencia);
        h.setUnidade(unidade);
        h.setValorAnterior(anterior);
        h.setValorNovo(novo);
        h.setResponsavel(responsavelAtual());
        historicoRepository.save(h);
    }

    public List<HistoricoAlteracao> listarHistorico(String entidade, int limite) {
        org.springframework.data.domain.Pageable p = org.springframework.data.domain.PageRequest.of(0, Math.max(1, Math.min(limite, 1000)));
        return (entidade == null || entidade.isBlank())
                ? historicoRepository.findByArmazemIdOrderByDataHoraDescIdDesc(getArmazemIdLogado(), p)
                : historicoRepository.findByArmazemIdAndEntidadeOrderByDataHoraDescIdDesc(getArmazemIdLogado(), entidade, p);
    }

    private static String mascararCpf(String cpf) {
        String d = cpf == null ? "" : cpf.replaceAll("\\D", "");
        return d.length() >= 4 ? "CPF final " + d.substring(d.length() - 4) : "CPF";
    }

    private Optional<Usuario> buscarUsuarioPorIdOuTelefone(String idOuTelefone) {
        if (idOuTelefone == null || idOuTelefone.trim().isEmpty()) return Optional.empty();

        try {
            Long id = Long.parseLong(idOuTelefone);
            Optional<Usuario> u = usuarioRepository.findById(id);
            if (u.isPresent() && u.get().getArmazem() != null && u.get().getArmazem().getId().equals(getArmazemIdLogado())) {
                return u;
            }
        } catch (NumberFormatException e) {
            // Não é ID numérico, busca pelo telefone
        }

        // Tolerante a duplicados antigos (um Usuario novo por pedido): pega o mais recente.
        List<Usuario> porTelefone = usuarioRepository.findAllByTelefoneAndArmazemIdOrderByIdAsc(idOuTelefone, getArmazemIdLogado());
        return porTelefone.isEmpty() ? Optional.empty() : Optional.of(porTelefone.get(porTelefone.size() - 1));
    }

    /** IDs de todos os Usuarios que a chave (id ou telefone) representa neste armazém. */
    private List<Long> idsUsuariosPorChave(String idOuTelefone) {
        List<Long> ids = new ArrayList<>();
        if (idOuTelefone == null || idOuTelefone.trim().isEmpty()) return ids;
        try {
            Optional<Usuario> u = usuarioRepository.findById(Long.parseLong(idOuTelefone));
            if (u.isPresent() && u.get().getArmazem() != null && u.get().getArmazem().getId().equals(getArmazemIdLogado())) {
                ids.add(u.get().getId());
                return ids;
            }
        } catch (NumberFormatException e) { /* é telefone */ }
        for (Usuario u : usuarioRepository.findAllByTelefoneAndArmazemIdOrderByIdAsc(idOuTelefone, getArmazemIdLogado())) ids.add(u.getId());
        return ids;
    }


    // =========================================================================
    // 2. AUTENTICAÇÃO, SaaS E COTAÇÕES PÚBLICAS
    // =========================================================================

    /**
     * Login de OPERADOR (funcionário). O dono entra pela conta (/conta/login), não por aqui.
     * Senhas novas são BCrypt; senhas antigas (SHA-256) ainda entram uma vez e são convertidas para BCrypt.
     */
    public Map<String, String> autenticarOperador(String login, String senhaPura) {
        if (login == null || senhaPura == null) return null;
        try {
            String sql = "SELECT f.id AS func_id, f.nome AS func_nome, f.senha AS func_senha, f.perfil, f.armazem_id, " +
                    "a.nome AS nome_armazem, a.cnpj, a.endereco, a.telefone, a.status, a.plano " +
                    "FROM funcionario f " +
                    "JOIN armazem a ON f.armazem_id = a.id " +
                    "WHERE f.login = ? AND f.ativo = true";

            List<Map<String, String>> achados = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, String> dados = new HashMap<>();
                dados.put("funcId", rs.getString("func_id"));
                dados.put("senhaHash", rs.getString("func_senha"));
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
            }, login);
            if (achados.isEmpty()) return null;

            Map<String, String> dados = achados.get(0);
            String hashSalvo = dados.remove("senhaHash");
            String funcId = dados.remove("funcId");
            if (!senhaConfere(senhaPura, hashSalvo)) return null;

            if (hashSalvo != null && !hashSalvo.startsWith("$2")) {
                jdbcTemplate.update("UPDATE funcionario SET senha = ? WHERE id = ?",
                        passwordEncoder.encode(senhaPura), Long.valueOf(funcId));
            }
            return dados;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean senhaConfere(String senhaPura, String hashSalvo) {
        if (hashSalvo == null) return false;
        if (hashSalvo.startsWith("$2")) return passwordEncoder.matches(senhaPura, hashSalvo);
        // formato antigo (SHA-256 em hexadecimal)
        return java.security.MessageDigest.isEqual(
                criptografarSenha(senhaPura).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                hashSalvo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public boolean cadastrarParceiroSaaS(String nomeArmazem, String nomeProprietario, String documentoCnpjCpf, String telefone, String endereco, String login, String senha, String planoEscolhido) {
        try {
            // CNPJ/CPF é opcional: vazio vira NULL e só entra na checagem de duplicidade quando preenchido
            String documento = (documentoCnpjCpf == null || documentoCnpjCpf.isBlank()) ? null : documentoCnpjCpf.trim();
            Integer count = (documento == null)
                    ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM armazem WHERE login = ?", Integer.class, login)
                    : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM armazem WHERE login = ? OR cnpj = ?", Integer.class, login, documento);
            if (count != null && count > 0) return false;

            java.sql.Date dataHoje = java.sql.Date.valueOf(LocalDate.now(ZoneId.of("America/Recife")));
            String senhaCriptografada = criptografarSenha(senha);

            String sqlInsert = "INSERT INTO armazem (nome, cnpj, telefone, endereco, login, senha, perfil, status, plano, data_cadastro) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'GESTOR', 'TRIAL', ?, ?)";

            jdbcTemplate.update(sqlInsert, nomeArmazem, documento, telefone, endereco, login, senhaCriptografada, planoEscolhido, dataHoje);
            return true;

        } catch (Exception e) {
            log.error("Erro ao cadastrar parceiro", e);
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
            dados.put("uf", a.getUf());
            dados.put("cidade", a.getCidade());
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

    @Transactional(rollbackFor = Exception.class)
    public void salvarMaterial(Material material) throws IOException {
        material.setArmazem(getArmazemLogado());
        materialRepository.save(material);
        auditar(HistoricoAlteracao.MATERIAL, HistoricoAlteracao.CRIADO, material.getNome(), material.getUnidade(), null, material.getPrecoPorKg());
    }

    @Transactional(rollbackFor = Exception.class)
    public void atualizarPrecoMaterial(Long id, BigDecimal novoPreco) throws IOException {
        Optional<Material> achado = materialRepository.findById(id);
        if (achado.isEmpty() || achado.get().getArmazem() == null
                || !achado.get().getArmazem().getId().equals(getArmazemIdLogado())) return;
        Material m = achado.get();
        BigDecimal anterior = m.getPrecoPorKg();
        if (anterior != null && anterior.compareTo(novoPreco) == 0) return; // nada mudou: nada a registrar
        m.setPrecoPorKg(novoPreco);
        materialRepository.save(m);
        auditar(HistoricoAlteracao.MATERIAL, HistoricoAlteracao.PRECO_ALTERADO, m.getNome(), m.getUnidade(), anterior, novoPreco);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletarMaterial(Long id) throws IOException {
        Optional<Material> achado = materialRepository.findById(id);
        if (achado.isEmpty() || achado.get().getArmazem() == null
                || !achado.get().getArmazem().getId().equals(getArmazemIdLogado())) return;
        Material m = achado.get();
        auditar(HistoricoAlteracao.MATERIAL, HistoricoAlteracao.EXCLUIDO, m.getNome(), m.getUnidade(), m.getPrecoPorKg(), null);
        materialRepository.delete(m);
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
        usuario.setArmazem(armazemRepository.findById(Long.parseLong(planilhaAlvo)).orElseGet(this::getArmazemLogado));
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

    /** Atendimento de balcão: sempre cria um cliente novo (telefone é chave temporária). */
    @Transactional(rollbackFor = Exception.class)
    public void salvarSolicitacaoInicial(Usuario usuario, String endereco) throws IOException {
        salvarSolicitacaoInicial(usuario, endereco, getArmazemIdLogado().toString(), false);
    }

    /** Pedido vindo do formulário público: reaproveita o cliente pelo telefone naquele armazém. */
    @Transactional(rollbackFor = Exception.class)
    public void salvarSolicitacaoInicial(Usuario usuario, String endereco, String idArmazemDestino) throws IOException {
        salvarSolicitacaoInicial(usuario, endereco, idArmazemDestino, true);
    }

    private void salvarSolicitacaoInicial(Usuario usuario, String endereco, String idArmazemDestino, boolean reaproveitar) throws IOException {
        Long armazemId = idArmazemDestino != null ? Long.parseLong(idArmazemDestino) : getArmazemIdLogado();
        Armazem destino = armazemRepository.findById(armazemId)
                .orElseThrow(() -> new IOException("Armazém de destino não encontrado"));

        if (reaproveitar && usuario.getTelefone() != null) {
            List<Usuario> existentes = usuarioRepository.findAllByTelefoneAndArmazemIdOrderByIdAsc(usuario.getTelefone(), armazemId);
            if (!existentes.isEmpty()) {
                Usuario atual = existentes.get(existentes.size() - 1);
                atual.setNome(usuario.getNome());
                atual.setEndereco(endereco);
                usuarioRepository.save(atual);
                // já existe pedido aberto deste cliente? então é duplo clique/reenvio: não cria outro.
                if (!ofertaRepository.findAllByUsuarioIdAndStatusIn(atual.getId(), STATUS_ABERTOS).isEmpty()) return;
                usuario = atual;
            }
        }

        if (usuario.getId() == null) {
            usuario.setArmazem(destino);
            usuario.setEndereco(endereco);
            usuario.prePersist();
            usuario = usuarioRepository.save(usuario);
        }

        Oferta oferta = new Oferta();
        oferta.setArmazem(destino);
        oferta.setUsuario(usuario);
        oferta.setMaterial("Solicitação de Coleta");
        oferta.setPeso(0.0);
        oferta.setEndereco(endereco);
        oferta.setPrecoEstimado(BigDecimal.ZERO);
        oferta.setStatus(Oferta.StatusOferta.DISPONIVEL);
        oferta.setData(LocalDate.now(ZoneId.of("America/Recife")).toString());
        ofertaRepository.save(oferta);
    }

    private static final List<Oferta.StatusOferta> STATUS_ABERTOS =
            Arrays.asList(Oferta.StatusOferta.DISPONIVEL, Oferta.StatusOferta.EM_ATENDIMENTO);

    public List<Usuario> buscarUsuariosComColetasPendentes() throws IOException {
        // Fila = todo pedido ainda aberto, de qualquer data (antes só aparecia o de hoje).
        Map<Long, Usuario> porId = new LinkedHashMap<>();
        for (Oferta o : ofertaRepository.findAllByArmazemIdOrderByDataCriacaoDesc(getArmazemIdLogado())) {
            if (o.getUsuario() == null || !STATUS_ABERTOS.contains(o.getStatus())) continue;
            Usuario u = o.getUsuario();
            Usuario existente = porId.get(u.getId());
            if (existente == null) {
                u.setStatusPlanilha(o.getStatus().toString());
                porId.put(u.getId(), u);
            } else if (o.getStatus() == Oferta.StatusOferta.EM_ATENDIMENTO) {
                existente.setStatusPlanilha("EM_ATENDIMENTO");
            }
        }
        return new ArrayList<>(porId.values());
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

    @Transactional(rollbackFor = Exception.class)
    public void marcarComoEmAtendimento(String idVendedor) throws IOException {
        mudarStatusOferta(idVendedor, Collections.singletonList(Oferta.StatusOferta.DISPONIVEL), Oferta.StatusOferta.EM_ATENDIMENTO);
    }

    @Transactional(rollbackFor = Exception.class)
    public void marcarSolicitacaoComoConcluida(String idVendedor) throws IOException {
        mudarStatusOferta(idVendedor, STATUS_ABERTOS, Oferta.StatusOferta.FINALIZADO);
    }

    /**
     * Fecha o atendimento inteiro numa única transação: grava as vendas, atualiza o CPF e
     * encerra o pedido. Se qualquer passo falhar, nada é gravado. Se o pedido já foi
     * encerrado (duplo clique, aba repetida), não grava nada e devolve false.
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean finalizarAtendimento(String idVendedor, String cpfFinal, List<List<Object>> linhasParaSalvar) throws IOException {
        List<Long> ids = idsUsuariosPorChave(idVendedor);
        if (ids.isEmpty()) return false;
        List<Oferta> abertos = ofertaRepository.travarPorUsuariosEStatus(ids, STATUS_ABERTOS);
        if (abertos.isEmpty()) return false;

        registrarVendasEmLote(linhasParaSalvar);
        atualizarCpfUsuario(idVendedor, cpfFinal);
        for (Oferta o : abertos) {
            o.setStatus(Oferta.StatusOferta.FINALIZADO);
            ofertaRepository.save(o);
        }
        return true;
    }

    /** Cancela pedido aberto. Fica registrado como CANCELADO (não some nem vira "finalizado"). */
    @Transactional(rollbackFor = Exception.class)
    public void cancelarSolicitacao(String idVendedor) throws IOException {
        mudarStatusOferta(idVendedor, STATUS_ABERTOS, Oferta.StatusOferta.CANCELADO);
    }

    private void mudarStatusOferta(String idVendedor, List<Oferta.StatusOferta> de, Oferta.StatusOferta para) {
        List<Long> ids = idsUsuariosPorChave(idVendedor);
        if (ids.isEmpty()) return;
        for (Oferta o : ofertaRepository.travarPorUsuariosEStatus(ids, de)) {
            o.setStatus(para);
            ofertaRepository.save(o);
        }
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

    @Transactional(rollbackFor = Exception.class)
    public void salvarDespesa(String descricao, BigDecimal valor) throws IOException {
        Despesa d = new Despesa();
        d.setArmazem(getArmazemLogado());
        d.setDescricao(descricao);
        d.setValor(valor);
        d.setDataDespesa(LocalDate.now(ZoneId.of("America/Recife")));
        despesaRepository.save(d);
        auditar(HistoricoAlteracao.DESPESA, HistoricoAlteracao.CRIADO, descricao, null, null, valor);
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

    @Transactional(rollbackFor = Exception.class)
    public void deletarDespesa(String id) throws IOException {
        // só apaga se a despesa for do armazém logado
        Optional<Despesa> achada = despesaRepository.findById(Long.parseLong(id));
        if (achada.isPresent() && achada.get().getArmazem() != null
                && achada.get().getArmazem().getId().equals(getArmazemIdLogado())) {
            Despesa d = achada.get();
            auditar(HistoricoAlteracao.DESPESA, HistoricoAlteracao.EXCLUIDO, d.getDescricao(), null, d.getValor(), null);
            despesaRepository.delete(d);
        }
    }

    /** Mesmo critério da lista pública da home: armazém ATIVO, ou TRIAL com até 7 dias de uso. */
    public boolean armazemAceitaSolicitacoes(String idArmazem) {
        if (idArmazem == null) return false;
        try {
            Long id = Long.parseLong(idArmazem.trim());
            LocalDate hoje = LocalDate.now(ZoneId.of("America/Recife"));
            return armazemRepository.findById(id).filter(a -> {
                if ("ATIVO".equals(a.getStatus())) return true;
                if ("TRIAL".equals(a.getStatus()) && a.getDataCadastro() != null) {
                    return java.time.temporal.ChronoUnit.DAYS.between(a.getDataCadastro(), hoje) <= 7;
                }
                return false;
            }).isPresent();
        } catch (NumberFormatException e) {
            return false;
        }
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

    @Transactional(rollbackFor = Exception.class)
    public void salvarFornecedorVip(String cpf, String material, BigDecimal preco) throws IOException {
        String cpfLimpo = cpf.replaceAll("\\D", "");
        Optional<PrecoVip> existente = precoVipRepository.findByArmazemIdAndCpfCatadorAndMaterialNome(getArmazemIdLogado(), cpfLimpo, material);
        String ref = material + " - " + mascararCpf(cpfLimpo);
        BigDecimal anterior = existente.map(PrecoVip::getPrecoEspecial).orElse(null);
        PrecoVip p = existente.orElseGet(PrecoVip::new);
        if (existente.isEmpty()) p.setArmazem(getArmazemLogado());
        p.setCpfCatador(cpfLimpo);
        p.setMaterialNome(material);
        p.setPrecoEspecial(preco);
        precoVipRepository.save(p);
        if (anterior == null) auditar(HistoricoAlteracao.PRECO_VIP, HistoricoAlteracao.CRIADO, ref, null, null, preco);
        else if (anterior.compareTo(preco) != 0) auditar(HistoricoAlteracao.PRECO_VIP, HistoricoAlteracao.PRECO_ALTERADO, ref, null, anterior, preco);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletarFornecedorVip(String cpf, String material) throws IOException {
        String cpfLimpo = cpf.replaceAll("\\D", "");
        precoVipRepository.findByArmazemIdAndCpfCatadorAndMaterialNome(getArmazemIdLogado(), cpfLimpo, material)
                .ifPresent(p -> {
                    auditar(HistoricoAlteracao.PRECO_VIP, HistoricoAlteracao.EXCLUIDO, material + " - " + mascararCpf(cpfLimpo), null, p.getPrecoEspecial(), null);
                    precoVipRepository.delete(p);
                });
    }


    // =========================================================================
    // 8. PAINEL MASTER (ADMINISTRATIVO SaaS)
    // =========================================================================

    public List<Map<String, Object>> listarTodosArmazensSaaS() {
        try {
            String sql = "SELECT * FROM armazem ORDER BY id DESC";
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("Erro ao listar armazéns", e);
            return new ArrayList<>();
        }
    }

    public void atualizarStatusArmazem(Long id, String novoStatus) {
        try {
            String sql = "UPDATE armazem SET status = ? WHERE id = ?";
            jdbcTemplate.update(sql, novoStatus, id);
        } catch (Exception e) {
            log.error("Erro ao atualizar status", e);
        }
    }


    // =========================================================================
    // 9. GESTÃO DE FUNCIONÁRIOS, SEGURANÇA E PERFIL
    // =========================================================================

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
        f.setSenha(passwordEncoder.encode(senhaPura));
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