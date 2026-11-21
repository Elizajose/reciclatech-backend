package com.reciclatech.backend.service;

import com.reciclatech.backend.model.Parceiro;
import com.reciclatech.backend.model.RecuperacaoSenha;
import com.reciclatech.backend.repository.ParceiroRepository;
import com.reciclatech.backend.repository.RecuperacaoSenhaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class RecuperacaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);
    private static final long VALIDADE_MINUTOS = 60;

    private final ParceiroRepository parceiroRepository;
    private final RecuperacaoSenhaRepository tokenRepository;
    private final ParceiroService parceiroService;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    /** Endereço público do site, vindo da configuração (nunca do pedido do navegador). */
    @Value("${coletae.url-base:http://localhost:8080}")
    private String urlBase;

    public RecuperacaoSenhaService(ParceiroRepository parceiroRepository,
                                   RecuperacaoSenhaRepository tokenRepository,
                                   ParceiroService parceiroService,
                                   EmailService emailService) {
        this.parceiroRepository = parceiroRepository;
        this.tokenRepository = tokenRepository;
        this.parceiroService = parceiroService;
        this.emailService = emailService;
    }

    /** Gera o token e manda o e-mail. Não revela se o e-mail existe e nunca lança exceção. */
    public void solicitar(String email) {
        try {
            if (email == null || email.isBlank()) return;
            Optional<Parceiro> achado = parceiroRepository.findByEmailIgnoreCase(email.trim());
            if (achado.isEmpty() || !achado.get().isAtivo()) return;
            Parceiro p = achado.get();

            tokenRepository.apagarDoParceiro(p.getId()); // só o pedido mais recente vale

            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            tokenRepository.save(new RecuperacaoSenha(p, hash(token), LocalDateTime.now().plusMinutes(VALIDADE_MINUTOS)));

            String link = urlBase.replaceAll("/+$", "") + "/conta/redefinir-senha?token=" + token;
            String destino = p.getEmail();
            String nome = p.getNome();
            // envia em segundo plano: o tempo de resposta não deve denunciar se o e-mail existe
            CompletableFuture.runAsync(() -> emailService.enviarRedefinicaoSenha(destino, nome, link));
        } catch (Exception e) {
            log.error("Falha ao solicitar redefinição de senha: {}", e.getMessage());
        }
    }

    public boolean tokenValido(String token) {
        return buscarValido(token).isPresent();
    }

    /** Troca a senha se o token for válido. false = link inválido, expirado ou já usado. */
    @Transactional
    public boolean redefinir(String token, String novaSenha) {
        Optional<RecuperacaoSenha> achado = buscarValido(token);
        if (achado.isEmpty()) return false;
        RecuperacaoSenha t = achado.get();
        parceiroService.definirSenha(t.getParceiro().getId(), novaSenha); // valida a senha nova
        t.setUsadoEm(LocalDateTime.now());
        tokenRepository.save(t);
        String destino = t.getParceiro().getEmail();
        String nome = t.getParceiro().getNome();
        CompletableFuture.runAsync(() -> emailService.enviarSenhaAlterada(destino, nome));
        return true;
    }

    private Optional<RecuperacaoSenha> buscarValido(String token) {
        if (token == null || token.isBlank() || token.length() > 100) return Optional.empty();
        return tokenRepository.findByTokenHash(hash(token))
                .filter(t -> t.getUsadoEm() == null && t.getExpiraEm().isAfter(LocalDateTime.now()));
    }

    private static String hash(String valor) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
