package com.reciclatech.backend.util;

/**
 * Fase 34: descobre o tipo do arquivo pelos primeiros bytes (assinatura), sem confiar no nome nem no que o navegador diz.
 * Aceita só PNG, JPEG, GIF, WEBP e PDF. SVG e HTML nunca passam (poderiam carregar script).
 */
public final class TipoArquivo {

    private TipoArquivo() {}

    /** "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf" ou nulo se não for aceito. */
    public static String detectar(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "image/gif";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "application/pdf";
        return null;
    }

    /** Nome seguro para mostrar e baixar: só letras, números, ponto, traço e sublinhado; até 80 letras. */
    public static String nomeSeguro(String original, String tipo) {
        String n = original == null ? "" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        if (n.isBlank() || n.chars().allMatch(c -> c == '_' || c == '.')) n = "anexo";
        if (n.length() > 80) n = n.substring(n.length() - 80);
        String ext = switch (tipo == null ? "" : tipo) {
            case "image/png" -> ".png"; case "image/jpeg" -> ".jpg"; case "image/gif" -> ".gif";
            case "image/webp" -> ".webp"; case "application/pdf" -> ".pdf"; default -> "";
        };
        String baixo = n.toLowerCase(java.util.Locale.ROOT);
        boolean extCerta = baixo.endsWith(ext) || (ext.equals(".jpg") && baixo.endsWith(".jpeg"));
        if (!ext.isEmpty() && !extCerta) n = (n.length() + ext.length() > 80 ? n.substring(0, 80 - ext.length()) : n) + ext;
        return n;
    }
}
