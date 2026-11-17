package com.reciclatech.backend.util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * Valida e padroniza a foto/logo enviada: só JPG ou PNG de verdade (confere o conteúdo, não o nome do arquivo),
 * recusa imagem gigante, reduz para no máximo 320 px e regrava como JPEG. Regravar descarta metadados e
 * qualquer coisa escondida dentro do arquivo.
 */
public final class ImagemPerfil {

    public static final int TAMANHO_MAX_BYTES = 5 * 1024 * 1024;
    public static final int LADO_MAX = 320;
    static final int DIMENSAO_MAX = 10_000;
    static final long PIXELS_MAX = 50_000_000L;

    private ImagemPerfil() {}

    /** Devolve um JPEG pronto para guardar. Lança IllegalArgumentException com mensagem pronta para a tela. */
    public static byte[] processar(byte[] bruto) {
        if (bruto == null || bruto.length == 0) throw new IllegalArgumentException("Escolha uma imagem.");
        if (bruto.length > TAMANHO_MAX_BYTES) throw new IllegalArgumentException("A imagem passa de 5 MB. Escolha uma menor.");
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bruto))) {
            Iterator<ImageReader> leitores = in == null ? null : ImageIO.getImageReaders(in);
            if (leitores == null || !leitores.hasNext()) throw invalida();
            ImageReader leitor = leitores.next();
            try {
                String formato = leitor.getFormatName() == null ? "" : leitor.getFormatName().toLowerCase(Locale.ROOT);
                if (!formato.equals("jpeg") && !formato.equals("png")) throw invalida();
                leitor.setInput(in, true, true);
                int w = leitor.getWidth(0), h = leitor.getHeight(0);
                if (w < 1 || h < 1 || w > DIMENSAO_MAX || h > DIMENSAO_MAX || (long) w * h > PIXELS_MAX) {
                    throw new IllegalArgumentException("A imagem é grande demais. Escolha uma foto menor.");
                }
                BufferedImage original = leitor.read(0);
                if (original == null) throw invalida();
                return gravarJpeg(reduzir(original));
            } finally {
                leitor.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw invalida();
        }
    }

    private static IllegalArgumentException invalida() {
        return new IllegalArgumentException("Esse arquivo não é uma imagem válida. Use JPG ou PNG.");
    }

    /** Reduz em etapas (metade a cada vez) para não serrilhar e põe fundo branco no lugar da transparência. */
    private static BufferedImage reduzir(BufferedImage origem) {
        int w = origem.getWidth(), h = origem.getHeight();
        double escala = Math.min(1.0, (double) LADO_MAX / Math.max(w, h));
        int alvoW = Math.max(1, (int) Math.round(w * escala)), alvoH = Math.max(1, (int) Math.round(h * escala));

        BufferedImage atual = paraRgb(origem);
        while (atual.getWidth() / 2 >= alvoW && atual.getHeight() / 2 >= alvoH) {
            atual = redimensionar(atual, atual.getWidth() / 2, atual.getHeight() / 2);
        }
        if (atual.getWidth() != alvoW || atual.getHeight() != alvoH) atual = redimensionar(atual, alvoW, alvoH);
        return atual;
    }

    private static BufferedImage paraRgb(BufferedImage origem) {
        BufferedImage rgb = new BufferedImage(origem.getWidth(), origem.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(origem, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static BufferedImage redimensionar(BufferedImage origem, int w, int h) {
        BufferedImage saida = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = saida.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(origem, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return saida;
    }

    private static byte[] gravarJpeg(BufferedImage img) throws IOException {
        Iterator<ImageWriter> escritores = ImageIO.getImageWritersByFormatName("jpeg");
        if (!escritores.hasNext()) throw new IOException("sem gravador JPEG");
        ImageWriter escritor = escritores.next();
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream fluxo = new MemoryCacheImageOutputStream(saida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(0.85f);
            escritor.setOutput(fluxo);
            escritor.write(null, new IIOImage(img, null, null), parametros);
            fluxo.flush();
            return saida.toByteArray();
        } finally {
            escritor.dispose();
        }
    }
}
