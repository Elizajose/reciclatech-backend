package com.reciclatech.backend.service;

import com.reciclatech.backend.model.Armazem;
import com.reciclatech.backend.model.Parceiro;
import com.reciclatech.backend.repository.ArmazemRepository;
import com.reciclatech.backend.repository.ParceiroRepository;
import com.reciclatech.backend.util.DocumentoValidator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Regras da conta do dono (Parceiro): cadastro, login e armazéns do dono. */
@Service
public class ParceiroService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ParceiroRepository parceiroRepository;
    private final ArmazemRepository armazemRepository;
    private final PasswordEncoder encoder;

    public ParceiroService(ParceiroRepository parceiroRepository,
                           ArmazemRepository armazemRepository,
                           PasswordEncoder encoder) {
        this.parceiroRepository = parceiroRepository;
        this.armazemRepository = armazemRepository;
        this.encoder = encoder;
    }

    public boolean emailEmUso(String email) {
        return email != null && parceiroRepository.existsByEmailIgnoreCase(email.trim());
    }

    /** Cria a conta. Lança IllegalArgumentException com mensagem pronta para mostrar na tela. */
    @Transactional
    public Parceiro registrar(String nome, String email, String senha) {
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Informe seu nome.");
        if (email == null || !EMAIL.matcher(email.trim()).matches()) throw new IllegalArgumentException("E-mail inválido.");
        if (senha == null || senha.length() < 8) throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres.");
        if (senha.length() > 72) throw new IllegalArgumentException("A senha deve ter no máximo 72 caracteres.");

        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        if (parceiroRepository.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new IllegalArgumentException("Este e-mail já está cadastrado.");
        }
        return parceiroRepository.save(new Parceiro(nome.trim(), emailNormalizado, encoder.encode(senha)));
    }

    /** Valida e-mail e senha. Vazio = credenciais inválidas (sem dizer qual dos dois errou). */
    @Transactional
    public Optional<Parceiro> autenticar(String email, String senha) {
        if (email == null || senha == null) return Optional.empty();
        Optional<Parceiro> achado = parceiroRepository.findByEmailIgnoreCase(email.trim());
        if (achado.isEmpty() || !achado.get().isAtivo()) return Optional.empty();
        if (!encoder.matches(senha, achado.get().getSenhaHash())) return Optional.empty();

        achado.get().setUltimoLogin(LocalDateTime.now());
        return achado;
    }

    /** Lista os armazéns do dono; os que ainda não têm código de operador (cadastro antigo) recebem um agora. */
    @Transactional
    public List<Armazem> listarArmazens(Long parceiroId) {
        List<Armazem> lista = armazemRepository.findByProprietarioIdOrderByNomeAsc(parceiroId);
        for (Armazem a : lista) {
            if (a.getCodigo() == null || a.getCodigo().isBlank()) {
                String c;
                do { c = com.reciclatech.backend.util.CodigoArmazem.gerar(); } while (armazemRepository.existsByCodigo(c));
                a.setCodigo(c);
                armazemRepository.save(a);
            }
        }
        return lista;
    }

    /** Devolve o armazém SOMENTE se pertencer ao dono informado. */
    @Transactional(readOnly = true)
    public Optional<Armazem> buscarArmazemDoDono(Long armazemId, Long parceiroId) {
        return armazemRepository.findByIdAndProprietarioId(armazemId, parceiroId);
    }

    /** Cadastra um armazém já vinculado ao dono. Mensagens de erro prontas para a tela. */
    @Transactional
    public Armazem cadastrarArmazem(Long parceiroId, String nome, String cnpj, String telefone, String endereco,
                                    String uf, String cidade, String plano) {
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Informe o nome do armazém.");
        if (nome.trim().length() > 150) throw new IllegalArgumentException("Nome do armazém muito longo.");
        if (telefone == null || telefone.isBlank()) throw new IllegalArgumentException("Informe o telefone do armazém.");
        if (endereco == null || endereco.isBlank()) throw new IllegalArgumentException("Informe o endereço do armazém.");
        String ufValida = validarUf(uf);
        String cidadeValida = validarCidade(cidade);

        String documento = (cnpj == null || cnpj.isBlank()) ? null : cnpj.trim();
        if (documento != null) {
            if (!DocumentoValidator.valido(documento)) throw new IllegalArgumentException("CPF/CNPJ inválido.");
            // mesmo formato para todos e comparação sem pontuação: "11.222.333/0001-81" e "11222333000181" são o mesmo documento
            documento = DocumentoValidator.formatar(documento);
            if (armazemRepository.existeDocumentoNormalizado(DocumentoValidator.normalizar(documento))) {
                throw new IllegalArgumentException("Este CPF/CNPJ já está cadastrado.");
            }
        }

        Parceiro dono = parceiroRepository.findById(parceiroId)
                .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada."));

        Armazem a = new Armazem();
        a.setProprietario(dono);
        a.setNome(nome.trim());
        a.setCnpj(documento);
        a.setTelefone(telefone.trim());
        a.setEndereco(endereco.trim());
        a.setUf(ufValida);
        a.setCidade(cidadeValida);
        a.setPerfil("GESTOR");
        a.setStatus("TRIAL");
        a.setPlano("PRO".equalsIgnoreCase(plano) ? "PRO" : "START");
        a.setDataCadastro(LocalDate.now(ZoneId.of("America/Recife")));
        return armazemRepository.save(a);
    }

    /** Impressão curta do hash atual da senha do dono (guardada na sessão para detectar troca de senha). */
    @Transactional(readOnly = true)
    public String impressaoDaSenha(Long parceiroId) {
        return parceiroRepository.findById(parceiroId).map(p -> com.reciclatech.backend.util.Impressao.de(p.getSenhaHash())).orElse("");
    }

    /** Define uma senha nova (usada na redefinição por e-mail). */
    @Transactional
    public void definirSenha(Long parceiroId, String novaSenha) {
        if (novaSenha == null || novaSenha.length() < 8) throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres.");
        if (novaSenha.length() > 72) throw new IllegalArgumentException("A senha deve ter no máximo 72 caracteres.");
        Parceiro p = parceiroRepository.findById(parceiroId)
                .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada."));
        p.setSenhaHash(encoder.encode(novaSenha));
        parceiroRepository.save(p);
    }

    /** Troca a senha da conta confirmando a senha atual. false = senha atual incorreta. */
    @Transactional
    public boolean alterarSenha(Long parceiroId, String senhaAtual, String novaSenha) {
        Parceiro p = parceiroRepository.findById(parceiroId)
                .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada."));
        if (senhaAtual == null || !encoder.matches(senhaAtual, p.getSenhaHash())) return false;
        definirSenha(parceiroId, novaSenha); // valida o tamanho e grava
        return true;
    }

    private static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS",
            "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");
    private static final Set<String> PARTICULAS = Set.of("de", "da", "do", "das", "dos", "e");

    private static String validarUf(String uf) {
        String v = uf == null ? "" : uf.trim().toUpperCase(Locale.ROOT);
        if (!UFS.contains(v)) throw new IllegalArgumentException("Selecione um estado válido.");
        return v;
    }

    /** Remove espaços extras e padroniza maiúsculas (ex.: "santa  cruz DO capibaribe" -> "Santa Cruz do Capibaribe"). */
    private static String validarCidade(String cidade) {
        String limpo = cidade == null ? "" : cidade.trim().replaceAll("\\s+", " ");
        if (limpo.length() < 2 || limpo.length() > 100) throw new IllegalArgumentException("Informe o município.");
        String[] palavras = limpo.toLowerCase(Locale.forLanguageTag("pt-BR")).split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < palavras.length; i++) {
            String w = palavras[i];
            if (i > 0) sb.append(' ');
            if (i > 0 && PARTICULAS.contains(w)) sb.append(w);
            else sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** Define estado e município de um armazém, só se ele for do dono informado. */
    @Transactional
    public void definirRegiao(Long parceiroId, Long armazemId, String uf, String cidade) {
        Armazem a = armazemRepository.findByIdAndProprietarioId(armazemId, parceiroId)
                .orElseThrow(() -> new IllegalArgumentException("Armazém não encontrado."));
        a.setUf(validarUf(uf));
        a.setCidade(validarCidade(cidade));
        armazemRepository.save(a);
    }
}
