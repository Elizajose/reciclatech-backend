package com.reciclatech.backend.service;

import com.reciclatech.backend.model.ImagemConta;
import com.reciclatech.backend.repository.ImagemContaRepository;
import com.reciclatech.backend.util.ImagemPerfil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Foto do dono e logo dos armazéns. Quem chama já confirmou que o armazém é do dono logado. */
@Service
public class ImagemContaService {

    public static final String DONO = "DONO";
    public static final String ARMAZEM = "ARMAZEM";

    private final ImagemContaRepository repositorio;

    public ImagemContaService(ImagemContaRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Valida, reduz e grava (substitui a anterior). IllegalArgumentException = mensagem pronta para a tela. */
    @Transactional
    public void salvar(String tipo, Long refId, byte[] bruto) {
        byte[] jpeg = ImagemPerfil.processar(bruto);
        ImagemConta imagem = repositorio.findByTipoAndRefId(tipo, refId).orElseGet(() -> new ImagemConta(tipo, refId));
        imagem.setDados(jpeg);
        imagem.setAtualizadoEm(LocalDateTime.now());
        repositorio.save(imagem);
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> buscar(String tipo, Long refId) {
        return repositorio.findByTipoAndRefId(tipo, refId).map(ImagemConta::getDados);
    }

    @Transactional
    public void remover(String tipo, Long refId) {
        repositorio.findByTipoAndRefId(tipo, refId).ifPresent(repositorio::delete);
    }

    /** Quais dos ids têm imagem (sem carregar os bytes). */
    @Transactional(readOnly = true)
    public Set<Long> comImagem(String tipo, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        return new HashSet<>(repositorio.idsComImagem(tipo, ids));
    }
}
