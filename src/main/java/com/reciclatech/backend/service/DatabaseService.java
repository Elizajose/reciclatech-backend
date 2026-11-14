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

    /** Teto do preço negociado em relação à tabela/VIP (variável PRECO_MAX_MULTIPLO; 2 = até o dobro). */
    @org.springframework.beans.factory.annotation.Value("${coletae.preco-max-multiplo:2}")
    private double multiploPrecoMax = com.reciclatech.backend.util.RegrasVenda.MULTIPLO_PADRAO;


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
     * Login de OPERADOR (funcionário): código do armazém + usuário + senha (o usuário é único só dentro do armazém). O dono entra pela aba "Sou dono".
     * Senhas novas são BCrypt; senhas antigas (SHA-256) ainda entram uma vez e são convertidas para BCrypt.
     * Devolve também: trialVencido ("true"/"false"), origem ("FUNC") e a "impressão" da senha (credId/credFp) para a sessão.
     */
    public Map<String, String> autenticarOperador(String codigoArmazem, String login, String senhaPura) {
        if (codigoArmazem == null || codigoArmazem.isBlank() || login == null || senhaPura == null) return null;
        try {
            String sql = "SELECT f.id AS func_id, f.nome AS func_nome, f.senha AS func_senha, f.perfil, f.armazem_id, " +
                    "a.nome AS nome_armazem, a.cnpj, a.endereco, a.telefone, a.status, a.plano, a.data_cadastro " +
                    "FROM funcionario f " +
                    "JOIN armazem a ON f.armazem_id = a.id " +
                    "WHERE a.codigo = ? AND f.login = ? AND f.ativo = true";

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
                java.sql.Date dc = rs.getDate("data_cadastro");
                dados.put("dataCadastro", dc == null ? "" : dc.toLocalDate().toString());
                return dados;
            }, codigoArmazem, login);
            if (achados.isEmpty()) return null;

            Map<String, String> dados = achados.get(0);
            String hashSalvo = dados.remove("senhaHash");
            String funcId = dados.remove("funcId");
            if (!senhaConfere(senhaPura, hashSalvo)) return null;

            if (hashSalvo != null && !hashSalvo.startsWith("$2")) {
                hashSalvo = passwordEncoder.encode(senhaPura);
                jdbcTemplate.update("UPDATE funcionario SET senha = ? WHERE id = ?", hashSalvo, Long.valueOf(funcId));
            }
            dados.put("origem", "FUNC");
            dados.put("credId", funcId);
            dados.put("credFp", com.reciclatech.backend.util.Impressao.de(hashSalvo));
            completarAssinatura(dados);
            return dados;
        } catch (Exception e) {
            log.error("Erro no login de operador", e);
            return null;
        }
    }

    /**
     * Armazéns ANTIGOS (criados antes da conta de dono) ainda têm usuário e senha na própria linha do armazém e
     * nenhum dono vinculado. Eles continuam entrando, como gestor, só com esse usuário e senha (o código é opcional).
     * Quando o armazém ganhar um dono, este caminho deixa de valer e o acesso passa a ser pela conta do dono.
     */
    public Map<String, String> autenticarArmazemAntigo(String codigoArmazem, String login, String senhaPura) {
        if (login == null || login.isBlank() || senhaPura == null) return null;
        try {
            boolean comCodigo = codigoArmazem != null && !codigoArmazem.isBlank();
            String sql = "SELECT a.id, a.nome, a.cnpj, a.endereco, a.telefone, a.status, a.plano, a.data_cadastro, a.senha " +
                    "FROM armazem a WHERE a.login = ? AND a.proprietario_id IS NULL" + (comCodigo ? " AND a.codigo = ?" : "");
            Object[] args = comCodigo ? new Object[]{login, codigoArmazem} : new Object[]{login};
            List<Map<String, String>> achados = jdbcTemplate.query(sql, (rs, n) -> {
                Map<String, String> d = new HashMap<>();
                d.put("idPlanilha", rs.getString("id"));
                d.put("perfil", "GESTOR");
                d.put("nomeArmazem", rs.getString("nome"));
                d.put("cnpj", rs.getString("cnpj"));
                d.put("endereco", rs.getString("endereco"));
                d.put("telefone", rs.getString("telefone"));
                d.put("status", rs.getString("status"));
                d.put("plano", rs.getString("plano"));
                d.put("nomeFuncionarioLogado", "Gestor / Administrador");
                java.sql.Date dc = rs.getDate("data_cadastro");
                d.put("dataCadastro", dc == null ? "" : dc.toLocalDate().toString());
                d.put("senhaHash", rs.getString("senha"));
                return d;
            }, args);
            if (achados.isEmpty()) return null;

            Map<String, String> dados = achados.get(0);
            String hashSalvo = dados.remove("senhaHash");
            if (!senhaConfere(senhaPura, hashSalvo)) return null;
            if (hashSalvo != null && !hashSalvo.startsWith("$2")) {
                hashSalvo = passwordEncoder.encode(senhaPura);
                jdbcTemplate.update("UPDATE armazem SET senha = ? WHERE id = ?", hashSalvo, Long.valueOf(dados.get("idPlanilha")));
            }
            dados.put("origem", "LEGADO");
            dados.put("credId", dados.get("idPlanilha"));
            dados.put("credFp", com.reciclatech.backend.util.Impressao.de(hashSalvo));
            completarAssinatura(dados);
            return dados;
        } catch (Exception e) {
            log.error("Erro no login de armazém antigo", e);
            return null;
        }
    }

    /** Calcula, com a mesma regra usada em toda a aplicação, se o armazém pode operar agora. */
    private void completarAssinatura(Map<String, String> dados) {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Recife"));
        String dc = dados.get("dataCadastro");
        LocalDate cadastro = (dc == null || dc.isBlank()) ? null : LocalDate.parse(dc);
        dados.put("trialVencido", String.valueOf(com.reciclatech.backend.util.Assinatura.trialVencido(dados.get("status"), cadastro, hoje)));
        dados.remove("dataCadastro");
    }

    private boolean senhaConfere(String senhaPura, String hashSalvo) {
        if (hashSalvo == null) return false;
        if (hashSalvo.startsWith("$2")) return passwordEncoder.matches(senhaPura, hashSalvo);
        // formato antigo (SHA-256 em hexadecimal)
        return java.security.MessageDigest.isEqual(
                criptografarSenha(senhaPura).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                hashSalvo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Documento do armazém sem pontuação e em maiúsculas, para achar duplicados escritos de formas diferentes. */
    static final String SQL_DOC_NORMALIZADO = "UPPER(regexp_replace(COALESCE(cnpj, ''), '[^A-Za-z0-9]', '', 'g'))";

    public boolean cadastrarParceiroSaaS(String nomeArmazem, String nomeProprietario, String documentoCnpjCpf, String telefone, String endereco, String login, String senha, String planoEscolhido) {
        try {
            // CNPJ/CPF é opcional: vazio vira NULL e só entra na checagem de duplicidade quando preenchido
            String documento = (documentoCnpjCpf == null || documentoCnpjCpf.isBlank()) ? null
                    : com.reciclatech.backend.util.DocumentoValidator.formatar(documentoCnpjCpf);
            Integer count = (documento == null)
                    ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM armazem WHERE login = ?", Integer.class, login)
                    : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM armazem WHERE login = ? OR " + SQL_DOC_NORMALIZADO + " = ?",
                            Integer.class, login, com.reciclatech.backend.util.DocumentoValidator.normalizar(documento));
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
                .filter(a -> com.reciclatech.backend.util.Assinatura.aceitaPedidosPublicos(a.getStatus(), a.getDataCadastro(), hoje))
                .collect(Collectors.toList());

        List<Map<String, Object>> cotacoes = new ArrayList<>();
        for (Armazem a : armazens) {
            Map<String, Object> dados = new HashMap<>();
            dados.put("idPlanilha", a.getId().toString());
            dados.put("nome", a.getNome());
            dados.put("htmlId", "arm" + a.getId()); // único por armazém (nomes parecidos davam o mesmo id na página)
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

    private static String chaveNome(String nome) {
        return nome == null ? "" : nome.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static final Set<String> UNIDADES = Set.of("KG", "UN");

    @Transactional(rollbackFor = Exception.class)
    public void salvarMaterial(Material material) throws IOException {
        String nome = com.reciclatech.backend.util.Valores.texto(material.getNome(), 100);
        if (nome == null) throw new IllegalArgumentException("Informe o nome do material (até 100 letras).");
        if (!com.reciclatech.backend.util.Valores.positivoAte(material.getPrecoPorKg(), com.reciclatech.backend.util.Valores.PRECO_MAX)) {
            throw new IllegalArgumentException("Informe um preço maior que zero.");
        }
        String unidade = material.getUnidade() == null ? "KG" : material.getUnidade().trim().toUpperCase(Locale.ROOT);
        if (!UNIDADES.contains(unidade)) throw new IllegalArgumentException("Unidade inválida.");
        for (Material existente : materialRepository.findAllByArmazemIdOrderByNomeAsc(getArmazemIdLogado())) {
            if (chaveNome(existente.getNome()).equals(chaveNome(nome))) {
                throw new IllegalArgumentException("Já existe um material chamado \"" + existente.getNome() + "\".");
            }
        }
        material.setNome(nome);
        material.setUnidade(unidade);
        material.setArmazem(getArmazemLogado());
        materialRepository.save(material);
        auditar(HistoricoAlteracao.MATERIAL, HistoricoAlteracao.CRIADO, material.getNome(), material.getUnidade(), null, material.getPrecoPorKg());
    }

    @Transactional(rollbackFor = Exception.class)
    public void atualizarPrecoMaterial(Long id, BigDecimal novoPreco) throws IOException {
        atualizarPrecoMaterial(id, novoPreco, false);
    }

    /** {@code confirmado=false} e variação grande (mais que o dobro / menos da metade) => VariacaoPrecoException, nada é gravado. */
    @Transactional(rollbackFor = Exception.class)
    public void atualizarPrecoMaterial(Long id, BigDecimal novoPreco, boolean confirmado) throws IOException {
        if (!com.reciclatech.backend.util.Valores.positivoAte(novoPreco, com.reciclatech.backend.util.Valores.PRECO_MAX)) {
            throw new IllegalArgumentException("Informe um preço maior que zero.");
        }
        Optional<Material> achado = materialRepository.findById(id);
        if (achado.isEmpty() || achado.get().getArmazem() == null
                || !achado.get().getArmazem().getId().equals(getArmazemIdLogado())) return;
        Material m = achado.get();
        BigDecimal anterior = m.getPrecoPorKg();
        if (anterior != null && anterior.compareTo(novoPreco) == 0) return; // nada mudou: nada a registrar
        if (!confirmado && com.reciclatech.backend.util.RegrasVenda.variacaoSuspeita(anterior, novoPreco)) {
            throw new VariacaoPrecoException(com.reciclatech.backend.util.RegrasVenda.textoVariacao(m.getNome(), anterior, novoPreco));
        }
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
        // O estoque é guardado pelo NOME do material: apagar com saldo faria o estoque "sumir" das telas.
        double saldo = saldoDoMaterial(m.getNome());
        if (Math.abs(saldo) > 0.0005) {
            throw new IllegalArgumentException("\"" + m.getNome() + "\" ainda tem " + String.format("%.2f", saldo).replace('.', ',')
                    + " no estoque. Zere o estoque (venda ou ajuste) antes de excluir.");
        }
        // preços VIP deste material ficariam órfãos: saem junto
        for (PrecoVip p : precoVipRepository.findAllByArmazemId(getArmazemIdLogado())) {
            if (chaveNome(p.getMaterialNome()).equals(chaveNome(m.getNome()))) precoVipRepository.delete(p);
        }
        auditar(HistoricoAlteracao.MATERIAL, HistoricoAlteracao.EXCLUIDO, m.getNome(), m.getUnidade(), m.getPrecoPorKg(), null);
        materialRepository.delete(m);
    }

    /** Saldo no pátio de um material (entradas - saídas), pelo mesmo critério das telas de estoque. */
    private double saldoDoMaterial(String nomeMaterial) {
        Long arm = getArmazemIdLogado();
        Double ent = ofertaRepository.somarEntradasPorMaterial(arm, nomeMaterial);
        Double sai = ofertaRepository.somarSaidasPorMaterial(arm, nomeMaterial);
        return (ent == null ? 0.0 : ent) - (sai == null ? 0.0 : sai);
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
    public Usuario salvarSolicitacaoInicial(Usuario usuario, String endereco) throws IOException {
        return salvarSolicitacaoInicial(usuario, endereco, getArmazemIdLogado().toString(), false);
    }

    /** Pedido vindo do formulário público: reaproveita o cliente pelo telefone naquele armazém. */
    @Transactional(rollbackFor = Exception.class)
    public Usuario salvarSolicitacaoInicial(Usuario usuario, String endereco, String idArmazemDestino) throws IOException {
        return salvarSolicitacaoInicial(usuario, endereco, idArmazemDestino, true);
    }

    private Usuario salvarSolicitacaoInicial(Usuario usuario, String endereco, String idArmazemDestino, boolean reaproveitar) throws IOException {
        Long armazemId = idArmazemDestino != null ? Long.parseLong(idArmazemDestino) : getArmazemIdLogado();
        Armazem destino = armazemRepository.findById(armazemId)
                .orElseThrow(() -> new IOException("Armazém de destino não encontrado"));

        if (reaproveitar && usuario.getTelefone() != null) {
            List<Usuario> existentes = usuarioRepository.findAllByTelefoneAndArmazemIdOrderByIdAsc(usuario.getTelefone(), armazemId);
            if (!existentes.isEmpty()) {
                Usuario atual = existentes.get(existentes.size() - 1);
                // O formulário público é aberto a qualquer pessoa: ele NÃO reescreve nome/endereço de um cliente que já
                // existe (alguém poderia trocar os dados de outro pelo telefone). Só completa o que estiver vazio.
                boolean mudou = false;
                if (atual.getNome() == null || atual.getNome().isBlank()) { atual.setNome(usuario.getNome()); mudou = true; }
                if (atual.getEndereco() == null || atual.getEndereco().isBlank()) { atual.setEndereco(endereco); mudou = true; }
                if (mudou) usuarioRepository.save(atual);
                // já existe pedido aberto deste cliente? então é duplo clique/reenvio: não cria outro.
                if (!ofertaRepository.findAllByUsuarioIdAndStatusIn(atual.getId(), STATUS_ABERTOS).isEmpty()) return atual;
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
        return usuario;
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
                u.setAtendentePlanilha(o.getStatus() == Oferta.StatusOferta.EM_ATENDIMENTO ? o.getAtendente() : null);
                porId.put(u.getId(), u);
            } else if (o.getStatus() == Oferta.StatusOferta.EM_ATENDIMENTO) {
                existente.setStatusPlanilha("EM_ATENDIMENTO");
                existente.setAtendentePlanilha(o.getAtendente());
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

    /** Resultado de "iniciar atendimento". */
    public static final class InicioAtendimento {
        public final boolean ok;
        public final String ocupadoPor; // preenchido quando outro operador já está atendendo
        InicioAtendimento(boolean ok, String ocupadoPor) { this.ok = ok; this.ocupadoPor = ocupadoPor; }
    }

    private String atendenteAtual() {
        Object nome = session.getAttribute("atendenteNome");
        if (nome != null && !nome.toString().isBlank()) return nome.toString();
        Object func = session.getAttribute("nomeFuncionarioLogado");
        return func == null ? "Operador" : func.toString();
    }

    /**
     * Começa (ou retoma) o atendimento de um cliente, gravando QUEM está atendendo.
     * Se outro operador já estiver com o cliente aberto, não toma o atendimento dele (devolve quem é), a menos que
     * {@code assumir} seja verdadeiro (decisão consciente na tela). Trava as linhas: dois cliques simultâneos não passam os dois.
     */
    @Transactional(rollbackFor = Exception.class)
    public InicioAtendimento iniciarAtendimento(String idVendedor, boolean assumir) throws IOException {
        List<Long> ids = idsUsuariosPorChave(idVendedor);
        if (ids.isEmpty()) throw new IOException("Cliente não encontrado neste armazém.");
        List<Oferta> abertas = ofertaRepository.travarPorUsuariosEStatus(ids, STATUS_ABERTOS);
        if (abertas.isEmpty()) return new InicioAtendimento(false, null); // já finalizado/cancelado
        String eu = atendenteAtual();
        if (!assumir) {
            for (Oferta o : abertas) {
                if (o.getStatus() == Oferta.StatusOferta.EM_ATENDIMENTO && o.getAtendente() != null
                        && !o.getAtendente().isBlank() && !o.getAtendente().equals(eu)) {
                    return new InicioAtendimento(false, o.getAtendente());
                }
            }
        }
        for (Oferta o : abertas) {
            o.setStatus(Oferta.StatusOferta.EM_ATENDIMENTO);
            o.setAtendente(eu);
            ofertaRepository.save(o);
        }
        return new InicioAtendimento(true, null);
    }

    /** Há pedido aberto para este cliente? (a tela de atendimento só deve abrir para quem tem) */
    public boolean temPedidoAberto(String idVendedor) {
        for (Long id : idsUsuariosPorChave(idVendedor)) {
            if (!ofertaRepository.findAllByUsuarioIdAndStatusIn(id, STATUS_ABERTOS).isEmpty()) return true;
        }
        return false;
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

        validarLinhasDeCompra(idVendedor, cpfFinal, linhasParaSalvar);
        registrarVendasEmLote(linhasParaSalvar);
        atualizarCpfUsuario(idVendedor, cpfFinal);
        for (Oferta o : abertos) {
            o.setStatus(Oferta.StatusOferta.FINALIZADO);
            ofertaRepository.save(o);
        }
        return true;
    }

    /**
     * O navegador manda peso e preço, mas o servidor confere tudo de novo: material precisa existir NESTE armazém,
     * peso e preço > 0, preço dentro do teto sobre a tabela (ou VIP) e o total é recalculado aqui.
     */
    private void validarLinhasDeCompra(String idVendedor, String cpfFinal, List<List<Object>> linhas) throws IOException {
        if (linhas == null || linhas.isEmpty()) throw new IllegalArgumentException("Nenhum material informado.");
        Map<String, Material> porNome = new HashMap<>();
        for (Material m : materialRepository.findAllByArmazemIdOrderByNomeAsc(getArmazemIdLogado())) porNome.putIfAbsent(chaveNome(m.getNome()), m);
        String cpfRef = (cpfFinal != null && !cpfFinal.isBlank() && !"NÃO INFORMADO".equals(cpfFinal)) ? cpfFinal
                : buscarUsuarioPorIdOuTelefone(idVendedor).map(Usuario::getCpf).orElse(null);
        Map<String, BigDecimal> vip = buscarPrecosEspeciais(cpfRef);
        Map<String, BigDecimal> vipPorChave = new HashMap<>();
        vip.forEach((k, v) -> vipPorChave.putIfAbsent(chaveNome(k), v));

        for (List<Object> row : linhas) {
            String nome = row.get(1).toString();
            Material mat = porNome.get(chaveNome(nome));
            if (mat == null) throw new IllegalArgumentException("O material \"" + nome + "\" não existe neste armazém.");
            BigDecimal peso = com.reciclatech.backend.util.Valores.decimal(row.get(2) instanceof Double d ? d : Double.valueOf(row.get(2).toString().replace(",", ".")));
            BigDecimal preco = row.get(4) instanceof BigDecimal b ? b : new BigDecimal(row.get(4).toString().replace(",", "."));
            BigDecimal referencia = vipPorChave.getOrDefault(chaveNome(nome), mat.getPrecoPorKg());
            com.reciclatech.backend.util.RegrasVenda.validarLinha(mat.getNome(), peso, preco, referencia, multiploPrecoMax);
            row.set(5, com.reciclatech.backend.util.RegrasVenda.total(preco, peso));
            if (referencia != null && preco.compareTo(referencia) != 0) {
                log.info("Preço negociado: armazem={} material={} tabela={} pago={} por={}", getArmazemIdLogado(), mat.getNome(), referencia, preco, atendenteAtual());
            }
        }
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

    /** Serializa as operações que mexem no estoque deste armazém (saída e ajuste): impede vender o mesmo saldo duas vezes. */
    private void travarEstoque() {
        armazemRepository.travarPorId(getArmazemIdLogado());
    }

    /** Saldo atual de todos os materiais (pelo nome), para conferir saída/ajuste antes de gravar. */
    public Map<String, Double> saldosDoEstoque() {
        Map<String, Double> saldo = new HashMap<>();
        for (Oferta o : ofertaRepository.findAllByArmazemIdOrderByDataCriacaoDesc(getArmazemIdLogado())) {
            if (o.getMaterial() == null || o.getPeso() == null || o.getStatus() == null) continue;
            switch (o.getStatus()) {
                case VENDIDO, AJUSTE_POSITIVO -> saldo.merge(o.getMaterial(), o.getPeso(), Double::sum);
                case SAIDA_INDUSTRIA, AJUSTE_NEGATIVO -> saldo.merge(o.getMaterial(), -o.getPeso(), Double::sum);
                default -> { }
            }
        }
        return saldo;
    }

    /**
     * Ajuste manual de estoque (sobra/falta). Só aceita AJUSTE_POSITIVO e AJUSTE_NEGATIVO: antes qualquer status
     * (inclusive VENDIDO e SAIDA_INDUSTRIA) era gravado e bagunçava caixa e relatórios. Ajuste negativo não passa do saldo.
     */
    @Transactional(rollbackFor = Exception.class)
    public void salvarAjusteEstoque(String material, Double peso, String tipoAjuste, String motivo) throws IOException {
        Oferta.StatusOferta tipo;
        if ("AJUSTE_POSITIVO".equals(tipoAjuste)) tipo = Oferta.StatusOferta.AJUSTE_POSITIVO;
        else if ("AJUSTE_NEGATIVO".equals(tipoAjuste)) tipo = Oferta.StatusOferta.AJUSTE_NEGATIVO;
        else throw new IllegalArgumentException("Tipo de ajuste inválido.");

        BigDecimal pesoValido = com.reciclatech.backend.util.Valores.peso(peso);
        if (pesoValido == null) throw new IllegalArgumentException("Informe uma quantidade maior que zero.");
        String motivoValido = com.reciclatech.backend.util.Valores.texto(motivo, 200);
        if (motivoValido == null) throw new IllegalArgumentException("Informe o motivo do ajuste (até 200 letras).");

        travarEstoque();
        Material mat = materialRepository.findAllByArmazemIdOrderByNomeAsc(getArmazemIdLogado()).stream()
                .filter(m -> chaveNome(m.getNome()).equals(chaveNome(material))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Material não encontrado neste armazém."));

        if (tipo == Oferta.StatusOferta.AJUSTE_NEGATIVO) {
            double saldo = saldosDoEstoque().getOrDefault(mat.getNome(), 0.0);
            if (pesoValido.doubleValue() > saldo + 0.0005) {
                throw new IllegalArgumentException("Só há " + String.format("%.2f", Math.max(saldo, 0)).replace('.', ',')
                        + " de \"" + mat.getNome() + "\" no estoque; não dá para baixar " + pesoValido.toPlainString().replace('.', ',') + ".");
            }
        }

        Oferta o = new Oferta();
        o.setArmazem(getArmazemLogado());
        o.setMaterial(mat.getNome());
        o.setPeso(pesoValido.doubleValue());
        o.setEndereco("AJUSTE MANUAL: " + motivoValido);
        o.setPrecoEstimado(BigDecimal.ZERO);
        o.setData(LocalDate.now(ZoneId.of("America/Recife")).toString());
        o.setStatus(tipo);
        ofertaRepository.save(o);
        auditar(HistoricoAlteracao.ESTOQUE, tipo == Oferta.StatusOferta.AJUSTE_POSITIVO ? HistoricoAlteracao.CRIADO : HistoricoAlteracao.EXCLUIDO,
                (tipo == Oferta.StatusOferta.AJUSTE_POSITIVO ? "Entrada manual: " : "Baixa manual: ") + mat.getNome() + " — " + motivoValido,
                mat.getUnidade(), null, pesoValido);
    }

    /**
     * Saída para a indústria: confere saldo, cria o destinatário (tipo INDUSTRIA) e grava as linhas tudo numa
     * transação, com o estoque travado. Devolve o id do destinatário (para abrir o romaneio).
     */
    @Transactional(rollbackFor = Exception.class)
    public Long registrarSaidaIndustria(String nomeIndustria, String documento, Map<Long, BigDecimal> pesos, Map<Long, BigDecimal> precos) throws IOException {
        String nome = com.reciclatech.backend.util.Valores.texto(nomeIndustria, 100);
        if (nome == null) throw new IllegalArgumentException("Informe o nome da indústria/comprador (até 100 letras).");
        String doc = (documento == null || documento.isBlank()) ? "NÃO INFORMADO" : com.reciclatech.backend.util.DocumentoValidator.formatar(documento);
        if (!"NÃO INFORMADO".equals(doc) && !com.reciclatech.backend.util.DocumentoValidator.valido(doc)) {
            throw new IllegalArgumentException("CPF/CNPJ do comprador inválido.");
        }

        travarEstoque();
        Map<String, Double> saldo = saldosDoEstoque();
        List<List<Object>> linhas = new ArrayList<>();
        for (Material mat : listarMateriais()) {
            BigDecimal peso = pesos.get(mat.getId());
            if (peso == null) continue;
            BigDecimal preco = precos.get(mat.getId());
            if (!com.reciclatech.backend.util.Valores.positivoAte(peso, com.reciclatech.backend.util.Valores.PESO_MAX)) {
                throw new IllegalArgumentException("Quantidade inválida para \"" + mat.getNome() + "\".");
            }
            if (!com.reciclatech.backend.util.Valores.positivoAte(preco, com.reciclatech.backend.util.Valores.PRECO_MAX)) {
                throw new IllegalArgumentException("Informe o preço de venda de \"" + mat.getNome() + "\" (maior que zero).");
            }
            double disponivel = saldo.getOrDefault(mat.getNome(), 0.0);
            if (peso.doubleValue() > disponivel + 0.0005) {
                throw new IllegalArgumentException("Quantidade incompatível! Você tentou vender " + peso.toPlainString().replace('.', ',') + " de "
                        + mat.getNome() + ", mas só possui " + String.format("%.2f", Math.max(disponivel, 0)).replace('.', ',') + " no pátio.");
            }
            linhas.add(Arrays.asList(UUID.randomUUID().toString().substring(0, 13).toUpperCase(), mat.getNome(), peso.doubleValue(),
                    "VENDA INDÚSTRIA", preco, com.reciclatech.backend.util.RegrasVenda.total(preco, peso),
                    LocalDate.now(ZoneId.of("America/Recife")).toString(), null, "SAIDA_INDUSTRIA", doc));
        }
        if (linhas.isEmpty()) throw new IllegalArgumentException("Informe a quantidade e o preço de pelo menos um material.");

        Usuario destinatario = new Usuario();
        destinatario.setNome(nome.toUpperCase(Locale.ROOT) + " (SAÍDA)");
        destinatario.setEndereco(doc);
        destinatario.setTipo(Usuario.TipoUsuario.INDUSTRIA);
        destinatario.setArmazem(getArmazemLogado());
        destinatario.prePersist();
        destinatario = usuarioRepository.save(destinatario);

        for (List<Object> row : linhas) {
            row.set(7, destinatario.getId().toString());
        }
        registrarVendasEmLote(linhas);
        return destinatario.getId();
    }


    // =========================================================================
    // 6. FINANCEIRO, CAIXA E DESPESAS
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public void salvarDespesa(String descricao, BigDecimal valor) throws IOException {
        String desc = com.reciclatech.backend.util.Valores.texto(descricao, 150);
        if (desc == null) throw new IllegalArgumentException("Descreva a despesa (até 150 letras).");
        if (!com.reciclatech.backend.util.Valores.positivoAte(valor, com.reciclatech.backend.util.Valores.DINHEIRO_MAX)) {
            throw new IllegalArgumentException("Informe um valor maior que zero.");
        }
        descricao = desc;
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

    /** Despesas do armazém logado (entidades), usadas no cálculo de lucro por período. */
    public List<Despesa> listarDespesasBrutas() throws IOException {
        return despesaRepository.findAllByArmazemIdOrderByDataDespesaDesc(getArmazemIdLogado());
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
            return armazemRepository.findById(id)
                    .filter(a -> com.reciclatech.backend.util.Assinatura.aceitaPedidosPublicos(a.getStatus(), a.getDataCadastro(), hoje))
                    .isPresent();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Totais de um dia, calculados no servidor a partir do que está gravado (mesmas regras da tela de análises). */
    public static final class ResumoDia {
        public final BigDecimal entradas, saidas, despesas, lucro;
        public final double pesoComprado;
        ResumoDia(BigDecimal e, BigDecimal s, BigDecimal d, BigDecimal l, double p) { entradas = e; saidas = s; despesas = d; lucro = l; pesoComprado = p; }
    }

    public ResumoDia resumoDoDia(LocalDate dia) {
        String data = dia.toString();
        Map<String, String> unidades = new HashMap<>();
        for (Material m : materialRepository.findAllByArmazemIdOrderByNomeAsc(getArmazemIdLogado())) unidades.putIfAbsent(m.getNome(), m.getUnidade());
        BigDecimal entradas = BigDecimal.ZERO, saidas = BigDecimal.ZERO;
        double peso = 0.0;
        for (Oferta o : ofertaRepository.findAllByArmazemIdOrderByDataCriacaoDesc(getArmazemIdLogado())) {
            if (o.getStatus() == null || o.getData() == null || !data.equals(o.getData())) continue;
            BigDecimal valor = o.getPrecoEstimado() == null ? BigDecimal.ZERO : o.getPrecoEstimado();
            if (o.getStatus() == Oferta.StatusOferta.SAIDA_INDUSTRIA) {
                entradas = entradas.add(valor); // dinheiro que entrou (venda para a indústria)
            } else if (o.getStatus() == Oferta.StatusOferta.VENDIDO) {
                saidas = saidas.add(valor);     // dinheiro que saiu (pago ao catador)
                if (o.getPeso() != null && "KG".equalsIgnoreCase(unidades.getOrDefault(o.getMaterial(), "KG"))) peso += o.getPeso();
            }
        }
        BigDecimal despesas = despesaRepository.findAllByArmazemIdAndDataDespesa(getArmazemIdLogado(), dia).stream()
                .map(Despesa::getValor).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ResumoDia(entradas.setScale(2, java.math.RoundingMode.HALF_UP), saidas.setScale(2, java.math.RoundingMode.HALF_UP),
                despesas.setScale(2, java.math.RoundingMode.HALF_UP),
                entradas.subtract(saidas).subtract(despesas).setScale(2, java.math.RoundingMode.HALF_UP), peso);
    }

    /**
     * Fecha o caixa de um dia. Os totais NÃO vêm da tela: são recalculados aqui. A data precisa ser válida,
     * não pode ser futura nem anterior a 366 dias. Fechar de novo o mesmo dia atualiza o fechamento.
     */
    @Transactional(rollbackFor = Exception.class)
    public void fecharCaixaDoDia(String data) throws IOException {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Recife"));
        LocalDate localDate;
        try {
            localDate = LocalDate.parse(data == null ? "" : data.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida.");
        }
        if (localDate.isAfter(hoje)) throw new IllegalArgumentException("Não dá para fechar o caixa de um dia que ainda não chegou.");
        if (localDate.isBefore(hoje.minusDays(366))) throw new IllegalArgumentException("Data muito antiga para fechar o caixa.");

        ResumoDia r = resumoDoDia(localDate);
        CaixaDiario caixa = caixaDiarioRepository.findByArmazemIdAndDataFechamento(getArmazemIdLogado(), localDate).orElse(new CaixaDiario());
        caixa.setArmazem(getArmazemLogado());
        caixa.setDataFechamento(localDate);
        caixa.setEntradas(r.entradas);
        caixa.setSaidas(r.saidas);
        caixa.setDespesas(r.despesas);
        caixa.setLucroLiquido(r.lucro);
        caixa.setPesoComprado(r.pesoComprado);
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
        String cpfLimpo = com.reciclatech.backend.util.DocumentoValidator.normalizar(cpf);

        return precoVipRepository.findAllByArmazemId(getArmazemIdLogado()).stream()
                .filter(p -> p.getCpfCatador() != null && com.reciclatech.backend.util.DocumentoValidator.normalizar(p.getCpfCatador()).equals(cpfLimpo))
                .collect(Collectors.toMap(PrecoVip::getMaterialNome, PrecoVip::getPrecoEspecial, (a, b) -> a));
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
        salvarFornecedorVip(cpf, material, preco, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void salvarFornecedorVip(String cpf, String material, BigDecimal preco, boolean confirmado) throws IOException {
        if (!com.reciclatech.backend.util.DocumentoValidator.valido(cpf)) throw new IllegalArgumentException("CPF inválido.");
        if (!com.reciclatech.backend.util.Valores.positivoAte(preco, com.reciclatech.backend.util.Valores.PRECO_MAX)) {
            throw new IllegalArgumentException("Informe um preço maior que zero.");
        }
        String cpfLimpo = com.reciclatech.backend.util.DocumentoValidator.normalizar(cpf);
        // grava com o nome EXATO do material cadastrado (a busca do preço compara o nome)
        final String materialInformado = material;
        material = materialRepository.findAllByArmazemIdOrderByNomeAsc(getArmazemIdLogado()).stream()
                .filter(m -> chaveNome(m.getNome()).equals(chaveNome(materialInformado)))
                .map(Material::getNome).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Material não encontrado neste armazém."));
        Optional<PrecoVip> existente = precoVipRepository.findByArmazemIdAndCpfCatadorAndMaterialNome(getArmazemIdLogado(), cpfLimpo, material);
        String ref = material + " - " + mascararCpf(cpfLimpo);
        BigDecimal anterior = existente.map(PrecoVip::getPrecoEspecial).orElse(null);
        if (!confirmado && com.reciclatech.backend.util.RegrasVenda.variacaoSuspeita(anterior, preco)) {
            throw new VariacaoPrecoException(com.reciclatech.backend.util.RegrasVenda.textoVariacao(ref, anterior, preco));
        }
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
        String cpfLimpo = com.reciclatech.backend.util.DocumentoValidator.normalizar(cpf);
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
            // só as colunas do painel: nunca carrega hash de senha
            String sql = "SELECT a.id, a.nome, a.cnpj, a.telefone, a.login, a.status, a.plano, a.data_cadastro, " +
                    "p.ultimo_login AS ultimo_acesso_dono FROM armazem a LEFT JOIN parceiro p ON p.id = a.proprietario_id ORDER BY a.id DESC";
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("Erro ao listar armazéns", e);
            return new ArrayList<>();
        }
    }

    public static final Set<String> STATUS_ARMAZEM_VALIDOS = Set.of("ATIVO", "TRIAL", "SUSPENSO", "BLOQUEADO");

    /** Muda a situação de um armazém. Só aceita os 4 status conhecidos. Devolve false se o armazém não existe. */
    public boolean atualizarStatusArmazem(Long id, String novoStatus) {
        String status = novoStatus == null ? "" : novoStatus.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ARMAZEM_VALIDOS.contains(status)) throw new IllegalArgumentException("Status inválido.");
        if ("TRIAL".equals(status)) {
            // recolocar em TRIAL = dar um novo teste de 7 dias a partir de hoje
            return jdbcTemplate.update("UPDATE armazem SET status = ?, data_cadastro = ? WHERE id = ?", status,
                    java.sql.Date.valueOf(LocalDate.now(ZoneId.of("America/Recife"))), id) > 0;
        }
        return jdbcTemplate.update("UPDATE armazem SET status = ? WHERE id = ?", status, id) > 0;
    }

    /** Troca o plano (START/PRO) de um armazém. Os dados não mudam; só o acesso aos recursos Pro. false = armazém não existe. */
    public boolean atualizarPlanoArmazem(Long id, String novoPlano) {
        String plano = novoPlano == null ? "" : novoPlano.trim().toUpperCase(Locale.ROOT);
        if (!"START".equals(plano) && !"PRO".equals(plano)) throw new IllegalArgumentException("Plano inválido.");
        return jdbcTemplate.update("UPDATE armazem SET plano = ? WHERE id = ?", plano, id) > 0;
    }

    /** Volume do mês (todos os armazéns): GMV = compras + vendas à indústria; toneladas = material comprado (kg / 1000). */
    public double[] resumoMensalMaster() {
        String mes = LocalDate.now(ZoneId.of("America/Recife")).toString().substring(0, 7);
        String sql = "SELECT COALESCE(SUM(CASE WHEN status IN ('VENDIDO','SAIDA_INDUSTRIA') THEN preco_estimado ELSE 0 END), 0), " +
                "COALESCE(SUM(CASE WHEN status = 'VENDIDO' THEN peso ELSE 0 END), 0) " +
                "FROM oferta WHERE data LIKE ?";
        return jdbcTemplate.query(sql, rs -> {
            rs.next();
            return new double[]{rs.getBigDecimal(1).doubleValue(), rs.getBigDecimal(2).doubleValue() / 1000.0};
        }, mes + "-%");
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

    /** Código que o operador digita no login. Se o armazém ainda não tem (cadastro antigo), gera e grava agora. */
    public String obterCodigoArmazemLogado() {
        Armazem a = getArmazemLogado();
        if (a == null) return "";
        if (a.getCodigo() == null || a.getCodigo().isBlank()) {
            a.setCodigo(novoCodigoLivre());
            armazemRepository.save(a);
        }
        return a.getCodigo();
    }

    private String novoCodigoLivre() {
        for (int i = 0; i < 20; i++) {
            String c = com.reciclatech.backend.util.CodigoArmazem.gerar();
            if (!armazemRepository.existsByCodigo(c)) return c;
        }
        throw new IllegalStateException("Não foi possível gerar um código livre.");
    }

    public List<Funcionario> listarFuncionarios() {
        return funcionarioRepository.findAllByArmazemId(getArmazemIdLogado());
    }

    public static final int SENHA_OPERADOR_MIN = 6;

    public void salvarFuncionario(String nome, String login, String senhaPura) {
        String nomeLimpo = com.reciclatech.backend.util.Valores.texto(nome, 100);
        if (nomeLimpo == null) throw new IllegalArgumentException("Informe o nome do operador.");
        String loginLimpo = login == null ? "" : login.trim();
        if (loginLimpo.isEmpty()) throw new IllegalArgumentException("Informe o usuário do operador.");
        if (loginLimpo.length() > 40 || loginLimpo.matches(".*\\s.*")) {
            throw new IllegalArgumentException("O usuário deve ter até 40 caracteres e não pode ter espaços.");
        }
        if (senhaPura == null || senhaPura.length() < SENHA_OPERADOR_MIN) {
            throw new IllegalArgumentException("A senha do operador deve ter pelo menos " + SENHA_OPERADOR_MIN + " caracteres.");
        }
        if (senhaPura.length() > 72) throw new IllegalArgumentException("A senha deve ter no máximo 72 caracteres.");
        if (funcionarioRepository.existsByArmazemIdAndLogin(getArmazemIdLogado(), loginLimpo)) {
            throw new IllegalArgumentException("login_duplicado");
        }
        Funcionario f = new Funcionario();
        f.setArmazem(getArmazemLogado());
        f.setNome(nomeLimpo);
        f.setLogin(loginLimpo);
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