package com.reciclatech.backend.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Freio simples contra tentativa de senha: 5 erros em 15 min bloqueiam a chave por 10 min (memória do servidor). */
@Component
public class LoginThrottle {

    private static final int MAX_FALHAS = 5;
    private static final long JANELA_MS = 15 * 60 * 1000L;
    private static final long BLOQUEIO_MS = 10 * 60 * 1000L;
    private static final int LIMITE_MAPA = 10_000;

    private static class Estado {
        int falhas;
        long inicio;
        long bloqueadoAte;
    }

    private final Map<String, Estado> mapa = new ConcurrentHashMap<>();

    private String norm(String chave) { return chave == null ? "" : chave.trim().toLowerCase(Locale.ROOT); }

    public boolean bloqueado(String chave) {
        Estado e = mapa.get(norm(chave));
        return e != null && e.bloqueadoAte > System.currentTimeMillis();
    }

    public void falhou(String chave) {
        if (mapa.size() > LIMITE_MAPA) mapa.clear();
        mapa.compute(norm(chave), (k, atual) -> {
            long agora = System.currentTimeMillis();
            Estado e = atual;
            if (e == null || agora - e.inicio > JANELA_MS) {
                e = new Estado();
                e.inicio = agora;
            }
            e.falhas++;
            if (e.falhas >= MAX_FALHAS) e.bloqueadoAte = agora + BLOQUEIO_MS;
            return e;
        });
    }

    public void sucesso(String chave) { mapa.remove(norm(chave)); }
}
