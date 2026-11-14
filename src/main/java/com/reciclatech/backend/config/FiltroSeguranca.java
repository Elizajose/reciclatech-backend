package com.reciclatech.backend.config;

import com.reciclatech.backend.service.ValidadorSessao;
import com.reciclatech.backend.util.OrigemConfiavel;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * 1) Cabeçalhos de segurança em todas as respostas.
 * 2) Proteção contra CSRF por verificação de origem: POST/PUT/DELETE vindo de outro site é recusado.
 *    Desligue em emergência com a variável CSRF_ORIGEM=false (sem precisar de novo deploy de código).
 */
@Component
public class FiltroSeguranca extends OncePerRequestFilter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FiltroSeguranca.class);

    @Value("${coletae.csrf-origem:true}")
    private boolean verificarOrigem;

    @Value("${coletae.url-base:}")
    private String urlBase;

    @Autowired
    private ValidadorSessao validadorSessao;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "SAMEORIGIN");
        res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

        // Páginas de área logada nunca ficam no cache do navegador: após "Sair", o botão Voltar não mostra mais a tela antiga.
        String uri = req.getRequestURI();
        if (uri.startsWith("/admin") || uri.startsWith("/conta") || uri.startsWith("/master")
                || uri.startsWith("/extrato") || uri.startsWith("/meus-extratos") || uri.equals("/login")) {
            res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            res.setHeader("Pragma", "no-cache");
            res.setDateHeader("Expires", 0);
        }

        String metodo = req.getMethod();
        boolean altera = !("GET".equals(metodo) || "HEAD".equals(metodo) || "OPTIONS".equals(metodo) || "TRACE".equals(metodo));
        if (verificarOrigem && altera) {
            Set<String> hosts = new HashSet<>();
            addHost(hosts, OrigemConfiavel.hostSemPorta(req.getHeader("Host")));
            addHost(hosts, OrigemConfiavel.hostSemPorta(req.getHeader("X-Forwarded-Host")));
            addHost(hosts, OrigemConfiavel.hostSemPorta(req.getServerName()));
            try {
                if (urlBase != null && !urlBase.isBlank()) addHost(hosts, URI.create(urlBase.trim()).getHost());
            } catch (IllegalArgumentException ignorado) { /* url-base inválida: ignora */ }

            if (!OrigemConfiavel.permitida(req.getHeader("Origin"), req.getHeader("Referer"), hosts)) {
                log.warn("POST recusado por origem diferente: {} {} origin={} referer={}",
                        metodo, req.getRequestURI(), req.getHeader("Origin"), req.getHeader("Referer"));
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Origem da requisição não permitida.");
                return;
            }
        }

        // Reconfere a sessão no banco (operador desativado, senha trocada, armazém bloqueado...). Páginas estáticas ficam de fora.
        // (as rotas de entrada ficam de fora: entrar de novo sempre tem de funcionar, mesmo com uma sessão velha no navegador)
        if (!ehEstatico(uri) && !"/sair".equals(uri) && !"/login-admin".equals(uri) && !"/master/login-secreto".equals(uri)) {
            HttpSession sessao = req.getSession(false);
            if (sessao != null && validadorSessao.verificar(sessao) == ValidadorSessao.Resultado.ENCERRAR) {
                try { sessao.invalidate(); } catch (IllegalStateException ja) { /* já encerrada */ }
                if (uri.startsWith("/api/")) {
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                } else {
                    res.sendRedirect(req.getContextPath() + "/login?erro=sessao");
                }
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private static boolean ehEstatico(String uri) {
        return uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/img/") || uri.startsWith("/video/")
                || uri.startsWith("/favicon") || uri.startsWith("/actuator/");
    }

    private static void addHost(Set<String> hosts, String h) {
        if (h != null && !h.isBlank()) hosts.add(h.toLowerCase());
    }
}
