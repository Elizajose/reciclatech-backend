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

    /**
     * Fase 28: versão de cada imagem existente (id -> número que muda quando a imagem é trocada), sem carregar os bytes.
     * Vai no endereço da imagem (?v=...): o navegador guarda a imagem e só baixa de novo quando ela muda.
     */
    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> versoes(String tipo, Collection<Long> ids) {
        java.util.Map<Long, Long> mapa = new java.util.HashMap<>();
        if (ids == null || ids.isEmpty()) return mapa;
        for (Object[] linha : repositorio.versoes(tipo, ids)) {
            if (linha == null || linha.length < 2 || !(linha[0] instanceof Long id)) continue;
            long v = linha[1] instanceof LocalDateTime quando ? quando.toEpochSecond(java.time.ZoneOffset.UTC) : 1L;
            mapa.put(id, v);
        }
        return mapa;
    }

    /** Fase 28: versão de uma imagem só; nulo = não tem imagem. */
    @Transactional(readOnly = true)
    public Long versao(String tipo, Long id) {
        if (id == null) return null;
        return versoes(tipo, java.util.List.of(id)).get(id);
    }

    /** Quais dos ids têm imagem (sem carregar os bytes). */
    @Transactional(readOnly = true)
    public Set<Long> comImagem(String tipo, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        return new HashSet<>(repositorio.idsComImagem(tipo, ids));
    }
}
