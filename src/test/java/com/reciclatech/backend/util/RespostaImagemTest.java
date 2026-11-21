package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Fase 28: só as rotas de imagem escapam do "no-store", e só com versão o navegador guarda. */
class RespostaImagemTest {

    @Test void rotasDeImagemReconhecidas() {
        assertTrue(RespostaImagem.rotaDeImagem("GET", "/conta/foto"));
        assertTrue(RespostaImagem.rotaDeImagem("GET", "/admin/logo"));
        assertTrue(RespostaImagem.rotaDeImagem("GET", "/conta/armazens/42/logo"));
    }

    @Test void outrasRotasContinuamSemCache() {
        assertFalse(RespostaImagem.rotaDeImagem("POST", "/conta/foto"));
        assertFalse(RespostaImagem.rotaDeImagem("GET", "/conta/armazens"));
        assertFalse(RespostaImagem.rotaDeImagem("GET", "/conta/armazens/42/logo/remover"));
        assertFalse(RespostaImagem.rotaDeImagem("GET", "/conta/armazens/abc/logo"));
        assertFalse(RespostaImagem.rotaDeImagem("GET", "/conta/foto/remover"));
        assertFalse(RespostaImagem.rotaDeImagem("GET", "/admin/coletas"));
        assertFalse(RespostaImagem.rotaDeImagem("GET", null));
    }

    @Test void soGuardaComVersao() {
        assertEquals(RespostaImagem.CACHE_VERSIONADA, RespostaImagem.cache("1732190000"));
        assertEquals(RespostaImagem.CACHE_SEM_VERSAO, RespostaImagem.cache(null));
        assertEquals(RespostaImagem.CACHE_SEM_VERSAO, RespostaImagem.cache(" "));
    }
}
