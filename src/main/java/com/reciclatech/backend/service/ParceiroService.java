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

    @Transactional(readOnly = true)
    public List<Armazem> listarArmazens(Long parceiroId) {
        return armazemRepository.findByProprietarioIdOrderByNomeAsc(parceiroId);
    }

    /** Devolve o armazém SOMENTE se pertencer ao dono informado. */
    @Transactional(readOnly = true)
    public Optional<Armazem> buscarArmazemDoDono(Long armazemId, Long parceiroId) {
        return armazemRepository.findByIdAndProprietarioId(armazemId, parceiroId);
    }

    /** Cadastra um armazém já vinculado ao dono. Mensagens de erro prontas para a tela. */
    @Transactional
    public Armazem cadastrarArmazem(Long parceiroId, String nome, String cnpj, String telefone, String endereco, String plano) {
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Informe o nome do armazém.");
        if (nome.trim().length() > 150) throw new IllegalArgumentException("Nome do armazém muito longo.");
        if (telefone == null || telefone.isBlank()) throw new IllegalArgumentException("Informe o telefone do armazém.");
        if (endereco == null || endereco.isBlank()) throw new IllegalArgumentException("Informe o endereço do armazém.");

        String documento = (cnpj == null || cnpj.isBlank()) ? null : cnpj.trim();
        if (documento != null) {
            if (!DocumentoValidator.valido(documento)) throw new IllegalArgumentException("CPF/CNPJ inválido.");
            if (armazemRepository.existsByCnpj(documento)) throw new IllegalArgumentException("Este CPF/CNPJ já está cadastrado.");
        }

        Parceiro dono = parceiroRepository.findById(parceiroId)
                .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada."));

        Armazem a = new Armazem();
        a.setProprietario(dono);
        a.setNome(nome.trim());
        a.setCnpj(documento);
        a.setTelefone(telefone.trim());
        a.setEndereco(endereco.trim());
        a.setPerfil("GESTOR");
        a.setStatus("TRIAL");
        a.setPlano("PRO".equalsIgnoreCase(plano) ? "PRO" : "START");
        a.setDataCadastro(LocalDate.now(ZoneId.of("America/Recife")));
        return armazemRepository.save(a);
    }

    /** Liga um armazém antigo (que já tinha login/senha) a esta conta. Só se ainda não tiver dono. */
    @Transactional
    public Armazem vincularArmazemLegado(Long parceiroId, Long armazemId) {
        Armazem a = armazemRepository.findById(armazemId)
                .orElseThrow(() -> new IllegalArgumentException("Armazém não encontrado."));
        if (a.getProprietario() != null) throw new IllegalArgumentException("Este armazém já está vinculado a uma conta.");
        a.setProprietario(parceiroRepository.getReferenceById(parceiroId));
        return armazemRepository.save(a);
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
}
