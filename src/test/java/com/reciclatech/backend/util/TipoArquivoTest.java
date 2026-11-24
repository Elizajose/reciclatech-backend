package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Fase 34: tipo do anexo pelo conteúdo. */
class TipoArquivoTest {

    private static byte[] b(int... v) { byte[] r = new byte[Math.max(12, v.length)]; for (int i = 0; i < v.length; i++) r[i] = (byte) v[i]; return r; }

    @Test void reconheceSoOsTiposAceitos() {
        assertEquals("image/png", TipoArquivo.detectar(b(0x89, 'P', 'N', 'G')));
        assertEquals("image/jpeg", TipoArquivo.detectar(b(0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals("image/gif", TipoArquivo.detectar(b('G', 'I', 'F', '8', '9', 'a')));
        assertEquals("image/webp", TipoArquivo.detectar(b('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')));
        assertEquals("application/pdf", TipoArquivo.detectar(b('%', 'P', 'D', 'F', '-', '1')));
        assertNull(TipoArquivo.detectar("<svg onload=alert(1)>....".getBytes()));
        assertNull(TipoArquivo.detectar("<html><body>oi</body>".getBytes()));
        assertNull(TipoArquivo.detectar(new byte[3]));
        assertNull(TipoArquivo.detectar(null));
    }

    @Test void nomeSeguroComExtensaoDoConteudo() {
        assertEquals("tela_1.png", TipoArquivo.nomeSeguro("C:\\x\\tela 1.png", "image/png"));
        assertEquals("foto.jpeg", TipoArquivo.nomeSeguro("foto.jpeg", "image/jpeg"));
        assertEquals("virus.exe.pdf", TipoArquivo.nomeSeguro("virus.exe", "application/pdf"));
        assertEquals("anexo.png", TipoArquivo.nomeSeguro("../..", "image/png"));
        assertTrue(TipoArquivo.nomeSeguro("a".repeat(200) + ".png", "image/png").length() <= 80);
        assertEquals("_script_.png", TipoArquivo.nomeSeguro("<script>", "image/png"));
    }
}
