package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ImagemPerfilTest {

    private static byte[] img(int w, int h, int tipo, String formato) throws Exception {
        BufferedImage b = new BufferedImage(w, h, tipo);
        var g = b.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w / 2, h);
        g.dispose();
        var o = new ByteArrayOutputStream();
        ImageIO.write(b, formato, o);
        return o.toByteArray();
    }

    private static void recusa(byte[] dados) {
        assertThrows(IllegalArgumentException.class, () -> ImagemPerfil.processar(dados));
    }

    @Test void pngGrandeVira320ComFormatoJpeg() throws Exception {
        byte[] saida = ImagemPerfil.processar(img(1200, 800, BufferedImage.TYPE_INT_RGB, "png"));
        BufferedImage r = ImageIO.read(new ByteArrayInputStream(saida));
        assertEquals(320, Math.max(r.getWidth(), r.getHeight()));
        assertEquals(0xFF, saida[0] & 0xFF);
        assertEquals(0xD8, saida[1] & 0xFF);
    }

    @Test void imagemPequenaNaoEAmpliada() throws Exception {
        BufferedImage r = ImageIO.read(new ByteArrayInputStream(
                ImagemPerfil.processar(img(100, 60, BufferedImage.TYPE_INT_RGB, "jpg"))));
        assertEquals(100, r.getWidth());
        assertEquals(60, r.getHeight());
    }

    @Test void transparenciaViraFundoBranco() throws Exception {
        byte[] png = img(50, 50, BufferedImage.TYPE_INT_ARGB, "png");
        BufferedImage r = ImageIO.read(new ByteArrayInputStream(ImagemPerfil.processar(png)));
        int rgb = r.getRGB(40, 25);
        assertTrue(((rgb >> 16) & 0xFF) > 230 && ((rgb >> 8) & 0xFF) > 230 && (rgb & 0xFF) > 230);
    }

    @Test void textoNaoEImagem() {
        recusa("isso nao e uma imagem".getBytes());
    }

    @Test void vazioENuloSaoRecusados() {
        recusa(new byte[0]);
        recusa(null);
    }

    @Test void gifNaoEAceito() throws Exception {
        recusa(img(20, 20, BufferedImage.TYPE_INT_RGB, "gif"));
    }

    @Test void acimaDe5MbERecusado() {
        recusa(new byte[ImagemPerfil.TAMANHO_MAX_BYTES + 1]);
    }

    @Test void extensaoFalsaComConteudoHtmlERecusada() {
        recusa("<html><script>alert(1)</script></html>".getBytes());
    }
}
