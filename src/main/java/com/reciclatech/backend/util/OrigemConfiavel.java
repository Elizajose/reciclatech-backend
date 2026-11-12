package com.reciclatech.backend.util;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Regra de "mesma origem" usada contra CSRF: um POST só vale se vier do próprio site. */
public final class OrigemConfiavel {
    private OrigemConfiavel() {}

    /**
     * @param origin  cabeçalho Origin (pode ser nulo)
     * @param referer cabeçalho Referer (pode ser nulo)
     * @param hostsPermitidos nomes de host (minúsculos, sem porta) do próprio site
     * Sem Origin e sem Referer (curl, apps antigos) a requisição é aceita: o ataque de CSRF vem de navegador,
     * e navegadores sempre enviam um dos dois em POST.
     */
    public static boolean permitida(String origin, String referer, Set<String> hostsPermitidos) {
        String candidato = valido(origin) ? origin : (valido(referer) ? referer : null);
        if (candidato == null) return true;
        try {
            String host = URI.create(candidato.trim()).getHost();
            return host != null && hostsPermitidos.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** "localhost:8080" -> "localhost"; "[::1]:8080" -> "::1"; lista "a.com, b.com" -> "a.com". */
    public static String hostSemPorta(String hostHeader) {
        if (hostHeader == null) return null;
        String h = hostHeader.split(",")[0].trim().toLowerCase(Locale.ROOT);
        if (h.isEmpty()) return null;
        if (h.startsWith("[")) {
            int fim = h.indexOf(']');
            return fim > 0 ? h.substring(1, fim) : h;
        }
        int dp = h.lastIndexOf(':');
        return dp > 0 ? h.substring(0, dp) : h;
    }

    private static boolean valido(String v) {
        return v != null && !v.isBlank() && !"null".equalsIgnoreCase(v.trim());
    }
}
