package com.reciclatech.backend.service;

import com.reciclatech.backend.model.Armazem;
import com.reciclatech.backend.model.Parceiro;
import com.reciclatech.backend.repository.ArmazemRepository;
import com.reciclatech.backend.repository.ParceiroRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Regras da conta do dono (Parceiro). Ainda não é chamado por nenhuma tela: entra na fase 3. */
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
}
