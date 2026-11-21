package com.reciclatech.backend.util;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

/**
 * Fase 28: resposta das imagens (foto do dono e logo do armazém).
 * Com versão no endereço (?v=...), o navegador guarda a imagem por 30 dias só para este usuário ("private");
 * quando a imagem é trocada a versão muda e o navegador baixa a nova. Sem versão, não guarda.
 */
public final class RespostaImagem {

    public static final String CACHE_VERSIONADA = "private, max-age=2592000";
    public static final String CACHE_SEM_VERSAO = "private, no-cache";

    private RespostaImagem() {}

    /**
     * Rotas de imagem que podem ser guardadas pelo navegador (o FiltroSeguranca não força "no-store" nelas).
     * Só GET e só estes endereços exatos: /conta/foto, /admin/logo e /conta/armazens/{número}/logo.
     */
    public static boolean rotaDeImagem(String metodo, String uri) {
        if (!"GET".equals(metodo) || uri == null) return false;
        return uri.equals("/conta/foto") || uri.equals("/admin/logo") || uri.matches("/conta/armazens/\\d{1,18}/logo");
    }

    public static String cache(String versao) {
        return versao != null && !versao.isBlank() ? CACHE_VERSIONADA : CACHE_SEM_VERSAO;
    }

    public static ResponseEntity<byte[]> jpeg(Optional<byte[]> dados, String versao) {
        if (dados == null || dados.isEmpty()) return ResponseEntity.notFound().header("Cache-Control", "no-store").build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("Cache-Control", cache(versao)).body(dados.get());
    }
}
