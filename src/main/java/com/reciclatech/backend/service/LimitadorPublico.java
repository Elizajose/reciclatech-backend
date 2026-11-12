package com.reciclatech.backend.service;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Limite simples em memória (janela deslizante) para formulários públicos. Zera ao reiniciar o sistema. */
@Service
public class LimitadorPublico {

    private final Map<String, Deque<Long>> acessos = new ConcurrentHashMap<>();

    /** Registra uma tentativa e diz se ela cabe no limite (máximo de "max" por "janelaMs"). */
    public boolean permitir(String chave, int max, long janelaMs) {
        long agora = System.currentTimeMillis();
        Deque<Long> fila = acessos.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            while (!fila.isEmpty() && agora - fila.peekFirst() > janelaMs) fila.pollFirst();
            if (fila.size() >= max) return false;
            fila.addLast(agora);
        }
        if (acessos.size() > 20000) limpar(agora, janelaMs);
        return true;
    }

    private void limpar(long agora, long janelaMs) {
        acessos.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || agora - e.getValue().peekLast() > janelaMs;
            }
        });
    }
}
