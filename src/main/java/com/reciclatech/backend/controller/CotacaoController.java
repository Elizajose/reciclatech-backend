package com.reciclatech.backend.controller;

import com.reciclatech.backend.service.CotacaoPublicaService;
import com.reciclatech.backend.service.LimitadorPublico;
import com.reciclatech.backend.util.CotacaoPublica;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fase 27: cotação pública da página inicial, em partes. A página pede só o que o catador escolheu
 * (Estado/cidade, busca pelo nome, material) em vez de receber todos os armazéns de uma vez.
 * Só leitura; nenhum dado interno (telefone, estoque, compras) sai por aqui.
 */
@RestController
public class CotacaoController {

    private static final Pattern UF = Pattern.compile("[A-Za-z]{2}");
    private static final int LIMITE_POR_MINUTO = 120;

    @Autowired private CotacaoPublicaService cotacaoService;
    @Autowired private LimitadorPublico limitador;

    @GetMapping("/cotacoes/regioes")
    public ResponseEntity<Map<String, Object>> regioes(HttpServletRequest req) {
        if (!permitido(req)) return muitas();
        return ok(CotacaoPublica.regioes(cotacaoService.armazens()));
    }

    @GetMapping("/cotacoes/armazens")
    public ResponseEntity<Map<String, Object>> armazens(@RequestParam(required = false) String uf,
                                                        @RequestParam(required = false) String cidade,
                                                        @RequestParam(required = false) String busca,
                                                        @RequestParam(required = false, defaultValue = "0") int pagina,
                                                        HttpServletRequest req) {
        if (!permitido(req)) return muitas();
        if (!regiaoValida(uf, cidade) || tamanho(busca) > 60 || pagina < 0 || pagina > 1000) return invalido();
        return ok(CotacaoPublica.armazens(cotacaoService.armazens(), uf, cidade, busca, pagina, cotacaoService.hoje()));
    }

    @GetMapping("/cotacoes/materiais")
    public ResponseEntity<Map<String, Object>> materiais(@RequestParam(required = false) String uf,
                                                         @RequestParam(required = false) String cidade,
                                                         HttpServletRequest req) {
        if (!permitido(req)) return muitas();
        if (!regiaoValida(uf, cidade)) return invalido();
        return ok(CotacaoPublica.materiais(cotacaoService.armazens(), uf, cidade));
    }

    @GetMapping("/cotacoes/ranking")
    public ResponseEntity<Map<String, Object>> ranking(@RequestParam(required = false) String uf,
                                                       @RequestParam(required = false) String cidade,
                                                       @RequestParam(required = false) String material,
                                                       HttpServletRequest req) {
        if (!permitido(req)) return muitas();
        if (!regiaoValida(uf, cidade) || material == null || material.isBlank() || material.length() > 130) return invalido();
        return ok(CotacaoPublica.ranking(cotacaoService.armazens(), uf, cidade, material, cotacaoService.hoje()));
    }

    /** Região obrigatória: Estado com 2 letras + cidade, ou "região não informada". */
    static boolean regiaoValida(String uf, String cidade) {
        if (CotacaoPublica.SEM_REGIAO.equals(uf)) return true;
        return uf != null && UF.matcher(uf.trim()).matches() && cidade != null && !cidade.isBlank() && cidade.length() <= 80;
    }

    private static int tamanho(String s) {
        return s == null ? 0 : s.length();
    }

    private boolean permitido(HttpServletRequest req) {
        return limitador.permitir("cot:" + req.getRemoteAddr(), LIMITE_POR_MINUTO, 60_000L);
    }

    private static ResponseEntity<Map<String, Object>> ok(Map<String, Object> corpo) {
        return ResponseEntity.ok().header("Cache-Control", "no-cache").body(corpo);
    }

    private static ResponseEntity<Map<String, Object>> invalido() {
        return ResponseEntity.badRequest().body(Map.of("erro", "Escolha o Estado e a cidade."));
    }

    private static ResponseEntity<Map<String, Object>> muitas() {
        return ResponseEntity.status(429).body(Map.of("erro", "Muitas consultas seguidas. Aguarde um minuto."));
    }
}
