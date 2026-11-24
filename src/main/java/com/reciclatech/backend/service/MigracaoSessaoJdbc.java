package com.reciclatech.backend.service;

import com.reciclatech.backend.util.TabelasSessao;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fase 34: cria (se faltar) as tabelas onde o Spring Session guarda as sessões. Roda ao iniciar, antes de o site
 * aceitar acessos, e é idempotente. Falha só vai para o log: a aplicação sobe do mesmo jeito.
 * Para voltar às sessões na memória (emergência), basta a variável de ambiente
 * SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration
 */
@Component
public class MigracaoSessaoJdbc {

    private static final Logger log = LoggerFactory.getLogger(MigracaoSessaoJdbc.class);

    private final JdbcTemplate jdbc;

    public MigracaoSessaoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void criarTabelas() {
        try {
            for (String sql : TabelasSessao.SQL) jdbc.execute(sql);
            log.info("Tabelas de sessão (spring_session) conferidas.");
        } catch (RuntimeException e) {
            log.error("Não foi possível criar as tabelas de sessão: {}", e.getMessage());
        }
    }
}
